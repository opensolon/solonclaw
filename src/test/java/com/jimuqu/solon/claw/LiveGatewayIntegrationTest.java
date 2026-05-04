package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.ChatMessage;

public class LiveGatewayIntegrationTest {
    @Test
    void shouldRunGatewayFlowAgainstRealResponsesModel() throws Exception {
        Assumptions.assumeTrue(
                "true"
                        .equalsIgnoreCase(
                                TestEnvironment.runtimeConfigValue(
                                        "solonclaw.tests.liveAi.enabled")));
        Assumptions.assumeTrue(
                StrUtil.isNotBlank(TestEnvironment.runtimeConfigValue("providers.default.apiKey")));

        TestEnvironment env = TestEnvironment.withLiveLlm();
        GatewayReply claimPrompt = env.send("live-room", "tester", "hello");
        assertThat(claimPrompt.getContent()).contains("/pairing claim-admin");
        GatewayReply claimReply = env.send("live-room", "tester", "/pairing claim-admin");
        assertThat(claimReply.getContent()).contains("唯一管理员");
        GatewayMessage source = env.message("live-room", "tester", "请用一句话介绍你自己。");

        GatewayReply first = env.gatewayService.handle(source);
        assertThat(first.getContent()).isNotBlank();
        assertThat(env.memoryChannelAdapter.getLastRequest().getText())
                .isEqualTo(first.getContent());

        GatewayReply branch =
                env.gatewayService.handle(env.message("live-room", "tester", "/branch live"));
        assertThat(branch.getBranchName()).isEqualTo("live");

        GatewayReply toolReply =
                env.gatewayService.handle(
                        new GatewayMessage(
                                PlatformType.MEMORY,
                                "live-room",
                                "tester",
                                "你必须调用 todo 工具写入 todos=[{id:\"live-1\",content:\"集成测试\",status:\"in_progress\"}]；然后再次调用 todo 工具读取当前列表，并告诉我结果。"));
        assertThat(toolReply.getContent()).isNotBlank();

        SessionRecord current = env.sessionRepository.getBoundSession("MEMORY:live-room:tester");
        List<ChatMessage> messages = MessageSupport.loadMessages(current.getNdjson());
        boolean hasToolMessage = false;
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatRole.TOOL) {
                hasToolMessage = true;
                break;
            }
        }
        assertThat(hasToolMessage).isTrue();

        GatewayReply cronCreate =
                env.gatewayService.handle(
                        new GatewayMessage(
                                PlatformType.MEMORY,
                                "live-room",
                                "tester",
                                "/cron create livejob|*/5 * * * *|请只回复：live cron ok"));
        Matcher matcher = Pattern.compile("([a-f0-9]{32})").matcher(cronCreate.getContent());
        assertThat(matcher.find()).isTrue();
        String jobId = matcher.group(1);

        GatewayReply cronRun =
                env.gatewayService.handle(
                        new GatewayMessage(
                                PlatformType.MEMORY, "live-room", "tester", "/cron run " + jobId));
        assertThat(cronRun.getContent()).contains("已执行定时任务");

        GatewayReply retry =
                env.gatewayService.handle(
                        new GatewayMessage(PlatformType.MEMORY, "live-room", "tester", "/retry"));
        assertThat(retry.getContent()).isNotBlank();
    }
}
