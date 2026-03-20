package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.event.RunEvent;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.AgentRuntimeService;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.SystemEventRunner;
import com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.runtime.support.NotificationResult;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void secondMessageGetsImmediateAckWhenConversationBusy() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            int current = invocationCount.incrementAndGet();
            progressConsumer.accept("progress-" + request.getCurrentMessage());
            if (current == 1) {
                firstStarted.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
            }
            return "reply-" + request.getCurrentMessage();
        };

        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getScheduler().setMaxConcurrentPerConversation(1);
        properties.getAgent().getScheduler().setAckWhenBusy(true);
        AgentRuntimeService runtimeService = runtimeService(conversationAgent, store, scheduler, registry, properties);

        try {
            String firstRunId = runtimeService.submitInbound(inbound("msg-1", "question-1"));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            String secondRunId = runtimeService.submitInbound(inbound("msg-2", "question-2"));
            assertNotNull(firstRunId);
            assertNotNull(secondRunId);
            assertTrue(waitUntil(() -> adapter.messages.stream().anyMatch(message -> message.contains("已收到")), 2000));

            releaseFirst.countDown();
            assertTrue(waitUntil(() -> {
                AgentRun run1 = runtimeService.getRun(firstRunId);
                AgentRun run2 = runtimeService.getRun(secondRunId);
                return run1 != null && run2 != null
                        && run1.getStatus() == RunStatus.SUCCEEDED
                        && run2.getStatus() == RunStatus.SUCCEEDED;
            }, 5000));
        } finally {
            releaseFirst.countDown();
            scheduler.shutdown();
        }
    }

    @Test
    void runCanNotifyUserProactively() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            if ("请主动通知我".equals(request.getCurrentMessage())) {
                NotificationResult result = request.getNotificationSupport().notifyUser("这是一条主动通知", false);
                assertTrue(result.isDelivered());
                return AgentRuntimeService.NO_REPLY;
            }
            return "reply-" + request.getCurrentMessage();
        };
        SolonClawProperties properties = new SolonClawProperties();
        AgentRuntimeService runtimeService = runtimeService(conversationAgent, store, scheduler, registry, properties);

        try {
            String runId = runtimeService.submitInbound(inbound("msg-notify", "请主动通知我"));
            assertNotNull(runId);
            assertTrue(waitUntil(() -> adapter.messages.contains("这是一条主动通知"), 5000));
            assertEquals(1, adapter.outbounds.size());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void childRunCompletionUsesSystemEventRunnerForAggregateReply() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            String message = request.getCurrentMessage();
            if (request.getCurrentSourceKind() == RuntimeSourceKind.USER_MESSAGE && "question-parent".equals(message)) {
                request.getSpawnTaskSupport().spawnTask("research-child");
                return "parent-waiting";
            }
            if (request.isChildRun() && "research-child".equals(message)) {
                return "child-result";
            }
            if (request.getCurrentSourceKind() == RuntimeSourceKind.CHILD_CONTINUATION) {
                return AgentRuntimeService.FINAL_REPLY_ONCE_PREFIX + "final-parent-answer";
            }
            return "reply-" + message;
        };
        SolonClawProperties properties = new SolonClawProperties();
        AgentRuntimeService runtimeService = runtimeService(conversationAgent, store, scheduler, registry, properties);

        try {
            String parentRunId = runtimeService.submitInbound(inbound("msg-parent", "question-parent"));
            assertNotNull(parentRunId);
            assertTrue(waitUntil(() -> adapter.messages.contains("final-parent-answer"), 5000));
            assertEquals(1, adapter.outbounds.stream()
                    .filter(outbound -> "final-parent-answer".equals(outbound.getContent()))
                    .count());
            assertTrue(store.hasRunEventType(parentRunId, "children_aggregated"));
            assertTrue(store.getRunEvents(parentRunId, 0).stream()
                    .map(RunEvent::getEventType)
                    .anyMatch("child_continuation_triggered"::equals));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void debugWebInboundIsHandledLikeNormalUserMessage() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter debugAdapter = new RecordingChannelAdapter(ChannelType.DEBUG_WEB);
        registry.register(debugAdapter);
        AtomicReference<ConversationExecutionRequest> lastRequest = new AtomicReference<ConversationExecutionRequest>();
        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            lastRequest.set(request);
            return "debug-reply";
        };
        SolonClawProperties properties = new SolonClawProperties();
        AgentRuntimeService runtimeService = runtimeService(conversationAgent, store, scheduler, registry, properties);

        try {
            InboundEnvelope inboundEnvelope = new InboundEnvelope();
            inboundEnvelope.setMessageId("debug-1");
            inboundEnvelope.setChannelType(ChannelType.DEBUG_WEB);
            inboundEnvelope.setChannelInstanceId("debug-web");
            inboundEnvelope.setSenderId("debug-user");
            inboundEnvelope.setConversationId("debug-1");
            inboundEnvelope.setConversationType(ConversationType.PRIVATE);
            inboundEnvelope.setContent("hello");
            inboundEnvelope.setReplyTarget(new ReplyTarget(ChannelType.DEBUG_WEB, ConversationType.PRIVATE, "debug-1", "debug-user"));
            inboundEnvelope.setReceivedAt(System.currentTimeMillis());
            inboundEnvelope.setSessionKey("debug-web:debug-1");
            inboundEnvelope.setSourceKind(RuntimeSourceKind.USER_MESSAGE);

            String runId = runtimeService.submitInbound(inboundEnvelope);
            assertNotNull(runId);
            assertTrue(waitUntil(() -> {
                AgentRun run = runtimeService.getRun(runId);
                return run != null && run.getStatus() == RunStatus.SUCCEEDED;
            }, 5000));
            assertEquals(RuntimeSourceKind.USER_MESSAGE, lastRequest.get().getCurrentSourceKind());
            assertNotNull(store.getLatestExternalRoute());
            assertEquals(ChannelType.DEBUG_WEB, store.getLatestExternalRoute().getReplyTarget().getChannelType());
            assertEquals("debug-reply", debugAdapter.messages.get(0));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void progressIsDispatchedOnlyToProgressCapableChannel() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        ProgressChannelAdapter adapter = new ProgressChannelAdapter();
        registry.register(adapter);

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            progressConsumer.accept("draft-1");
            progressConsumer.accept("draft-2");
            return "final-answer";
        };

        SolonClawProperties properties = new SolonClawProperties();
        AgentRuntimeService runtimeService = runtimeService(conversationAgent, store, scheduler, registry, properties);

        try {
            InboundEnvelope inboundEnvelope = new InboundEnvelope();
            inboundEnvelope.setMessageId("msg-feishu");
            inboundEnvelope.setChannelType(ChannelType.FEISHU);
            inboundEnvelope.setChannelInstanceId("feishu-default");
            inboundEnvelope.setSenderId("ou-1");
            inboundEnvelope.setConversationId("oc-1");
            inboundEnvelope.setConversationType(ConversationType.GROUP);
            inboundEnvelope.setContent("hello");
            inboundEnvelope.setReplyTarget(new ReplyTarget(ChannelType.FEISHU, ConversationType.GROUP, "oc-1", "ou-1"));
            inboundEnvelope.setReceivedAt(System.currentTimeMillis());
            inboundEnvelope.setSessionKey("feishu:group:oc-1");
            inboundEnvelope.setSourceKind(RuntimeSourceKind.USER_MESSAGE);

            runtimeService.submitInbound(inboundEnvelope);

            assertTrue(waitUntil(() -> adapter.outbounds.size() >= 3, 5000));
            assertTrue(adapter.outbounds.get(0).isProgress());
            assertTrue(adapter.outbounds.get(1).isProgress());
            assertEquals("final-answer", adapter.outbounds.get(2).getContent());
        } finally {
            scheduler.shutdown();
        }
    }

    private AgentRuntimeService runtimeService(
            ConversationAgent conversationAgent,
            RuntimeStoreService store,
            ConversationScheduler scheduler,
            ChannelRegistry registry,
            SolonClawProperties properties
    ) {
        SystemEventRunner systemEventRunner = new SystemEventRunner(
                conversationAgent,
                store,
                scheduler,
                registry,
                properties
        );
        return new AgentRuntimeService(
                conversationAgent,
                store,
                scheduler,
                registry,
                systemEventRunner,
                properties
        );
    }

    private InboundEnvelope inbound(String messageId, String content) {
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1");

        InboundEnvelope envelope = new InboundEnvelope();
        envelope.setMessageId(messageId);
        envelope.setChannelType(ChannelType.DINGTALK);
        envelope.setChannelInstanceId("dingtalk-default");
        envelope.setSenderId("user-1");
        envelope.setConversationId("group-1");
        envelope.setConversationType(ConversationType.GROUP);
        envelope.setContent(content);
        envelope.setReplyTarget(replyTarget);
        envelope.setReceivedAt(System.currentTimeMillis());
        envelope.setSessionKey("dingtalk:group:group-1");
        envelope.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
        return envelope;
    }

    private boolean waitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private static class RecordingChannelAdapter implements ChannelAdapter {
        private final ChannelType channelType;
        protected final List<String> messages = new CopyOnWriteArrayList<String>();
        protected final List<OutboundEnvelope> outbounds = new CopyOnWriteArrayList<OutboundEnvelope>();

        private RecordingChannelAdapter() {
            this(ChannelType.DINGTALK);
        }

        private RecordingChannelAdapter(ChannelType channelType) {
            this.channelType = channelType;
        }

        @Override
        public ChannelType channelType() {
            return channelType;
        }

        @Override
        public DeliveryResult send(OutboundEnvelope outboundEnvelope) {
            outbounds.add(outboundEnvelope);
            messages.add(outboundEnvelope.getContent());
            DeliveryResult result = new DeliveryResult();
            result.setDelivered(true);
            result.setChannelType(channelType());
            result.setOriginalLength(outboundEnvelope.getContent() == null ? 0 : outboundEnvelope.getContent().length());
            result.setFinalLength(result.getOriginalLength());
            result.setMessage("sent");
            return result;
        }
    }

    private static class ProgressChannelAdapter extends RecordingChannelAdapter {
        @Override
        public ChannelType channelType() {
            return ChannelType.FEISHU;
        }

        @Override
        public boolean supportsProgressUpdates() {
            return true;
        }
    }

    @Test
    void emptyModelResponseFallsBackToUserFriendlyFailureReply() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        ConversationAgent conversationAgent = (request, progressConsumer) -> "";
        SolonClawProperties properties = new SolonClawProperties();
        AgentRuntimeService runtimeService = runtimeService(conversationAgent, store, scheduler, registry, properties);

        try {
            String runId = runtimeService.submitInbound(inbound("msg-empty", "空回复测试"));
            assertNotNull(runId);
            assertTrue(waitUntil(() -> !adapter.messages.isEmpty(), 5000));
            assertEquals("这次处理没有拿到有效结果，可能是模型响应超时或解析异常。请再试一次。", adapter.messages.get(0));
            assertEquals(RunStatus.FAILED, runtimeService.getRun(runId).getStatus());
            assertTrue(store.hasRunEventType(runId, "llm_empty_response"));
        } finally {
            scheduler.shutdown();
        }
    }
}
