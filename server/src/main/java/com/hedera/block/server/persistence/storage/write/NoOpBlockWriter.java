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

package com.hedera.block.server.persistence.storage.write;

import static com.hedera.block.server.metrics.BlockNodeMetricTypes.Counter.BlocksPersisted;
import static java.lang.System.Logger.Level.INFO;

import com.hedera.block.server.config.BlockNodeContext;
import com.hedera.block.server.metrics.MetricsService;
import com.hedera.hapi.block.stream.BlockItem;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * The NoOpBlockWriter class is a stub implementation of the block writer intended for testing purposes only. It is
 * designed to isolate the Producer and Mediator components from storage implementation during testing while still
 * providing metrics and logging for troubleshooting.
 */
public class NoOpBlockWriter implements BlockWriter<List<BlockItem>> {

    private final MetricsService metricsService;

    /**
     * Creates a new NoOpBlockWriter instance for testing and troubleshooting only.
     *
     * @param blockNodeContext the block node context
     */
    public NoOpBlockWriter(BlockNodeContext blockNodeContext) {
        this.metricsService = blockNodeContext.metricsService();
        System.getLogger(getClass().getName()).log(INFO, "Using " + getClass().getSimpleName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<List<BlockItem>> write(@NonNull List<BlockItem> blockItems) throws IOException {
        if (blockItems.getLast().hasBlockProof()) {
            metricsService.get(BlocksPersisted).increment();
        }

        return Optional.empty();
    }
}
