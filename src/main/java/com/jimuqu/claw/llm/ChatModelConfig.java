package com.jimuqu.claw.llm;

import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;

/**
 * 负责从配置中构建默认聊天模型 Bean。
 */
@Configuration
public class ChatModelConfig {
    /**
     * 基于配置构建聊天模型实例。
     *
     * @param config 默认聊天配置
     * @return 聊天模型实例
     */
    @Bean
    public ChatModel build(@Inject("${solon.ai.chat.default}") ChatConfig config) {
        return ChatModel.of(config).build();
    }
}
