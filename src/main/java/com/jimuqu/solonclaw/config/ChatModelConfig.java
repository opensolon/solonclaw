package com.jimuqu.solonclaw.config;

import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChatModel 配置类
 * <p>
 * 使用 Solon AI 的 ChatModel 替代手动调用 OpenAI API
 *
 * @author SolonClaw
 */
@Configuration
public class ChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatModelConfig.class);

    /**
     * 配置 ChatConfig Bean
     */
    @Bean
    public ChatConfig chatConfig(
        @Inject("${solon.ai.chat.openai.apiUrl}") String apiUrl,
        @Inject("${solon.ai.chat.openai.apiKey}") String apiKey,
        @Inject("${solon.ai.chat.openai.model}") String model,
        @Inject("${solon.ai.chat.openai.provider:openai}") String provider
    ) {
        log.info("开始配置 ChatConfig...");
        log.info("API URL: {}", apiUrl);
        log.info("Model: {}", model);
        log.info("Provider: {}", provider);

        ChatConfig config = new ChatConfig();
        config.setApiUrl(apiUrl);
        config.setApiKey(apiKey);
        config.setModel(model);
        config.setProvider(provider);

        log.info("ChatConfig 配置完成");
        return config;
    }

    /**
     * 配置 ChatModel Bean
     */
    @Bean
    public ChatModel chatModel(ChatConfig chatConfig) {
        log.info("开始构建 ChatModel...");
        ChatModel chatModel = ChatModel.of(chatConfig).build();
        log.info("ChatModel 构建完成");
        return chatModel;
    }
}
