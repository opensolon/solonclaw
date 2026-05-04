package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.CompressionOutcome;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.engine.DefaultContextCompressionService;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.constants.CompressionConstants;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.message.ChatMessage;

/** 校验上下文压缩的反抖动、失败冷却与摘要合并行为。 */
public class CompressionStabilityTest {
    @Test
    void shouldSkipRecompressionWhenRecentCompressionDidNotGainEnoughNewContext() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-1");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser(repeat("A", 3200)),
                                ChatMessage.ofAssistant(repeat("B", 3200)))));
        session.setLastCompressionAt(System.currentTimeMillis());
        session.setLastCompressionInputTokens(1500);

        SessionRecord compressed = service.compressIfNeeded(session, "system", "next");

        assertThat(compressed.getCompressedSummary()).isNull();
        assertThat(compressed.getLastCompressionAt()).isEqualTo(session.getLastCompressionAt());
    }

    @Test
    void shouldSkipCompressionDuringFailureCooldown() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-2");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser(repeat("A", 3200)),
                                ChatMessage.ofAssistant(repeat("B", 3200)))));
        session.setLastCompressionFailedAt(System.currentTimeMillis());

        SessionRecord compressed = service.compressIfNeeded(session, "system", "next");

        assertThat(compressed.getCompressedSummary()).isNull();
        assertThat(compressed.getLastCompressionInputTokens()).isZero();
    }

    @Test
    void shouldMergePreviousSummaryWhenCompressingAgain() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-3");
        session.setCompressedSummary(CompressionConstants.SUMMARY_PREFIX + "\n旧摘要内容");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofSystem("system"),
                                ChatMessage.ofUser("目标：完成同步"),
                                ChatMessage.ofAssistant(
                                        CompressionConstants.SUMMARY_PREFIX + "\n旧摘要内容"),
                                ChatMessage.ofAssistant("已经完成第一步并修改多个文件。"),
                                ChatMessage.ofTool("tool output " + repeat("C", 500), "tool", "1"),
                                ChatMessage.ofUser("继续推进"),
                                ChatMessage.ofAssistant("继续处理中"),
                                ChatMessage.ofUser("收尾"))));

        SessionRecord compressed = service.compressNow(session, "system prompt");

        assertThat(compressed.getCompressedSummary()).contains("Previous Summary");
        assertThat(compressed.getCompressedSummary()).contains("旧摘要内容");
    }

    @Test
    void shouldFlattenNestedPreviousSummaryAndCapSummaryLength() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-4");
        session.setCompressedSummary(
                CompressionConstants.SUMMARY_PREFIX
                        + "\nPrevious Summary\n更早摘要\n\nGoal\n第一次目标\n\nProgress\n"
                        + repeat("历史进展", 120));
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofSystem("system"),
                                ChatMessage.ofUser("目标：持续跟进 code review"),
                                ChatMessage.ofAssistant(session.getCompressedSummary()),
                                ChatMessage.ofAssistant(repeat("中间分析", 1000)),
                                ChatMessage.ofUser("继续推进"),
                                ChatMessage.ofAssistant(repeat("最新进展", 800)),
                                ChatMessage.ofUser("收尾"))));

        SessionRecord compressed = service.compressNow(session, "system prompt");

        assertThat(countOccurrences(compressed.getCompressedSummary(), "Previous Summary"))
                .isEqualTo(1);
        assertThat(compressed.getCompressedSummary()).contains("\nGoal\n");
        assertThat(compressed.getCompressedSummary()).contains("\nProgress\n");
        assertThat(compressed.getCompressedSummary().length())
                .isLessThanOrEqualTo(
                        CompressionConstants.SUMMARY_PREFIX.length()
                                + 1
                                + CompressionConstants.MAX_SUMMARY_LENGTH
                                + 3);
    }

    @Test
    void shouldPreferLatestGoalAndDropOldHeadAfterSummaryExists() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-4b");
        session.setCompressedSummary(
                CompressionConstants.SUMMARY_PREFIX + "\nGoal\n老任务\n\nProgress\n旧进展");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("你检测一下是否能找到git之类的工具"),
                                ChatMessage.ofAssistant("git/python/node 都在"),
                                ChatMessage.ofAssistant(session.getCompressedSummary()),
                                ChatMessage.ofAssistant("刚刚检查了 code review 目录"),
                                ChatMessage.ofUser("进度怎么样了"))));

        SessionRecord compressed = service.compressNow(session, "system prompt");

        assertThat(compressed.getNdjson()).doesNotContain("你检测一下是否能找到git之类的工具");
        assertThat(compressed.getCompressedSummary()).contains("进度怎么样了");
        assertThat(compressed.getCompressedSummary()).contains("Previous Summary\nGoal\n老任务");
    }

    @Test
    void shouldForceCompressionWhenRecentRealInputAlreadyExceededThreshold() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-5");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("先做一次检查"),
                                ChatMessage.ofAssistant(repeat("A", 200)),
                                ChatMessage.ofUser("继续"))));
        session.setLastInputTokens(1600);
        session.setLastUsageAt(System.currentTimeMillis());
        session.setLastCompressionAt(System.currentTimeMillis() - 120_000L);

        SessionRecord compressed = service.compressIfNeeded(session, "system", "下一轮继续");

        assertThat(compressed.getCompressedSummary()).contains(CompressionConstants.SUMMARY_PREFIX);
        assertThat(compressed.getLastCompressionInputTokens()).isGreaterThanOrEqualTo(1600);
    }

    @Test
    void shouldReturnWarningOutcomeWhenCompressionFails() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-fail");
        session.setNdjson("{not-valid-json");

        CompressionOutcome outcome = service.compressNowWithOutcome(session, "system", "focus");

        assertThat(outcome.isFailed()).isTrue();
        assertThat(outcome.getWarning()).contains("压缩摘要生成失败");
        assertThat(outcome.getSession()).isSameAs(session);
        assertThat(session.getCompressionFailureCount()).isEqualTo(1);
    }

    @Test
    void shouldAlwaysProtectLatestUserMessage() throws Exception {
        AppConfig config = config();
        config.getCompression().setProtectHeadMessages(1);
        config.getCompression().setTailRatio(0.01D);
        DefaultContextCompressionService service = new DefaultContextCompressionService(config);
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-last-user");
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofSystem("system"),
                                ChatMessage.ofUser("old goal"),
                                ChatMessage.ofAssistant(repeat("middle", 500)),
                                ChatMessage.ofUser("必须保留的最后用户消息"),
                                ChatMessage.ofAssistant("处理中"))));

        SessionRecord compressed = service.compressNow(session, "system");

        assertThat(compressed.getNdjson()).contains("必须保留的最后用户消息");
    }

    @Test
    void shouldShrinkToolArgumentsWithoutBreakingJson() throws Exception {
        DefaultContextCompressionService service = new DefaultContextCompressionService(config());
        Method method =
                DefaultContextCompressionService.class.getDeclaredMethod(
                        "shrinkToolArgumentsJson", String.class, int.class);
        method.setAccessible(true);

        String raw = new ONode().set("path", "demo.txt").set("content", repeat("x", 500)).toJson();
        String shrunk = (String) method.invoke(service, raw, 32);

        ONode parsed = ONode.ofJson(shrunk);
        assertThat(parsed.get("path").getString()).isEqualTo("demo.txt");
        assertThat(parsed.get("content").getString()).endsWith("...[truncated]");
    }

    private AppConfig config() {
        AppConfig config = new AppConfig();
        config.getCompression().setEnabled(true);
        config.getCompression().setThresholdPercent(0.5D);
        config.getCompression().setProtectHeadMessages(1);
        config.getCompression().setTailRatio(0.2D);
        config.getLlm().setContextWindowTokens(2000);
        return config;
    }

    private String repeat(String value, int count) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(value);
        }
        return buffer.toString();
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while (text != null && token != null && token.length() > 0) {
            int idx = text.indexOf(token, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + token.length();
        }
        return count;
    }
}
