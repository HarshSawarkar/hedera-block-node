/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.block.server.persistence.storage.read;

import static com.hedera.block.protos.BlockStreamService.Block;
import static com.hedera.block.protos.BlockStreamService.BlockItem;
import static com.hedera.block.server.Constants.BLOCK_FILE_EXTENSION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.hedera.block.server.config.BlockNodeContext;
import com.hedera.block.server.config.BlockNodeContextFactory;
import com.hedera.block.server.persistence.storage.Util;
import com.hedera.block.server.persistence.storage.write.BlockAsDirWriterBuilder;
import com.hedera.block.server.persistence.storage.write.BlockWriter;
import com.hedera.block.server.util.PersistTestUtils;
import com.hedera.block.server.util.TestUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.config.Config;
import io.helidon.config.MapConfigSource;
import io.helidon.config.spi.ConfigSource;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlockAsDirReaderTest {

    private final System.Logger LOGGER = System.getLogger(getClass().getName());

    private static final String TEMP_DIR = "block-node-unit-test-dir";
    private static final String JUNIT = "my-junit-test";

    private Path testPath;
    private Config testConfig;

    @BeforeEach
    public void setUp() throws IOException {
        testPath = Files.createTempDirectory(TEMP_DIR);
        LOGGER.log(System.Logger.Level.INFO, "Created temp directory: " + testPath.toString());

        final Map<String, String> testProperties = Map.of(JUNIT, testPath.toString());
        final ConfigSource testConfigSource = MapConfigSource.builder().map(testProperties).build();
        testConfig = Config.builder(testConfigSource).build();
    }

    @AfterEach
    public void tearDown() {
        if (!TestUtils.deleteDirectory(testPath.toFile())) {
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "Failed to delete temp directory: " + testPath.toString());
        }
    }

    @Test
    public void testReadBlockDoesNotExist() throws IOException {
        final BlockReader<Block> blockReader =
                BlockAsDirReaderBuilder.newBuilder(JUNIT, testConfig).build();
        final Optional<Block> blockOpt = blockReader.read(10000);
        assertTrue(blockOpt.isEmpty());
    }

    @Test
    public void testReadPermsRepairSucceeded() throws IOException {
        final List<BlockItem> blockItems = PersistTestUtils.generateBlockItems(1);

        final BlockNodeContext blockNodeContext = BlockNodeContextFactory.create();
        final BlockWriter<BlockItem> blockWriter =
                BlockAsDirWriterBuilder.newBuilder(JUNIT, testConfig, blockNodeContext).build();
        for (BlockItem blockItem : blockItems) {
            blockWriter.write(blockItem);
        }

        // Make the block unreadable
        removeBlockReadPerms(1, testConfig);

        // The default BlockReader will attempt to repair the permissions and should succeed
        final BlockReader<Block> blockReader =
                BlockAsDirReaderBuilder.newBuilder(JUNIT, testConfig).build();
        final Optional<Block> blockOpt = blockReader.read(1);
        assertFalse(blockOpt.isEmpty());
        assertEquals(10, blockOpt.get().getBlockItemsList().size());
    }

    @Test
    public void testRemoveBlockReadPermsRepairFailed() throws IOException {
        final List<BlockItem> blockItems = PersistTestUtils.generateBlockItems(1);

        final BlockNodeContext blockNodeContext = BlockNodeContextFactory.create();
        final BlockWriter<BlockItem> blockWriter =
                BlockAsDirWriterBuilder.newBuilder(JUNIT, testConfig, blockNodeContext).build();
        for (BlockItem blockItem : blockItems) {
            blockWriter.write(blockItem);
        }

        // Make the block unreadable
        removeBlockReadPerms(1, testConfig);

        // For this test, build the Reader with ineffective repair permissions to
        // simulate a failed repair (root changed the perms, etc.)
        final BlockReader<Block> blockReader =
                BlockAsDirReaderBuilder.newBuilder(JUNIT, testConfig)
                        .filePerms(TestUtils.getNoPerms())
                        .build();
        final Optional<Block> blockOpt = blockReader.read(1);
        assertTrue(blockOpt.isEmpty());
    }

    @Test
    public void testRemoveBlockItemReadPerms() throws IOException {
        final List<BlockItem> blockItems = PersistTestUtils.generateBlockItems(1);

        final BlockNodeContext blockNodeContext = BlockNodeContextFactory.create();
        final BlockWriter<BlockItem> blockWriter =
                BlockAsDirWriterBuilder.newBuilder(JUNIT, testConfig, blockNodeContext).build();
        for (BlockItem blockItem : blockItems) {
            blockWriter.write(blockItem);
        }

        removeBlockItemReadPerms(1, 1, testConfig);

        final BlockReader<Block> blockReader =
                BlockAsDirReaderBuilder.newBuilder(JUNIT, testConfig).build();
        assertThrows(IOException.class, () -> blockReader.read(1));
    }

    @Test
    public void testPathIsNotDirectory() throws IOException {
        final List<BlockItem> blockItems = PersistTestUtils.generateBlockItems(1);
        final Path blockNodeRootPath = Path.of(testConfig.get(JUNIT).asString().get());

        // Write a file named "1" where a directory should be
        writeFileToPath(blockNodeRootPath.resolve(Path.of("1")), blockItems.getFirst());

        // Should return empty because the path is not a directory
        final BlockReader<Block> blockReader =
                BlockAsDirReaderBuilder.newBuilder(JUNIT, testConfig).build();
        final Optional<Block> blockOpt = blockReader.read(1);
        assertTrue(blockOpt.isEmpty());
    }

    @Test
    public void testRepairReadPermsFails() throws IOException {

        final List<BlockItem> blockItems = PersistTestUtils.generateBlockItems(1);

        final BlockNodeContext blockNodeContext = BlockNodeContextFactory.create();
        final BlockWriter<BlockItem> blockWriter =
                BlockAsDirWriterBuilder.newBuilder(JUNIT, testConfig, blockNodeContext).build();
        for (final BlockItem blockItem : blockItems) {
            blockWriter.write(blockItem);
        }

        removeBlockReadPerms(1, testConfig);

        // Use a spy on a subclass of the BlockAsDirReader to proxy calls
        // to the actual methods but to also throw an IOException when
        // the setPerm method is called.
        final TestBlockAsDirReader blockReader = spy(new TestBlockAsDirReader(JUNIT, testConfig));
        doThrow(IOException.class).when(blockReader).setPerm(any(), any());

        final Optional<Block> blockOpt = blockReader.read(1);
        assertTrue(blockOpt.isEmpty());
    }

    @Test
    public void testBlockNodePathReadFails() throws IOException {

        // Remove read perm on the root path
        removePathReadPerms(Path.of(testConfig.get(JUNIT).asString().get()));

        // Use a spy on a subclass of the BlockAsDirReader to proxy calls
        // to the actual methods but to also throw an IOException when
        // the setPerm method is called.
        final TestBlockAsDirReader blockReader = spy(new TestBlockAsDirReader(JUNIT, testConfig));
        doThrow(IOException.class).when(blockReader).setPerm(any(), any());

        final Optional<Block> blockOpt = blockReader.read(1);
        assertTrue(blockOpt.isEmpty());
    }

    private void writeFileToPath(final Path path, final BlockItem blockItem) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path.toString())) {
            blockItem.writeTo(fos);
            LOGGER.log(
                    System.Logger.Level.INFO, "Successfully wrote the block item file: {0}", path);
        }
    }

    public static void removeBlockReadPerms(int blockNumber, final Config config)
            throws IOException {
        final Path blockNodeRootPath = Path.of(config.get(JUNIT).asString().get());
        final Path blockPath = blockNodeRootPath.resolve(String.valueOf(blockNumber));
        removePathReadPerms(blockPath);
    }

    static void removePathReadPerms(final Path path) throws IOException {
        Files.setPosixFilePermissions(path, TestUtils.getNoRead().value());
    }

    private void removeBlockItemReadPerms(int blockNumber, int blockItem, Config config)
            throws IOException {
        final Path blockNodeRootPath = Path.of(config.get(JUNIT).asString().get());
        final Path blockPath = blockNodeRootPath.resolve(String.valueOf(blockNumber));
        final Path blockItemPath = blockPath.resolve(blockItem + BLOCK_FILE_EXTENSION);
        Files.setPosixFilePermissions(blockItemPath, TestUtils.getNoRead().value());
    }

    // TestBlockAsDirReader overrides the setPerm() method to allow a test spy to simulate an
    // IOException while allowing the real setPerm() method to remain protected.
    private static final class TestBlockAsDirReader extends BlockAsDirReader {
        public TestBlockAsDirReader(String key, Config config) {
            super(key, config, Util.defaultPerms);
        }

        @Override
        public void setPerm(@NonNull final Path path, @NonNull final Set<PosixFilePermission> perms)
                throws IOException {
            super.setPerm(path, perms);
        }
    }
}
