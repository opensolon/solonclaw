package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.AgentRun;
import com.jimuqu.claw.agent.model.ChannelType;
import com.jimuqu.claw.agent.model.ConversationType;
import com.jimuqu.claw.agent.model.InboundEnvelope;
import com.jimuqu.claw.agent.model.OutboundEnvelope;
import com.jimuqu.claw.agent.model.ReplyTarget;
import com.jimuqu.claw.agent.model.RunStatus;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.agent.runtime.ParentRunChildrenSummary;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Agent 运行时的并发调度和忙时回执行为。
 */
class AgentRuntimeServiceTest {
    /** 临时测试目录。 */
    @TempDir
    Path tempDir;

    /**
     * 验证同会话繁忙时第二条消息会收到即时回执。
     *
     * @throws Exception 执行异常
     */
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

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);

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

            assertEquals(3, adapter.outbounds.size());
            assertEquals("reply-question-1", runtimeService.getRun(firstRunId).getFinalResponse());
            assertEquals("reply-question-2", runtimeService.getRun(secondRunId).getFinalResponse());
        } finally {
            releaseFirst.countDown();
            scheduler.shutdown();
        }
    }

    /**
     * 验证父运行可派生子任务，子任务完成后会触发父会话 continuation run。
     *
     * @throws Exception 执行异常
     */
    @Test
    void childRunCompletionContinuesParentConversation() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            String message = request.getCurrentMessage();
            if ("question-parent".equals(message)) {
                request.getSpawnTaskSupport().spawnTask("research-child");
                progressConsumer.accept("spawned");
                return "parent-waiting";
            }
            if ("research-child".equals(message)) {
                progressConsumer.accept("child-running");
                return "child-result";
            }
            if (message != null && message.contains("[内部事件] 子任务已完成")) {
                return "final-parent-answer";
            }
            return "reply-" + message;
        };

        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getScheduler().setMaxConcurrentPerConversation(1);
        properties.getAgent().getScheduler().setAckWhenBusy(false);

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            String parentRunId = runtimeService.submitInbound(inbound("msg-parent", "question-parent"));
            assertNotNull(parentRunId);

            assertTrue(waitUntil(() -> {
                AgentRun parentRun = runtimeService.getRun(parentRunId);
                return parentRun != null && parentRun.getStatus() == RunStatus.WAITING_CHILDREN;
            }, 3000));

            assertTrue(waitUntil(() -> adapter.messages.contains("final-parent-answer"), 5000));

            assertEquals(1, adapter.outbounds.size());
            assertEquals("final-parent-answer", adapter.outbounds.get(0).getContent());
            assertEquals(RunStatus.WAITING_CHILDREN, runtimeService.getRun(parentRunId).getStatus());
        } finally {
            scheduler.shutdown();
        }
    }

    /**
     * 验证后续一句“看看上个任务的情况”可以通过查询能力读取最近子任务状态。
     *
     * @throws Exception 执行异常
     */
    @Test
    void followupMessageCanInspectLatestChildRunStatus() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            String message = request.getCurrentMessage();
            if ("question-parent".equals(message)) {
                request.getSpawnTaskSupport().spawnTask("research-child");
                return "parent-waiting";
            }
            if ("research-child".equals(message)) {
                return "child-result";
            }
            if (message != null && message.contains("[内部事件] 子任务已完成")) {
                return "child-finished";
            }
            if ("看看上个任务的情况".equals(message)) {
                AgentRun latestChild = request.getRunQuerySupport().getLatestChildRun();
                return "latest-child-status=" + (latestChild == null ? "NONE" : latestChild.getStatus());
            }
            return "reply-" + message;
        };

        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getScheduler().setMaxConcurrentPerConversation(1);
        properties.getAgent().getScheduler().setAckWhenBusy(false);

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            runtimeService.submitInbound(inbound("msg-parent", "question-parent"));
            assertTrue(waitUntil(() -> adapter.messages.contains("child-finished"), 5000));

            String inspectRunId = runtimeService.submitInbound(inbound("msg-inspect", "看看上个任务的情况"));
            assertNotNull(inspectRunId);
            assertTrue(waitUntil(() -> adapter.messages.contains("latest-child-status=SUCCEEDED"), 5000));
        } finally {
            scheduler.shutdown();
        }
    }

    /**
     * 验证当前运行可通过主动通知能力直接向当前会话用户发消息。
     *
     * @throws Exception 执行异常
     */
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
        properties.getAgent().getScheduler().setMaxConcurrentPerConversation(1);
        properties.getAgent().getScheduler().setAckWhenBusy(false);

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            String runId = runtimeService.submitInbound(inbound("msg-notify", "请主动通知我"));
            assertNotNull(runId);
            assertTrue(waitUntil(() -> adapter.messages.contains("这是一条主动通知"), 5000));
            assertEquals(1, adapter.outbounds.size());
            assertEquals("这是一条主动通知", adapter.outbounds.get(0).getContent());
        } finally {
            scheduler.shutdown();
        }
    }

    /**
     * 验证可按父运行聚合多个子任务，并判断是否全部完成。
     *
     * @throws Exception 执行异常
     */
    @Test
    void followupMessageCanInspectParentRunChildSummary() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        CountDownLatch slowChildStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowChild = new CountDownLatch(1);

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            String message = request.getCurrentMessage();
            if ("question-parent-multi".equals(message)) {
                request.getSpawnTaskSupport().spawnTask("child-fast-1");
                request.getSpawnTaskSupport().spawnTask("child-fast-2");
                request.getSpawnTaskSupport().spawnTask("child-slow-3");
                return "parent-waiting-multi";
            }
            if ("child-fast-1".equals(message) || "child-fast-2".equals(message)) {
                return "done-" + message;
            }
            if ("child-slow-3".equals(message)) {
                slowChildStarted.countDown();
                assertTrue(releaseSlowChild.await(5, TimeUnit.SECONDS));
                return "done-" + message;
            }
            if (message != null && message.contains("[内部事件] 子任务已完成")) {
                return "child-finished";
            }
            if ("看看这批子任务是否都完成了".equals(message)) {
                ParentRunChildrenSummary summary = request.getRunQuerySupport().getChildSummary(null, null);
                if (summary == null) {
                    return "summary-missing";
                }
                return "summary total=" + summary.getTotalChildren()
                        + " pending=" + summary.getPendingChildren()
                        + " allCompleted=" + summary.isAllCompleted();
            }
            return "reply-" + message;
        };

        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getScheduler().setMaxConcurrentPerConversation(1);
        properties.getAgent().getScheduler().setAckWhenBusy(false);

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            String parentRunId = runtimeService.submitInbound(inbound("msg-parent-multi", "question-parent-multi"));
            assertNotNull(parentRunId);
            assertTrue(slowChildStarted.await(3, TimeUnit.SECONDS));

            String inspectPendingRunId = runtimeService.submitInbound(inbound("msg-check-pending", "看看这批子任务是否都完成了"));
            assertNotNull(inspectPendingRunId);
            assertTrue(waitUntil(() -> adapter.messages.stream().anyMatch(message ->
                    message.contains("summary total=3 pending=1 allCompleted=false")), 5000));

            releaseSlowChild.countDown();
            assertTrue(waitUntil(() -> adapter.messages.stream().filter("child-finished"::equals).count() >= 3, 5000));

            String inspectDoneRunId = runtimeService.submitInbound(inbound("msg-check-done", "看看这批子任务是否都完成了"));
            assertNotNull(inspectDoneRunId);
            assertTrue(waitUntil(() -> adapter.messages.stream().anyMatch(message ->
                    message.contains("summary total=3 pending=0 allCompleted=true")), 5000));
        } finally {
            releaseSlowChild.countDown();
            scheduler.shutdown();
        }
    }

    /**
     * 验证父会话可在子任务未全部完成时返回 NO_REPLY，待全部完成后再统一汇总回复。
     *
     * @throws Exception 执行异常
     */
    @Test
    void parentCanSuppressIntermediateRepliesUntilAllChildrenComplete() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        CountDownLatch slowChildStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowChild = new CountDownLatch(1);

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            String message = request.getCurrentMessage();
            if ("question-parent-aggregate".equals(message)) {
                request.getSpawnTaskSupport().spawnTask("aggregate-fast-1");
                request.getSpawnTaskSupport().spawnTask("aggregate-fast-2");
                request.getSpawnTaskSupport().spawnTask("aggregate-slow-3");
                return "parent-aggregate-waiting";
            }
            if ("aggregate-fast-1".equals(message) || "aggregate-fast-2".equals(message)) {
                return "done-" + message;
            }
            if ("aggregate-slow-3".equals(message)) {
                slowChildStarted.countDown();
                assertTrue(releaseSlowChild.await(5, TimeUnit.SECONDS));
                return "done-" + message;
            }
            if (message != null && message.contains("[内部事件] 子任务已完成")) {
                ParentRunChildrenSummary summary = request.getRunQuerySupport().getChildSummary(null, null);
                if (summary == null || !summary.isAllCompleted()) {
                    return AgentRuntimeService.NO_REPLY;
                }
                return AgentRuntimeService.FINAL_REPLY_ONCE_PREFIX
                        + "final-aggregate total=" + summary.getTotalChildren()
                        + " succeeded=" + summary.getSucceededChildren()
                        + " failed=" + summary.getFailedChildren();
            }
            return "reply-" + message;
        };

        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getScheduler().setMaxConcurrentPerConversation(1);
        properties.getAgent().getScheduler().setAckWhenBusy(false);

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            String parentRunId = runtimeService.submitInbound(inbound("msg-parent-aggregate", "question-parent-aggregate"));
            assertNotNull(parentRunId);
            assertTrue(slowChildStarted.await(3, TimeUnit.SECONDS));

            assertTrue(waitUntil(() -> runtimeService.getRun(parentRunId).getStatus() == RunStatus.WAITING_CHILDREN, 3000));
            assertTrue(waitUntil(() -> adapter.outbounds.isEmpty(), 1000));

            releaseSlowChild.countDown();
            assertTrue(waitUntil(() -> adapter.messages.stream().anyMatch(message ->
                    message.contains("final-aggregate total=3 succeeded=3 failed=0")), 5000));
            assertEquals(1, adapter.outbounds.stream()
                    .filter(outbound -> outbound.getContent().contains("final-aggregate total=3 succeeded=3 failed=0"))
                    .count());
        } finally {
            releaseSlowChild.countDown();
            scheduler.shutdown();
        }
    }

    /**
     * 验证同一父运行下可按 batchKey 查询指定批次的子任务聚合结果。
     *
     * @throws Exception 执行异常
     */
    @Test
    void followupMessageCanInspectChildSummaryByBatchKey() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        CountDownLatch slowBatchStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowBatch = new CountDownLatch(1);

        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            String message = request.getCurrentMessage();
            if ("question-parent-batch".equals(message)) {
                request.getSpawnTaskSupport().spawnTask("batch-A-fast", "plan-A");
                request.getSpawnTaskSupport().spawnTask("batch-A-slow", "plan-A");
                request.getSpawnTaskSupport().spawnTask("batch-B-fast", "plan-B");
                return "batch-waiting";
            }
            if ("batch-A-fast".equals(message) || "batch-B-fast".equals(message)) {
                return "done-" + message;
            }
            if ("batch-A-slow".equals(message)) {
                slowBatchStarted.countDown();
                assertTrue(releaseSlowBatch.await(5, TimeUnit.SECONDS));
                return "done-" + message;
            }
            if (message != null && message.contains("[内部事件] 子任务已完成")) {
                return AgentRuntimeService.NO_REPLY;
            }
            if ("看看 plan-A 这批任务的情况".equals(message)) {
                ParentRunChildrenSummary summary = request.getRunQuerySupport().getChildSummary(null, "plan-A");
                if (summary == null) {
                    return "plan-A-missing";
                }
                return "plan-A total=" + summary.getTotalChildren()
                        + " pending=" + summary.getPendingChildren()
                        + " allCompleted=" + summary.isAllCompleted();
            }
            return "reply-" + message;
        };

        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getScheduler().setMaxConcurrentPerConversation(1);
        properties.getAgent().getScheduler().setAckWhenBusy(false);

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            String parentRunId = runtimeService.submitInbound(inbound("msg-parent-batch", "question-parent-batch"));
            assertNotNull(parentRunId);
            assertTrue(slowBatchStarted.await(3, TimeUnit.SECONDS));

            String inspectPendingRunId = runtimeService.submitInbound(inbound("msg-planA-pending", "看看 plan-A 这批任务的情况"));
            assertNotNull(inspectPendingRunId);
            assertTrue(waitUntil(() -> adapter.messages.stream().anyMatch(message ->
                    message.contains("plan-A total=2 pending=1 allCompleted=false")), 5000));

            releaseSlowBatch.countDown();
            String inspectDoneRunId = runtimeService.submitInbound(inbound("msg-planA-done", "看看 plan-A 这批任务的情况"));
            assertNotNull(inspectDoneRunId);
            assertTrue(waitUntil(() -> adapter.messages.stream().anyMatch(message ->
                    message.contains("plan-A total=2 pending=0 allCompleted=true")), 5000));
        } finally {
            releaseSlowBatch.countDown();
            scheduler.shutdown();
        }
    }

    /**
     * 验证系统消息不会覆盖最近一次真实外部会话路由。
     */
    @Test
    void systemMessageDoesNotOverrideLatestExternalRoute() throws Exception {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        ConversationAgent conversationAgent = (request, progressConsumer) -> "reply-" + request.getCurrentMessage();
        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getScheduler().setAckWhenBusy(false);

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            runtimeService.submitInbound(inbound("msg-latest", "question-latest"));

            ReplyTarget otherReplyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-2", "user-2");
            runtimeService.submitSystemMessage("dingtalk:group:group-2", otherReplyTarget, "scheduled-message");

            assertEquals("dingtalk:group:group-1", store.getLatestExternalRoute().getSessionKey());
            assertEquals("group-1", store.getLatestExternalRoute().getReplyTarget().getConversationId());
            assertTrue(waitUntil(() -> adapter.outbounds.size() >= 2, 2000));
        } finally {
            scheduler.shutdown();
        }
    }

    /**
     * 构造一条测试入站消息。
     *
     * @param messageId 消息标识
     * @param content 文本内容
     * @return 入站消息
     */
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
        return envelope;
    }

    /**
     * 轮询等待条件成立。
     *
     * @param condition 条件判断
     * @param timeoutMs 超时时间
     * @return 若条件成立则返回 true
     * @throws InterruptedException 线程中断异常
     */
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

    /**
     * 记录测试发送内容的伪渠道适配器。
     */
    private static class RecordingChannelAdapter implements ChannelAdapter {
        /** 记录发送文本。 */
        private final List<String> messages = new CopyOnWriteArrayList<>();
        /** 记录完整出站消息。 */
        private final List<OutboundEnvelope> outbounds = new CopyOnWriteArrayList<>();

        /**
         * 返回适配器渠道类型。
         *
         * @return 钉钉渠道
         */
        @Override
        public ChannelType channelType() {
            return ChannelType.DINGTALK;
        }

        /**
         * 记录一次发送请求。
         *
         * @param outboundEnvelope 出站消息
         */
        @Override
        public void send(OutboundEnvelope outboundEnvelope) {
            outbounds.add(outboundEnvelope);
            messages.add(outboundEnvelope.getContent());
        }
    }
}
