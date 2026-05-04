package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.web.DashboardSessionService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

public class DashboardSessionServiceTest {
    @Test
    void shouldExposeAssistantReasoningAndCompressionMetadata() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:dash:user");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("问题"),
                                ChatMessage.ofAssistant("<think>先分析路径</think>\n最终答复"))));
        session.setCompressedSummary("Summary\n已压缩");
        session.setLastCompressionAt(123L);
        session.setLastCompressionInputTokens(456);
        env.sessionRepository.save(session);

        DashboardSessionService service = new DashboardSessionService(env.sessionRepository);
        Map<String, Object> detail = service.getSessionMessages(session.getSessionId());

        assertThat(detail.get("compressed_summary")).isEqualTo("Summary\n已压缩");
        assertThat(detail.get("last_compression_at")).isEqualTo(123L);
        assertThat(detail.get("last_compression_input_tokens")).isEqualTo(456);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) detail.get("messages");
        Map<String, Object> assistant = messages.get(1);
        assertThat(assistant.get("content")).isEqualTo("最终答复");
        assertThat(assistant.get("reasoning")).isEqualTo("先分析路径");
    }

    @Test
    void shouldBuildSessionTreeFromParentLinksAcrossSourceKeys() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord root = env.sessionRepository.bindNewSession("MEMORY:lineage:root");
        root.setTitle("root");
        env.sessionRepository.save(root);

        SessionRecord child =
                env.sessionRepository.cloneSession(
                        "MEMORY:lineage:child", root.getSessionId(), "child");
        child.setTitle("child");
        env.sessionRepository.save(child);

        SessionRecord grandchild =
                env.sessionRepository.cloneSession(
                        "MEMORY:lineage:grandchild", child.getSessionId(), "grandchild");
        grandchild.setTitle("grandchild");
        env.sessionRepository.save(grandchild);

        DashboardSessionService service = new DashboardSessionService(env.sessionRepository);
        Map<String, Object> tree = service.sessionTree(child.getSessionId());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) tree.get("nodes");
        assertThat(nodes)
                .extracting(node -> node.get("id"))
                .contains(root.getSessionId(), child.getSessionId(), grandchild.getSessionId());
    }
}
