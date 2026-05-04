package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class GatewayCommandFlowTest {
    @Test
    void shouldHandleBasicCommandsAndConversationFlow() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        GatewayReply claimPrompt = env.send("room-1", "user-1", "hello");
        assertThat(claimPrompt.getContent()).contains("/pairing claim-admin");
        GatewayReply claimReply = env.send("room-1", "user-1", "/pairing claim-admin");
        assertThat(claimReply.getContent()).contains("唯一管理员");

        GatewayReply firstReply =
                env.gatewayService.handle(env.message("room-1", "user-1", "hello"));
        assertThat(firstReply.getContent()).contains("echo:hello");
        String firstSessionId = firstReply.getSessionId();

        GatewayReply statusReply =
                env.gatewayService.handle(env.message("room-1", "user-1", "/status"));
        assertThat(statusReply.getContent()).contains(firstSessionId);

        GatewayReply retryReply =
                env.gatewayService.handle(env.message("room-1", "user-1", "/retry"));
        assertThat(retryReply.getContent()).contains("echo:hello");

        GatewayReply branchReply =
                env.gatewayService.handle(env.message("room-1", "user-1", "/branch review"));
        assertThat(branchReply.getContent()).contains("review");

        GatewayReply undoReply =
                env.gatewayService.handle(env.message("room-1", "user-1", "/undo"));
        assertThat(undoReply.getContent()).contains("已从会话中移除上一轮对话");

        GatewayReply newReply = env.gatewayService.handle(env.message("room-1", "user-1", "/new"));
        assertThat(newReply.getSessionId()).isNotEqualTo(firstSessionId);

        SessionRecord rebound = env.sessionRepository.getBoundSession("MEMORY:room-1:user-1");
        assertThat(rebound.getSessionId()).isEqualTo(newReply.getSessionId());
    }

    @Test
    void shouldRenderHelpWithChineseDescriptionsPerLine() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        GatewayReply claimPrompt = env.send("room-help", "user-help", "hello");
        assertThat(claimPrompt.getContent()).contains("/pairing claim-admin");
        GatewayReply claimReply = env.send("room-help", "user-help", "/pairing claim-admin");
        assertThat(claimReply.getContent()).contains("唯一管理员");

        GatewayReply helpReply = env.send("room-help", "user-help", "/help");
        assertThat(helpReply.getContent()).contains("/new - 创建并切换到新会话");
        assertThat(helpReply.getContent()).contains("/help - 显示帮助信息");
        assertThat(Arrays.asList(helpReply.getContent().split("\\R")))
                .isNotEmpty()
                .allMatch(line -> line.startsWith("/") && line.contains(" - "));
    }
}
