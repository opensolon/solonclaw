package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.llm.SolonAiLlmGateway;
import com.jimuqu.solon.claw.llm.dialect.RawResponseLoggingChatDialect;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatModel;

/** 校验 LLM provider 配置的前置失败逻辑。 */
public class SolonAiLlmGatewayConfigTest {
    @Test
    void shouldFailFastForUnsupportedProvider() {
        AppConfig config = new AppConfig();
        config.getLlm().setProvider("unknown-provider");
        config.getLlm().setApiUrl("https://example.com/v1/chat/completions");
        config.getLlm().setModel("test-model");

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-1");

        assertThatThrownBy(() -> gateway.chat(session, "system", "hello", Collections.emptyList()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持的 provider");
    }

    @Test
    void shouldRequireResponsesEndpointForOpenaiResponsesProvider() {
        AppConfig config = new AppConfig();
        config.getLlm().setProvider("openai-responses");
        config.getLlm().setApiUrl("https://example.com/v1/chat/completions");
        config.getLlm().setModel("gpt-5.4");

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-2");

        assertThatThrownBy(() -> gateway.chat(session, "system", "hello", Collections.emptyList()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/responses");
    }

    @Test
    void shouldUseRawResponseLoggingDialectForOpenaiResponsesProvider() throws Exception {
        AppConfig config = new AppConfig();
        config.getLlm().setProvider("openai-responses");
        config.getLlm().setApiUrl("https://example.com/v1/responses");
        config.getLlm().setModel("gpt-5.4");

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        Method buildChatModel =
                SolonAiLlmGateway.class.getDeclaredMethod(
                        "buildChatModel", AppConfig.LlmConfig.class);
        buildChatModel.setAccessible(true);

        ChatModel chatModel = (ChatModel) buildChatModel.invoke(gateway, config.getLlm());

        assertThat(chatModel.getDialect()).isInstanceOf(RawResponseLoggingChatDialect.class);
        assertThat(((RawResponseLoggingChatDialect) chatModel.getDialect()).getDialectName())
                .isEqualTo("openai-responses");
    }

    @Test
    void shouldUseRawResponseLoggingDialectForOpenaiProvider() throws Exception {
        AppConfig config = new AppConfig();
        config.getLlm().setProvider("openai");
        config.getLlm().setDialect("openai");
        config.getLlm().setApiUrl("https://example.com/v1/chat/completions");
        config.getLlm().setModel("gpt-5.4");

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        Method buildChatModel =
                SolonAiLlmGateway.class.getDeclaredMethod(
                        "buildChatModel", AppConfig.LlmConfig.class);
        buildChatModel.setAccessible(true);

        ChatModel chatModel = (ChatModel) buildChatModel.invoke(gateway, config.getLlm());

        assertThat(chatModel.getDialect()).isInstanceOf(RawResponseLoggingChatDialect.class);
        assertThat(((RawResponseLoggingChatDialect) chatModel.getDialect()).getDialectName())
                .isEqualTo("openai");
    }

    @Test
    void shouldUseRawResponseLoggingDialectForAllSupportedProviders() throws Exception {
        assertLoggingDialect("ollama", "http://localhost:11434/api/chat", "llama3");
        assertLoggingDialect("gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-pro");
        assertLoggingDialect("anthropic", "https://api.anthropic.com/v1/messages", "claude-sonnet");
    }

    private void assertLoggingDialect(String provider, String apiUrl, String model)
            throws Exception {
        AppConfig config = new AppConfig();
        config.getLlm().setProvider(provider);
        config.getLlm().setDialect(provider);
        config.getLlm().setApiUrl(apiUrl);
        config.getLlm().setModel(model);

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        Method buildChatModel =
                SolonAiLlmGateway.class.getDeclaredMethod(
                        "buildChatModel", AppConfig.LlmConfig.class);
        buildChatModel.setAccessible(true);

        ChatModel chatModel = (ChatModel) buildChatModel.invoke(gateway, config.getLlm());

        assertThat(chatModel.getDialect()).isInstanceOf(RawResponseLoggingChatDialect.class);
        assertThat(((RawResponseLoggingChatDialect) chatModel.getDialect()).getDialectName())
                .isEqualTo(provider);
    }
}
