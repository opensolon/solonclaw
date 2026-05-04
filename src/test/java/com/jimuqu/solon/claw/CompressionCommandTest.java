package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.support.constants.CompressionConstants;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

public class CompressionCommandTest {
    @Test
    void shouldCompressCurrentSessionWhenSlashCommandCalled() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
        env.send("admin-chat", "admin-user", "start");

        SessionRecord session =
                env.sessionRepository.getBoundSession("MEMORY:admin-chat:admin-user");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofSystem("system"),
                                ChatMessage.ofUser("目标：完成一个复杂任务"),
                                ChatMessage.ofAssistant("步骤一已经完成，修改了多个文件并分析了错误。"),
                                ChatMessage.ofTool("tool output " + repeat("A", 600), "tool", "1"),
                                ChatMessage.ofUser("继续下一步"),
                                ChatMessage.ofAssistant("继续处理并准备输出最终结果。"),
                                ChatMessage.ofUser("最后确认一下"))));
        env.sessionRepository.save(session);

        GatewayReply reply =
                env.gatewayService.handle(env.message("admin-chat", "admin-user", "/compress"));
        assertThat(reply.getContent()).contains("上下文压缩");

        SessionRecord updated = env.sessionRepository.findById(session.getSessionId());
        assertThat(updated.getCompressedSummary()).contains(CompressionConstants.SUMMARY_PREFIX);
        assertThat(updated.getNdjson()).contains(CompressionConstants.SUMMARY_PREFIX);
    }

    @Test
    void shouldIncludeManualFocusWhenCompressingCurrentSession() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
        env.send("admin-chat", "admin-user", "start");

        SessionRecord session =
                env.sessionRepository.getBoundSession("MEMORY:admin-chat:admin-user");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("分析当前问题"),
                                ChatMessage.ofAssistant("已经处理第一部分"),
                                ChatMessage.ofTool("tool output " + repeat("C", 500), "tool", "1"),
                                ChatMessage.ofUser("继续推进"),
                                ChatMessage.ofAssistant("准备发布"),
                                ChatMessage.ofUser(repeat("B", 5000)))));
        env.sessionRepository.save(session);

        GatewayReply reply =
                env.gatewayService.handle(
                        env.message("admin-chat", "admin-user", "/compress 发布流程"));
        SessionRecord updated = env.sessionRepository.findById(session.getSessionId());

        assertThat(reply.getContent()).contains("关注主题");
        assertThat(updated.getCompressedSummary()).contains("Focus");
        assertThat(updated.getCompressedSummary()).contains("发布流程");
    }

    private String repeat(String value, int count) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(value);
        }
        return buffer.toString();
    }
}
