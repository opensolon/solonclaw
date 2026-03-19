package com.jimuqu.claw.agent.store;

import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.event.ConversationEvent;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.enums.InboundTriggerType;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.event.RunEvent;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证运行时存储服务的持久化和恢复行为。
 */
class RuntimeStoreServiceTest {
    /** 临时测试目录。 */
    @TempDir
    Path tempDir;

    /**
     * 验证历史消息会按入站顺序重建。
     */
    @Test
    void loadConversationHistoryBeforeKeepsInboundOrder() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        InboundEnvelope first = inbound("session-a", "msg-1", "first");
        InboundEnvelope second = inbound("session-a", "msg-2", "second");

        long firstVersion = store.appendInboundConversationEvent(first);
        long secondVersion = store.appendInboundConversationEvent(second);
        store.appendAssistantConversationEvent("session-a", "run-1", "msg-1", firstVersion, "reply-first");
        store.appendAssistantConversationEvent("session-a", "run-2", "msg-2", secondVersion, "reply-second");

        List<ChatMessage> history = store.loadConversationHistoryBefore("session-a", 5L);

        assertEquals(4, history.size());
        assertEquals("first", history.get(0).getContent());
        assertEquals("reply-first", history.get(1).getContent());
        assertEquals("second", history.get(2).getContent());
        assertEquals("reply-second", history.get(3).getContent());
    }

    /**
     * 验证重启后未完成任务会被标记为中止。
     */
    @Test
    void marksIncompleteRunsAbortedOnStartup() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        AgentRun run = new AgentRun();
        run.setRunId("run-abort");
        run.setSessionKey("debug-web:test");
        run.setStatus(RunStatus.RUNNING);
        run.setCreatedAt(System.currentTimeMillis());
        store.saveRun(run);

        RuntimeStoreService restarted = new RuntimeStoreService(tempDir.toFile());

        AgentRun restored = restarted.getRun("run-abort");
        assertNotNull(restored);
        assertEquals(RunStatus.ABORTED, restored.getStatus());

        List<RunEvent> events = restarted.getRunEvents("run-abort", 0);
        assertEquals("aborted", events.get(events.size() - 1).getMessage());
    }

    /**
     * 验证最近外部路由会带上会话键一起保存。
     */
    @Test
    void remembersLatestExternalRouteWithSessionKey() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "cid", "uid");

        store.rememberReplyTarget("dingtalk:group:cid", replyTarget);

        assertEquals("dingtalk:group:cid", store.getLatestExternalRoute().getSessionKey());
        assertEquals("cid", store.getLatestExternalRoute().getReplyTarget().getConversationId());
        assertEquals("cid", store.getReplyTarget("dingtalk:group:cid").getConversationId());
    }

    /**
     * 验证结构化子任务事件会以系统消息形式进入会话历史。
     */
    @Test
    void loadConversationHistoryBeforeIncludesStructuredChildEvents() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        InboundEnvelope parent = inbound("session-a", "msg-1", "parent-question");
        long parentVersion = store.appendInboundConversationEvent(parent);

        AgentRun childRun = new AgentRun();
        childRun.setRunId("child-1");
        childRun.setSessionKey("session-a:subtask:1");
        childRun.setTaskDescription("research-child");
        childRun.setStatus(RunStatus.SUCCEEDED);
        childRun.setFinalResponse("child-result");

        store.appendChildRunSpawnedEvent("session-a", "parent-run", parentVersion, childRun);
        store.appendChildRunCompletedEvent("session-a", "parent-run", parentVersion, childRun);

        List<ChatMessage> history = store.loadConversationHistoryBefore("session-a", 10L);

        assertEquals(3, history.size());
        assertEquals("parent-question", history.get(0).getContent());
        assertEquals("SYSTEM", history.get(1).getRole().toString());
        assertEquals("SYSTEM", history.get(2).getRole().toString());
        assertTrue(history.get(1).getContent().contains("childRunId=child-1"));
        assertTrue(history.get(2).getContent().contains("result="));
    }

    /**
     * 验证可见系统消息会以 system 角色写入会话事件。
     */
    @Test
    void appendInboundConversationEventUsesSystemRoleForVisibleSystemTrigger() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        InboundEnvelope user = inbound("session-a", "msg-1", "question");
        long userVersion = store.appendInboundConversationEvent(user);

        InboundEnvelope system = inbound("session-a", "system-1", "scheduled-task");
        system.setChannelType(ChannelType.SYSTEM);
        system.setTriggerType(InboundTriggerType.SYSTEM_VISIBLE);
        system.setHistoryAnchorVersion(userVersion);

        long systemVersion = store.appendInboundConversationEvent(system);
        List<ConversationEvent> events = store.readConversationEvents("session-a");

        assertEquals(2L, systemVersion);
        assertEquals("user_message", events.get(0).getEventType());
        assertEquals("system_event", events.get(1).getEventType());
        assertEquals("system", events.get(1).getRole());
        assertEquals(userVersion, events.get(1).getSourceUserVersion());
    }

    /**
     * 验证同一锚点下的系统事件与回复会按事件版本顺序重建。
     */
    @Test
    void loadConversationHistoryBeforeKeepsAnchoredSystemEventOrder() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        InboundEnvelope user = inbound("session-a", "msg-1", "question");
        long userVersion = store.appendInboundConversationEvent(user);
        store.appendAssistantConversationEvent("session-a", "run-1", "msg-1", userVersion, "reply-user");

        InboundEnvelope system = inbound("session-a", "system-1", "scheduled-task");
        system.setChannelType(ChannelType.SYSTEM);
        system.setTriggerType(InboundTriggerType.SYSTEM_VISIBLE);
        system.setHistoryAnchorVersion(userVersion);
        long systemVersion = store.appendInboundConversationEvent(system);
        store.appendAssistantConversationEvent("session-a", "run-2", "system-1", userVersion, "reply-system");

        List<ChatMessage> history = store.loadConversationHistoryBefore("session-a", systemVersion + 10L);

        assertEquals(4, history.size());
        assertEquals("question", history.get(0).getContent());
        assertEquals("reply-user", history.get(1).getContent());
        assertEquals("SYSTEM", history.get(2).getRole().toString());
        assertEquals("scheduled-task", history.get(2).getContent());
        assertEquals("reply-system", history.get(3).getContent());
    }

    /**
     * 验证可按父运行聚合多个子任务状态。
     */
    @Test
    void summarizeChildRunsByParentRun() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        AgentRun child1 = new AgentRun();
        child1.setRunId("child-1");
        child1.setParentRunId("parent-1");
        child1.setParentSessionKey("session-a");
        child1.setTaskDescription("task-1");
        child1.setStatus(RunStatus.SUCCEEDED);
        child1.setCreatedAt(1L);
        store.saveRun(child1);

        AgentRun child2 = new AgentRun();
        child2.setRunId("child-2");
        child2.setParentRunId("parent-1");
        child2.setParentSessionKey("session-a");
        child2.setTaskDescription("task-2");
        child2.setStatus(RunStatus.RUNNING);
        child2.setCreatedAt(2L);
        store.saveRun(child2);

        ParentRunChildrenSummary summary = store.summarizeChildRuns("parent-1");

        assertEquals("parent-1", summary.getParentRunId());
        assertEquals(2, summary.getTotalChildren());
        assertEquals(1, summary.getSucceededChildren());
        assertEquals(0, summary.getFailedChildren());
        assertEquals(1, summary.getPendingChildren());
        assertTrue(!summary.isAllCompleted());
    }

    /**
     * 验证可按 batchKey 只聚合同一父运行下的一批子任务。
     */
    @Test
    void summarizeChildRunsByParentRunAndBatchKey() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        AgentRun batchA1 = new AgentRun();
        batchA1.setRunId("child-a1");
        batchA1.setParentRunId("parent-1");
        batchA1.setParentSessionKey("session-a");
        batchA1.setBatchKey("plan-A");
        batchA1.setTaskDescription("task-a1");
        batchA1.setStatus(RunStatus.SUCCEEDED);
        batchA1.setCreatedAt(1L);
        store.saveRun(batchA1);

        AgentRun batchA2 = new AgentRun();
        batchA2.setRunId("child-a2");
        batchA2.setParentRunId("parent-1");
        batchA2.setParentSessionKey("session-a");
        batchA2.setBatchKey("plan-A");
        batchA2.setTaskDescription("task-a2");
        batchA2.setStatus(RunStatus.RUNNING);
        batchA2.setCreatedAt(2L);
        store.saveRun(batchA2);

        AgentRun batchB1 = new AgentRun();
        batchB1.setRunId("child-b1");
        batchB1.setParentRunId("parent-1");
        batchB1.setParentSessionKey("session-a");
        batchB1.setBatchKey("plan-B");
        batchB1.setTaskDescription("task-b1");
        batchB1.setStatus(RunStatus.SUCCEEDED);
        batchB1.setCreatedAt(3L);
        store.saveRun(batchB1);

        ParentRunChildrenSummary summary = store.summarizeChildRuns("parent-1", "plan-A");

        assertEquals("plan-A", summary.getBatchKey());
        assertEquals(2, summary.getTotalChildren());
        assertEquals(1, summary.getSucceededChildren());
        assertEquals(1, summary.getPendingChildren());
        assertTrue(!summary.isAllCompleted());
    }

    /**
     * 构造一条简化版入站消息。
     *
     * @param sessionKey 会话键
     * @param messageId 消息标识
     * @param content 文本内容
     * @return 入站消息
     */
    private InboundEnvelope inbound(String sessionKey, String messageId, String content) {
        InboundEnvelope envelope = new InboundEnvelope();
        envelope.setSessionKey(sessionKey);
        envelope.setMessageId(messageId);
        envelope.setChannelType(ChannelType.DEBUG_WEB);
        envelope.setConversationType(ConversationType.PRIVATE);
        envelope.setConversationId("conv");
        envelope.setSenderId("user");
        envelope.setContent(content);
        envelope.setReceivedAt(System.currentTimeMillis());
        return envelope;
    }
}


