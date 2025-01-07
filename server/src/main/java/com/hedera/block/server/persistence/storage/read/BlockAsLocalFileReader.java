// SPDX-License-Identifier: Apache-2.0
package com.hedera.block.server.persistence.storage.read;

import com.hedera.block.common.utils.Preconditions;
import com.hedera.block.server.persistence.storage.path.BlockPathResolver;
import com.hedera.hapi.block.BlockUnparsed;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A Block reader that reads block-as-file.
 */
public final class BlockAsLocalFileReader implements LocalBlockReader<BlockUnparsed> {
    private final BlockPathResolver pathResolver;

    /**
     * Constructor.
     *
     * @param pathResolver valid, {@code non-null} instance of
     * {@link BlockPathResolver} used to resolve paths to block files
     */
    private BlockAsLocalFileReader(@NonNull final BlockPathResolver pathResolver) {
        this.pathResolver = Objects.requireNonNull(pathResolver);
    }

    /**
     * This method creates and returns a new instance of
     * {@link BlockAsLocalFileReader}.
     *
     * @param pathResolver valid, {@code non-null} instance of
     * {@link BlockPathResolver} used to resolve paths to block files
     * @return a new, fully initialized instance of
     * {@link BlockAsLocalFileReader}
     */
    public static BlockAsLocalFileReader of(@NonNull final BlockPathResolver pathResolver) {
        return new BlockAsLocalFileReader(pathResolver);
    }

    @NonNull
    @Override
    public Optional<BlockUnparsed> read(final long blockNumber) throws IOException, ParseException {
        Preconditions.requireWhole(blockNumber);
        final Path resolvedBlockPath = pathResolver.resolvePathToBlock(blockNumber);
        if (Files.exists(resolvedBlockPath)) {
            return Optional.of(doRead(resolvedBlockPath));
        } else {
            return Optional.empty();
        }
    }

    private BlockUnparsed doRead(final Path resolvedBlockPath) throws IOException, ParseException {
        try (final ReadableStreamingData data = new ReadableStreamingData(resolvedBlockPath)) {
            return BlockUnparsed.PROTOBUF.parse(data);
        }
    }
}
