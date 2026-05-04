package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.storage.session.SqliteAgentSession;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.chat.message.ChatMessage;

public class SqliteAgentSessionTest {
    @Test
    void shouldPersistMessagesAndFlowSnapshotIntoSqlite() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");

        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        agentSession.addMessage(
                Arrays.asList(ChatMessage.ofUser("你好"), ChatMessage.ofAssistant("收到")));
        agentSession.getContext().put("flag", "demo");
        agentSession.pending(true, "need-review");
        agentSession.updateSnapshot();

        SessionRecord reloaded = env.sessionRepository.findById(session.getSessionId());
        assertThat(reloaded.getNdjson()).contains("你好");
        assertThat(reloaded.getNdjson()).contains("收到");
        assertThat(reloaded.getAgentSnapshotJson()).isNotBlank();

        SqliteAgentSession restored = new SqliteAgentSession(reloaded);
        assertThat(restored.getMessages()).hasSize(2);
        assertThat(restored.getContext().<String>getAs("flag")).isEqualTo("demo");
        assertThat(restored.isPending()).isTrue();
        assertThat(restored.getPendingReason()).isEqualTo("need-review");
    }

    @Test
    void shouldRestoreStopLoopHistoryAsLinkedList() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-b:user-b");

        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        ReActTrace trace = new ReActTrace();
        trace.setExtra("stoploop_history", new ArrayList<String>(Arrays.asList("first", "second")));
        agentSession.getContext().put("trace-1", trace);
        agentSession.updateSnapshot();

        SessionRecord reloaded = env.sessionRepository.findById(session.getSessionId());
        SqliteAgentSession restored = new SqliteAgentSession(reloaded);
        ReActTrace restoredTrace = restored.getContext().getAs("trace-1");

        assertThat(restoredTrace.getExtra("stoploop_history")).isInstanceOf(LinkedList.class);
    }
}
