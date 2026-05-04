package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.SessionSearchEntry;
import com.jimuqu.solon.claw.core.model.SessionSearchQuery;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;

public class SessionSearchServiceTest {
    @Test
    void shouldListRecentSessionsAndExcludeCurrentSession() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        SessionRecord current = env.sessionRepository.bindNewSession("MEMORY:current-room:user");
        current.setTitle("current session");
        current.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("current"),
                                ChatMessage.ofAssistant("current reply"))));
        env.sessionRepository.save(current);

        SessionRecord previous = env.sessionRepository.bindNewSession("MEMORY:history-room:user");
        previous.setTitle("history session");
        previous.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("older"),
                                ChatMessage.ofAssistant("older reply"))));
        env.sessionRepository.save(previous);

        List<SessionSearchEntry> entries =
                env.sessionSearchService.search("MEMORY:current-room:user", "", 3);

        assertThat(entries)
                .extracting(SessionSearchEntry::getSessionId)
                .doesNotContain(current.getSessionId());
        assertThat(entries).extracting(SessionSearchEntry::getTitle).contains("history session");
    }

    @Test
    void shouldFoldDelegatedChildSessionsIntoParentAndAvoidPersistingSearchSummary()
            throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        SessionRecord current = env.sessionRepository.bindNewSession("MEMORY:current-room:user");
        current.setTitle("current");
        current.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("current"),
                                ChatMessage.ofAssistant("current reply"))));
        env.sessionRepository.save(current);

        SessionRecord parent = env.sessionRepository.bindNewSession("MEMORY:history-room:user");
        parent.setTitle("parent session");
        parent.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("setup context"),
                                ChatMessage.ofAssistant("setup done"))));
        env.sessionRepository.save(parent);

        SessionRecord child =
                env.sessionRepository.cloneSession(
                        "MEMORY:history-room:user", parent.getSessionId(), "delegate");
        child.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("investigate bug-123"),
                                ChatMessage.ofAssistant("fixed bug-123 with file update"))));
        env.sessionRepository.save(child);

        String parentNdjson = env.sessionRepository.findById(parent.getSessionId()).getNdjson();
        String childNdjson = env.sessionRepository.findById(child.getSessionId()).getNdjson();

        List<SessionSearchEntry> entries =
                env.sessionSearchService.search("MEMORY:current-room:user", "bug-123", 3);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getSessionId()).isEqualTo(parent.getSessionId());
        assertThat(entries.get(0).getSummary()).isNotBlank();
        assertThat(env.sessionRepository.findById(parent.getSessionId()).getNdjson())
                .isEqualTo(parentNdjson);
        assertThat(env.sessionRepository.findById(child.getSessionId()).getNdjson())
                .isEqualTo(childNdjson);
    }

    @Test
    void shouldSearchToolNamesAndToolCalls() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        SessionRecord current = env.sessionRepository.bindNewSession("MEMORY:current-room:user");
        current.setTitle("current");
        current.setNdjson(MessageSupport.toNdjson(Arrays.asList(ChatMessage.ofUser("current"))));
        env.sessionRepository.save(current);

        SessionRecord previous = env.sessionRepository.bindNewSession("MEMORY:history-room:user");
        previous.setTitle("tool session");
        previous.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("run command"),
                                assistantWithToolCall(
                                        "execute_shell", "{\"command\":\"git status\"}"))));
        env.sessionRepository.save(previous);

        List<SessionSearchEntry> entries =
                env.sessionSearchService.search("MEMORY:current-room:user", "execute_shell", 3);

        assertThat(entries).extracting(SessionSearchEntry::getTitle).contains("tool session");
    }

    @Test
    void shouldExcludeCurrentLineageRootAndChildrenFromRecent() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        SessionRecord root = env.sessionRepository.bindNewSession("MEMORY:room:user");
        root.setTitle("root current");
        root.setNdjson(MessageSupport.toNdjson(Arrays.asList(ChatMessage.ofUser("root"))));
        env.sessionRepository.save(root);

        SessionRecord child =
                env.sessionRepository.cloneSession(
                        "MEMORY:room:user", root.getSessionId(), "child");
        child.setTitle("child current");
        child.setNdjson(MessageSupport.toNdjson(Arrays.asList(ChatMessage.ofUser("child"))));
        env.sessionRepository.save(child);
        env.sessionRepository.bindSource("MEMORY:room:user", child.getSessionId());

        SessionRecord other = env.sessionRepository.bindNewSession("MEMORY:other:user");
        other.setTitle("other");
        other.setNdjson(MessageSupport.toNdjson(Arrays.asList(ChatMessage.ofUser("other"))));
        env.sessionRepository.save(other);

        List<SessionSearchEntry> entries =
                env.sessionSearchService.search("MEMORY:room:user", "", 5);

        assertThat(entries).extracting(SessionSearchEntry::getTitle).contains("other");
        assertThat(entries)
                .extracting(SessionSearchEntry::getTitle)
                .doesNotContain("root current", "child current");
    }

    @Test
    void shouldSearchRealRunRecordsWhenRunIdIsProvided() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:run-room:user");
        session.setTitle("run backed session");
        env.sessionRepository.save(session);
        com.jimuqu.solon.claw.core.model.AgentRunRecord run =
                new com.jimuqu.solon.claw.core.model.AgentRunRecord();
        run.setRunId("run-search-1");
        run.setSessionId(session.getSessionId());
        run.setSourceKey("MEMORY:run-room:user");
        run.setStatus("success");
        run.setInputPreview("needle input");
        run.setFinalReplyPreview("needle output");
        run.setStartedAt(System.currentTimeMillis());
        run.setLastActivityAt(run.getStartedAt());
        env.agentRunRepository.saveRun(run);

        SessionSearchQuery query = new SessionSearchQuery();
        query.setRunId("run-search-1");
        query.setQuery("needle");
        query.setLimit(10);

        List<SessionSearchEntry> entries = env.sessionSearchService.search(query);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getRunId()).isEqualTo("run-search-1");
        assertThat(entries.get(0).getSessionId()).isEqualTo(session.getSessionId());
    }

    @Test
    void shouldSearchRealToolCallsWhenToolNameIsProvided() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:tool-room:user");
        session.setTitle("tool backed session");
        env.sessionRepository.save(session);
        com.jimuqu.solon.claw.core.model.AgentRunRecord run =
                new com.jimuqu.solon.claw.core.model.AgentRunRecord();
        run.setRunId("run-tool-1");
        run.setSessionId(session.getSessionId());
        run.setSourceKey("MEMORY:tool-room:user");
        run.setStatus("success");
        run.setStartedAt(System.currentTimeMillis());
        run.setLastActivityAt(run.getStartedAt());
        env.agentRunRepository.saveRun(run);
        ToolCallRecord toolCall = new ToolCallRecord();
        toolCall.setToolCallId(IdSupport.newId());
        toolCall.setRunId(run.getRunId());
        toolCall.setSessionId(session.getSessionId());
        toolCall.setSourceKey("MEMORY:tool-room:user");
        toolCall.setToolName("execute_shell");
        toolCall.setStatus("completed");
        toolCall.setArgsPreview("git status");
        toolCall.setResultPreview("clean");
        toolCall.setStartedAt(System.currentTimeMillis());
        env.agentRunRepository.saveToolCall(toolCall);

        SessionSearchQuery query = new SessionSearchQuery();
        query.setToolName("execute_shell");
        query.setQuery("git status");
        query.setLimit(10);

        List<SessionSearchEntry> entries = env.sessionSearchService.search(query);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getRunId()).isEqualTo(run.getRunId());
        assertThat(entries.get(0).getToolName()).isEqualTo("execute_shell");
    }

    private AssistantMessage assistantWithToolCall(String name, String arguments) {
        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", name);
        function.put("arguments", arguments);
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("id", "call-1");
        raw.put("type", "function");
        raw.put("function", function);
        List<Map> rawCalls = new ArrayList<Map>();
        rawCalls.add(raw);
        return new AssistantMessage("", false, rawCalls);
    }
}
