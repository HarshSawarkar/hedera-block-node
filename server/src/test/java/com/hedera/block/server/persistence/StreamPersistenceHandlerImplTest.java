// SPDX-License-Identifier: Apache-2.0
package com.hedera.block.server.persistence;

import static com.hedera.block.server.metrics.BlockNodeMetricTypes.Counter.StreamPersistenceHandlerError;
import static com.hedera.block.server.util.PersistTestUtils.generateBlockItemsUnparsed;
import static com.hedera.hapi.block.SubscribeStreamResponseCode.READ_STREAM_SUCCESS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.block.server.ack.AckHandler;
import com.hedera.block.server.config.BlockNodeContext;
import com.hedera.block.server.events.ObjectEvent;
import com.hedera.block.server.mediator.SubscriptionHandler;
import com.hedera.block.server.metrics.MetricsService;
import com.hedera.block.server.notifier.Notifier;
import com.hedera.block.server.persistence.storage.write.AsyncBlockWriterFactory;
import com.hedera.block.server.service.ServiceStatus;
import com.hedera.block.server.util.TestConfigUtil;
import com.hedera.hapi.block.BlockItemSetUnparsed;
import com.hedera.hapi.block.BlockItemUnparsed;
import com.hedera.hapi.block.SubscribeStreamResponseUnparsed;
import com.hedera.pbj.runtime.OneOf;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamPersistenceHandlerImplTest {
    private static final int TEST_TIMEOUT = 50;

    @Mock
    private SubscriptionHandler<SubscribeStreamResponseUnparsed> subscriptionHandler;

    @Mock
    private Notifier notifier;

    @Mock
    private BlockNodeContext blockNodeContext;

    @Mock
    private ServiceStatus serviceStatus;

    @Mock
    private MetricsService metricsService;

    @Mock
    private AckHandler ackHandlerMock;

    @Mock
    private AsyncBlockWriterFactory asyncBlockWriterFactoryMock;

    @Mock
    private Executor executorMock;

    @Test
    void testOnEventWhenServiceIsNotRunning() {
        when(blockNodeContext.metricsService()).thenReturn(metricsService);
        when(serviceStatus.isRunning()).thenReturn(false);

        final StreamPersistenceHandlerImpl streamPersistenceHandler = new StreamPersistenceHandlerImpl(
                subscriptionHandler,
                notifier,
                blockNodeContext,
                serviceStatus,
                ackHandlerMock,
                asyncBlockWriterFactoryMock,
                executorMock);

        final List<BlockItemUnparsed> blockItems = generateBlockItemsUnparsed(1);
        final BlockItemSetUnparsed blockItemSet =
                BlockItemSetUnparsed.newBuilder().blockItems(blockItems).build();
        final SubscribeStreamResponseUnparsed subscribeStreamResponse = SubscribeStreamResponseUnparsed.newBuilder()
                .blockItems(blockItemSet)
                .build();
        final ObjectEvent<SubscribeStreamResponseUnparsed> event = new ObjectEvent<>();
        event.set(subscribeStreamResponse);

        streamPersistenceHandler.onEvent(event, 0, false);

        // Indirectly confirm the branch we're in by verifying
        // these methods were not called.
        verify(notifier, never()).publish(any());
        verify(metricsService, never()).get(StreamPersistenceHandlerError);
    }

    @Test
    void testBlockItemIsNull() throws IOException {
        final BlockNodeContext blockNodeContext = TestConfigUtil.getTestBlockNodeContext();
        when(serviceStatus.isRunning()).thenReturn(true);

        final StreamPersistenceHandlerImpl streamPersistenceHandler = new StreamPersistenceHandlerImpl(
                subscriptionHandler,
                notifier,
                blockNodeContext,
                serviceStatus,
                ackHandlerMock,
                asyncBlockWriterFactoryMock,
                executorMock);

        final List<BlockItemUnparsed> blockItems = generateBlockItemsUnparsed(1);
        final BlockItemSetUnparsed blockItemSet =
                BlockItemSetUnparsed.newBuilder().blockItems(blockItems).build();
        final SubscribeStreamResponseUnparsed subscribeStreamResponse = spy(SubscribeStreamResponseUnparsed.newBuilder()
                .blockItems(blockItemSet)
                .build());

        // Force the block item to be null
        when(subscribeStreamResponse.blockItems()).thenReturn(null);
        final ObjectEvent<SubscribeStreamResponseUnparsed> event = new ObjectEvent<>();
        event.set(subscribeStreamResponse);

        streamPersistenceHandler.onEvent(event, 0, false);

        verify(serviceStatus, timeout(TEST_TIMEOUT).times(1)).stopRunning(any());
        verify(subscriptionHandler, timeout(TEST_TIMEOUT).times(1)).unsubscribe(any());
        verify(notifier, timeout(TEST_TIMEOUT).times(1)).notifyUnrecoverableError();
    }

    @Test
    void testSubscribeStreamResponseTypeUnknown() throws IOException {
        final BlockNodeContext blockNodeContext = TestConfigUtil.getTestBlockNodeContext();
        when(serviceStatus.isRunning()).thenReturn(true);

        final StreamPersistenceHandlerImpl streamPersistenceHandler = new StreamPersistenceHandlerImpl(
                subscriptionHandler,
                notifier,
                blockNodeContext,
                serviceStatus,
                ackHandlerMock,
                asyncBlockWriterFactoryMock,
                executorMock);

        final List<BlockItemUnparsed> blockItems = generateBlockItemsUnparsed(1);
        final BlockItemSetUnparsed blockItemSet =
                BlockItemSetUnparsed.newBuilder().blockItems(blockItems).build();
        final SubscribeStreamResponseUnparsed subscribeStreamResponse = spy(SubscribeStreamResponseUnparsed.newBuilder()
                .blockItems(blockItemSet)
                .build());

        // Force the block item to be UNSET
        final OneOf<SubscribeStreamResponseUnparsed.ResponseOneOfType> illegalOneOf =
                new OneOf<>(SubscribeStreamResponseUnparsed.ResponseOneOfType.UNSET, null);
        when(subscribeStreamResponse.response()).thenReturn(illegalOneOf);

        final ObjectEvent<SubscribeStreamResponseUnparsed> event = new ObjectEvent<>();
        event.set(subscribeStreamResponse);

        streamPersistenceHandler.onEvent(event, 0, false);

        verify(serviceStatus, timeout(TEST_TIMEOUT).times(1)).stopRunning(any());
        verify(subscriptionHandler, timeout(TEST_TIMEOUT).times(1)).unsubscribe(any());
        verify(notifier, timeout(TEST_TIMEOUT).times(1)).notifyUnrecoverableError();
    }

    @Test
    void testSubscribeStreamResponseTypeStatus() {
        when(blockNodeContext.metricsService()).thenReturn(metricsService);
        when(serviceStatus.isRunning()).thenReturn(true);

        final StreamPersistenceHandlerImpl streamPersistenceHandler = new StreamPersistenceHandlerImpl(
                subscriptionHandler,
                notifier,
                blockNodeContext,
                serviceStatus,
                ackHandlerMock,
                asyncBlockWriterFactoryMock,
                executorMock);

        final SubscribeStreamResponseUnparsed subscribeStreamResponse = spy(SubscribeStreamResponseUnparsed.newBuilder()
                .status(READ_STREAM_SUCCESS)
                .build());
        final ObjectEvent<SubscribeStreamResponseUnparsed> event = new ObjectEvent<>();
        event.set(subscribeStreamResponse);

        streamPersistenceHandler.onEvent(event, 0, false);

        verify(serviceStatus, never()).stopRunning(any());
        verify(subscriptionHandler, never()).unsubscribe(any());
        verify(notifier, never()).notifyUnrecoverableError();
    }
}
