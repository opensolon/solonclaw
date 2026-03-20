package com.jimuqu.claw.agent.store;

import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.event.ConversationEvent;
import com.jimuqu.claw.agent.model.event.RunEvent;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStoreServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void loadConversationHistoryBeforeKeepsInboundOrder() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        InboundEnvelope first = inbound("session-a", "msg-1", "first", RuntimeSourceKind.USER_MESSAGE);
        InboundEnvelope second = inbound("session-a", "msg-2", "second", RuntimeSourceKind.USER_MESSAGE);

        long firstVersion = store.appendInboundConversationEvent(first);
        long secondVersion = store.appendInboundConversationEvent(second);
        store.appendAssistantConversationEvent("session-a", "run-1", "msg-1", firstVersion, RuntimeSourceKind.USER_MESSAGE, "reply-first");
        store.appendAssistantConversationEvent("session-a", "run-2", "msg-2", secondVersion, RuntimeSourceKind.USER_MESSAGE, "reply-second");

        List<ChatMessage> history = store.loadConversationHistoryBefore("session-a", 5L);

        assertEquals(4, history.size());
        assertEquals("first", history.get(0).getContent());
        assertEquals("reply-first", history.get(1).getContent());
        assertEquals("second", history.get(2).getContent());
        assertEquals("reply-second", history.get(3).getContent());
    }

    @Test
    void marksIncompleteRunsAbortedOnStartup() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        AgentRun run = new AgentRun();
        run.setRunId("run-abort");
        run.setSessionKey("debug-web:test");
        run.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
        run.setStatus(RunStatus.RUNNING);
        run.setCreatedAt(System.currentTimeMillis());
        store.saveRun(run);

        RuntimeStoreService restarted = new RuntimeStoreService(tempDir.toFile());

        AgentRun restored = restarted.getRun("run-abort");
        assertNotNull(restored);
        assertEquals(RunStatus.ABORTED, restored.getStatus());

        List<RunEvent> events = restarted.getRunEvents("run-abort", 0);
        assertEquals("aborted", events.get(events.size() - 1).getMessage());
        assertEquals(RuntimeSourceKind.USER_MESSAGE, events.get(0).getSourceKind());
    }

    @Test
    void remembersLatestExternalRouteWithSessionKey() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "cid", "uid");

        store.rememberReplyTarget("dingtalk:group:cid", replyTarget);

        assertEquals("dingtalk:group:cid", store.getLatestExternalRoute().getSessionKey());
        assertEquals("cid", store.getLatestExternalRoute().getReplyTarget().getConversationId());
        assertEquals("cid", store.getReplyTarget("dingtalk:group:cid").getConversationId());
    }

    @Test
    void loadConversationHistoryBeforeIncludesStructuredChildEvents() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        InboundEnvelope parent = inbound("session-a", "msg-1", "parent-question", RuntimeSourceKind.USER_MESSAGE);
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

    @Test
    void appendInboundConversationEventUsesSourceKindForRoleAndEventType() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        InboundEnvelope user = inbound("session-a", "msg-1", "question", RuntimeSourceKind.USER_MESSAGE);
        long userVersion = store.appendInboundConversationEvent(user);

        InboundEnvelope system = inbound("session-a", "system-1", "heartbeat", RuntimeSourceKind.HEARTBEAT_EVENT);
        system.setChannelType(ChannelType.SYSTEM);
        system.setHistoryAnchorVersion(userVersion);
        long systemVersion = store.appendInboundConversationEvent(system);

        List<ConversationEvent> events = store.readConversationEvents("session-a");
        assertEquals(2L, systemVersion);
        assertEquals("user_message", events.get(0).getEventType());
        assertEquals("user", events.get(0).getRole());
        assertEquals(RuntimeSourceKind.USER_MESSAGE, events.get(0).getSourceKind());
        assertEquals("system_event", events.get(1).getEventType());
        assertEquals("system", events.get(1).getRole());
        assertEquals(RuntimeSourceKind.HEARTBEAT_EVENT, events.get(1).getSourceKind());
    }

    @Test
    void summarizeChildRunsByParentRunAndBatchKey() {
        RuntimeStoreService store = new RuntimeStoreService(tempDir.toFile());

        AgentRun batchA1 = child("child-a1", "parent-1", "session-a", "plan-A", RunStatus.SUCCEEDED, 1L);
        AgentRun batchA2 = child("child-a2", "parent-1", "session-a", "plan-A", RunStatus.RUNNING, 2L);
        AgentRun batchB1 = child("child-b1", "parent-1", "session-a", "plan-B", RunStatus.SUCCEEDED, 3L);
        store.saveRun(batchA1);
        store.saveRun(batchA2);
        store.saveRun(batchB1);

        ParentRunChildrenSummary summary = store.summarizeChildRuns("parent-1", "plan-A");

        assertEquals("plan-A", summary.getBatchKey());
        assertEquals(2, summary.getTotalChildren());
        assertEquals(1, summary.getSucceededChildren());
        assertEquals(1, summary.getPendingChildren());
        assertTrue(!summary.isAllCompleted());
    }

    private InboundEnvelope inbound(String sessionKey, String messageId, String content, RuntimeSourceKind sourceKind) {
        InboundEnvelope envelope = new InboundEnvelope();
        envelope.setSessionKey(sessionKey);
        envelope.setMessageId(messageId);
        envelope.setChannelType(ChannelType.DEBUG_WEB);
        envelope.setConversationType(ConversationType.PRIVATE);
        envelope.setConversationId("conv");
        envelope.setSenderId("user");
        envelope.setContent(content);
        envelope.setReceivedAt(System.currentTimeMillis());
        envelope.setSourceKind(sourceKind);
        return envelope;
    }

    private AgentRun child(String runId, String parentRunId, String parentSessionKey, String batchKey, RunStatus status, long createdAt) {
        AgentRun run = new AgentRun();
        run.setRunId(runId);
        run.setParentRunId(parentRunId);
        run.setParentSessionKey(parentSessionKey);
        run.setBatchKey(batchKey);
        run.setStatus(status);
        run.setCreatedAt(createdAt);
        run.setSourceKind(RuntimeSourceKind.USER_MESSAGE);
        return run;
    }
}
