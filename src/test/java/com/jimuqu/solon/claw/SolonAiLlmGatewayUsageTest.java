package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.llm.SolonAiLlmGateway;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.AiUsage;

/** 校验 Solon AI usage 到本地 token 桶的归一化。 */
public class SolonAiLlmGatewayUsageTest {
    @Test
    void shouldReadOpenaiChatCachedTokensFromPromptDetails() throws Exception {
        ONode source =
                ONode.ofJson(
                        "{"
                                + "\"prompt_tokens\":1000,"
                                + "\"completion_tokens\":50,"
                                + "\"total_tokens\":1050,"
                                + "\"prompt_tokens_details\":{\"cached_tokens\":200},"
                                + "\"completion_tokens_details\":{\"reasoning_tokens\":7}"
                                + "}");

        LlmResult result = collect(new AiUsage(1000L, 0L, 50L, 1050L, source));

        assertThat(result.getInputTokens()).isEqualTo(800L);
        assertThat(result.getOutputTokens()).isEqualTo(50L);
        assertThat(result.getCacheReadTokens()).isEqualTo(200L);
        assertThat(result.getCacheWriteTokens()).isEqualTo(0L);
        assertThat(result.getReasoningTokens()).isEqualTo(7L);
        assertThat(result.getTotalTokens()).isEqualTo(1050L);
    }

    @Test
    void shouldReadOpenaiResponsesCachedTokensFromInputDetails() throws Exception {
        ONode source =
                ONode.ofJson(
                        "{"
                                + "\"input_tokens\":1000,"
                                + "\"output_tokens\":50,"
                                + "\"total_tokens\":1050,"
                                + "\"input_tokens_details\":{\"cached_tokens\":200,\"cache_creation_tokens\":100},"
                                + "\"output_tokens_details\":{\"reasoning_tokens\":9}"
                                + "}");

        LlmResult result = collect(new AiUsage(1000L, 0L, 50L, 1050L, source));

        assertThat(result.getInputTokens()).isEqualTo(700L);
        assertThat(result.getOutputTokens()).isEqualTo(50L);
        assertThat(result.getCacheReadTokens()).isEqualTo(200L);
        assertThat(result.getCacheWriteTokens()).isEqualTo(100L);
        assertThat(result.getReasoningTokens()).isEqualTo(9L);
        assertThat(result.getTotalTokens()).isEqualTo(1050L);
    }

    @Test
    void shouldKeepAnthropicInputSeparateFromPromptCache() throws Exception {
        ONode source =
                ONode.ofJson(
                        "{"
                                + "\"input_tokens\":1000,"
                                + "\"output_tokens\":50,"
                                + "\"cache_read_input_tokens\":200,"
                                + "\"cache_creation_input_tokens\":100"
                                + "}");

        LlmResult result = collect(new AiUsage(1000L, 0L, 50L, 1050L, 100L, 200L, source));

        assertThat(result.getInputTokens()).isEqualTo(1000L);
        assertThat(result.getOutputTokens()).isEqualTo(50L);
        assertThat(result.getCacheReadTokens()).isEqualTo(200L);
        assertThat(result.getCacheWriteTokens()).isEqualTo(100L);
        assertThat(result.getTotalTokens()).isEqualTo(1350L);
    }

    private LlmResult collect(AiUsage usage) throws Exception {
        Class<?> collectorClass = usageCollectorClass();
        Constructor<?> constructor = collectorClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object collector = constructor.newInstance();

        Method add = collectorClass.getDeclaredMethod("add", AiUsage.class);
        add.setAccessible(true);
        add.invoke(collector, usage);

        LlmResult result = new LlmResult();
        Method applyTo = collectorClass.getDeclaredMethod("applyTo", LlmResult.class);
        applyTo.setAccessible(true);
        applyTo.invoke(collector, result);
        return result;
    }

    private Class<?> usageCollectorClass() {
        for (Class<?> nested : SolonAiLlmGateway.class.getDeclaredClasses()) {
            if ("UsageCollector".equals(nested.getSimpleName())) {
                return nested;
            }
        }
        throw new IllegalStateException("UsageCollector not found");
    }
}
