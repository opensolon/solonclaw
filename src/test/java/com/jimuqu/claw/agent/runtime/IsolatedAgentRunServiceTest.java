package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.job.AgentTurnSpec;
import com.jimuqu.claw.agent.job.JobDeliveryMode;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.IsolatedAgentRunService;
import com.jimuqu.claw.agent.runtime.support.AgentTurnRequest;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsolatedAgentRunServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void deliveryNoneSuppressesFinalReply() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) -> "自动化结果";
        IsolatedAgentRunService service = new IsolatedAgentRunService(conversationAgent, store, scheduler, registry, properties);

        try {
            String runId = service.submit(request(JobDeliveryMode.NONE, null));
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
    void boundReplyTargetDeliverySendsFinalReply() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) -> "自动化结果";
        IsolatedAgentRunService service = new IsolatedAgentRunService(conversationAgent, store, scheduler, registry, properties);

        try {
            ReplyTarget boundReplyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1");
            String runId = service.submit(request(JobDeliveryMode.BOUND_REPLY_TARGET, boundReplyTarget));
            assertNotNull(runId);
            assertTrue(waitUntil(() -> adapter.messages.contains("自动化结果"), 5000));
            assertEquals("group-1", adapter.outbounds.get(0).getReplyTarget().getConversationId());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void lastRouteDeliveryUsesLatestExternalRoute() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ReplyTarget latestReplyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-last", "user-last");
        store.rememberReplyTarget("dingtalk:group:group-last", latestReplyTarget);

        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        ConversationAgent conversationAgent = (request, progressConsumer) -> "自动化结果";
        IsolatedAgentRunService service = new IsolatedAgentRunService(conversationAgent, store, scheduler, registry, properties);

        try {
            String runId = service.submit(request(JobDeliveryMode.LAST_ROUTE, null));
            assertNotNull(runId);
            assertTrue(waitUntil(() -> adapter.messages.contains("自动化结果"), 5000));
            assertEquals("group-last", adapter.outbounds.get(0).getReplyTarget().getConversationId());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void lightContextFlagPropagatesToExecutionRequest() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);
        SolonClawProperties properties = new SolonClawProperties();
        AtomicReference<com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest> capturedRequest =
                new AtomicReference<com.jimuqu.claw.agent.runtime.support.ConversationExecutionRequest>();
        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            capturedRequest.set(request);
            return "自动化结果";
        };
        IsolatedAgentRunService service = new IsolatedAgentRunService(conversationAgent, store, scheduler, registry, properties);

        try {
            AgentTurnRequest request = request(JobDeliveryMode.NONE, null);
            request.getAgentTurn().setLightContext(true);

            String runId = service.submit(request);
            assertNotNull(runId);
            assertTrue(waitUntil(() -> {
                AgentRun run = store.getRun(runId);
                return run != null && run.getStatus() == RunStatus.SUCCEEDED;
            }, 5000));
            assertNotNull(capturedRequest.get());
            assertTrue(capturedRequest.get().isLightContext());
        } finally {
            scheduler.shutdown();
        }
    }

    private AgentTurnRequest request(JobDeliveryMode deliveryMode, ReplyTarget boundReplyTarget) {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setSourceKind(com.jimuqu.claw.agent.model.enums.RuntimeSourceKind.JOB_AGENT_TURN);
        request.setJobName("agent-job");
        request.setBoundSessionKey("dingtalk:group:group-1");
        request.setBoundReplyTarget(boundReplyTarget);
        request.setDeliveryMode(deliveryMode);
        AgentTurnSpec spec = new AgentTurnSpec();
        spec.setMessage("执行自动化任务");
        request.setAgentTurn(spec);
        return request;
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
