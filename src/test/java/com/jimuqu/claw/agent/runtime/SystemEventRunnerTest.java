package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.enums.SystemEventPolicy;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.SystemEventRunner;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.runtime.support.NotificationResult;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemEventRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void jobSystemEventCanFallbackDeliverReminderText() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) -> "记得喝水。";
        SystemEventRunner runner = new SystemEventRunner(conversationAgent, store, scheduler, registry, properties);

        try {
            SystemEventRequest request = new SystemEventRequest();
            request.setSourceKind(RuntimeSourceKind.JOB_SYSTEM_EVENT);
            request.setPolicy(SystemEventPolicy.USER_VISIBLE_OPTIONAL);
            request.setSessionKey("dingtalk:group:group-1");
            request.setReplyTarget(new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1"));
            request.setContent("提醒我喝水");
            request.setAllowNotifyUser(true);

            String runId = runner.submit(request);
            assertNotNull(runId);
            assertTrue(waitUntil(() -> adapter.messages.contains("记得喝水。"), 5000));
            assertTrue(store.hasRunEventType(runId, "delivery_fallback_sent"));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void heartbeatEventSuppressesPlainUserVisibleReplyByDefault() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) -> "系统状态正常";
        SystemEventRunner runner = new SystemEventRunner(conversationAgent, store, scheduler, registry, properties);

        try {
            SystemEventRequest request = new SystemEventRequest();
            request.setSourceKind(RuntimeSourceKind.HEARTBEAT_EVENT);
            request.setPolicy(SystemEventPolicy.INTERNAL_ONLY);
            request.setSessionKey("dingtalk:group:group-1");
            request.setReplyTarget(new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1"));
            request.setContent("heartbeat");
            request.setAllowNotifyUser(true);

            String runId = runner.submit(request);
            assertNotNull(runId);
            assertTrue(waitUntil(() -> {
                AgentRun run = store.getRun(runId);
                return run != null && run.getStatus() == RunStatus.SUCCEEDED;
            }, 5000));
            assertTrue(adapter.outbounds.isEmpty());
            assertTrue(store.hasRunEventType(runId, "delivery_suppressed"));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void heartbeatEventCanNotifyUserExplicitly() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            NotificationResult result = request.getNotificationSupport().notifyUser("需要人工关注", false);
            assertTrue(result.isDelivered());
            return "NO_REPLY";
        };
        SystemEventRunner runner = new SystemEventRunner(conversationAgent, store, scheduler, registry, properties);

        try {
            SystemEventRequest request = new SystemEventRequest();
            request.setSourceKind(RuntimeSourceKind.HEARTBEAT_EVENT);
            request.setPolicy(SystemEventPolicy.INTERNAL_ONLY);
            request.setSessionKey("dingtalk:group:group-1");
            request.setReplyTarget(new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1"));
            request.setContent("heartbeat");
            request.setAllowNotifyUser(true);

            String runId = runner.submit(request);
            assertNotNull(runId);
            assertTrue(waitUntil(() -> adapter.messages.contains("需要人工关注"), 5000));
            assertEquals(1, adapter.outbounds.size());
            assertTrue(store.hasRunEventType(runId, "notify"));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void childContinuationDeliversOnlyFinalReplyOnce() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) ->
                "FINAL_REPLY_ONCE:聚合完成";
        SystemEventRunner runner = new SystemEventRunner(conversationAgent, store, scheduler, registry, properties);

        try {
            AgentRun parentRun = new AgentRun();
            parentRun.setRunId("parent-run");
            parentRun.setSessionKey("dingtalk:group:group-1");
            parentRun.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
            parentRun.setSourceUserVersion(1L);
            store.saveRun(parentRun);

            AgentRun child = new AgentRun();
            child.setRunId("child-1");
            child.setParentRunId("parent-run");
            child.setParentSessionKey("dingtalk:group:group-1");
            child.setStatus(RunStatus.SUCCEEDED);
            store.saveRun(child);

            SystemEventRequest request = new SystemEventRequest();
            request.setSourceKind(RuntimeSourceKind.CHILD_CONTINUATION);
            request.setPolicy(SystemEventPolicy.AGGREGATE_ONLY);
            request.setSessionKey("dingtalk:group:group-1");
            request.setReplyTarget(new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1"));
            request.setContent("child completed");
            request.setRelatedRunId("parent-run");

            String firstRunId = runner.submit(request);
            assertNotNull(firstRunId);
            assertTrue(waitUntil(() -> adapter.messages.contains("聚合完成"), 5000));
            assertTrue(store.hasRunEventType("parent-run", "children_aggregated"));

            String secondRunId = runner.submit(request);
            assertNotNull(secondRunId);
            assertTrue(waitUntil(() -> {
                AgentRun run = store.getRun(secondRunId);
                return run != null && run.getStatus() == RunStatus.SUCCEEDED;
            }, 5000));
            assertEquals(1, adapter.outbounds.stream().filter(outbound -> "聚合完成".equals(outbound.getContent())).count());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void childContinuationCanDeliverIncrementalReplyBeforeAllChildrenComplete() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) -> "一个子任务已完成，先同步进展。";
        SystemEventRunner runner = new SystemEventRunner(conversationAgent, store, scheduler, registry, properties);

        try {
            AgentRun parentRun = new AgentRun();
            parentRun.setRunId("parent-run");
            parentRun.setSessionKey("dingtalk:group:group-1");
            parentRun.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
            parentRun.setSourceUserVersion(1L);
            store.saveRun(parentRun);

            AgentRun childDone = new AgentRun();
            childDone.setRunId("child-1");
            childDone.setParentRunId("parent-run");
            childDone.setParentSessionKey("dingtalk:group:group-1");
            childDone.setStatus(RunStatus.SUCCEEDED);
            childDone.setFinalResponse("done");
            store.saveRun(childDone);

            AgentRun childRunning = new AgentRun();
            childRunning.setRunId("child-2");
            childRunning.setParentRunId("parent-run");
            childRunning.setParentSessionKey("dingtalk:group:group-1");
            childRunning.setStatus(RunStatus.RUNNING);
            store.saveRun(childRunning);

            SystemEventRequest request = new SystemEventRequest();
            request.setSourceKind(RuntimeSourceKind.CHILD_CONTINUATION);
            request.setPolicy(SystemEventPolicy.USER_VISIBLE_OPTIONAL);
            request.setSessionKey("dingtalk:group:group-1");
            request.setReplyTarget(new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1"));
            request.setContent("child completed");
            request.setRelatedRunId("parent-run");

            String runId = runner.submit(request);
            assertNotNull(runId);
            assertTrue(waitUntil(() -> adapter.messages.contains("一个子任务已完成，先同步进展。"), 5000));
            assertTrue(store.readConversationEvents("dingtalk:group:group-1").stream()
                    .anyMatch(event -> "assistant_reply".equals(event.getEventType())
                            && "一个子任务已完成，先同步进展。".equals(event.getContent())));
            assertTrue(!store.hasRunEventType("parent-run", "children_aggregated"));
        } finally {
            scheduler.shutdown();
        }
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
        private final List<String> messages = new CopyOnWriteArrayList<String>();
        private final List<OutboundEnvelope> outbounds = new CopyOnWriteArrayList<OutboundEnvelope>();

        @Override
        public ChannelType channelType() {
            return ChannelType.DINGTALK;
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
}
