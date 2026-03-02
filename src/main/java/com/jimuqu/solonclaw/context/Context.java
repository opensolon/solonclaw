package com.jimuqu.solonclaw.context;

import java.util.HashMap;
import java.util.Map;

/**
 * 上下文数据模型
 * <p>
 * 封装构建 AI 对话所需的各种上下文信息
 *
 * @author SolonClaw
 */
public class Context {

    /**
     * 知识上下文 - 从知识库检索的相关经验
     */
    private final String knowledge;

    /**
     * 系统上下文 - 系统提示词、配置信息
     */
    private final String system;

    /**
     * 会话上下文 - 历史对话记录摘要
     */
    private final String session;

    /**
     * 工具上下文 - 可用工具列表和描述
     */
    private final String tools;

    /**
     * 元数据 - 额外的上下文信息
     */
    private final Map<String, Object> metadata;

    public Context(String knowledge, String system, String session, String tools) {
        this.knowledge = knowledge;
        this.system = system;
        this.session = session;
        this.tools = tools;
        this.metadata = new HashMap<>();
    }

    public Context(String knowledge, String system, String session, String tools, Map<String, Object> metadata) {
        this.knowledge = knowledge;
        this.system = system;
        this.session = session;
        this.tools = tools;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * 构建完整的提示词
     * <p>
     * 将所有上下文组件整合成发送给 AI 的完整提示词
     *
     * @param userMessage 用户消息
     * @return 完整的提示词
     */
    public String buildPrompt(String userMessage) {
        StringBuilder prompt = new StringBuilder();

        // 添加系统上下文
        if (system != null && !system.isEmpty()) {
            prompt.append(system).append("\n\n");
        }

        // 添加工具上下文
        if (tools != null && !tools.isEmpty()) {
            prompt.append(tools).append("\n\n");
        }

        // 添加知识上下文
        if (knowledge != null && !knowledge.isEmpty()) {
            prompt.append("## 参考知识\n\n");
            prompt.append(knowledge).append("\n\n");
        }

        // 添加会话上下文（历史摘要）
        if (session != null && !session.isEmpty()) {
            prompt.append("## 对话历史\n\n");
            prompt.append(session).append("\n\n");
        }

        // 添加用户消息
        prompt.append("## 当前问题\n\n");
        prompt.append(userMessage);

        return prompt.toString();
    }

    // Getters

    public String getKnowledge() {
        return knowledge;
    }

    public String getSystem() {
        return system;
    }

    public String getSession() {
        return session;
    }

    public String getTools() {
        return tools;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * 添加元数据
     *
     * @param key   键
     * @param value 值
     * @return Context 实例（支持链式调用）
     */
    public Context putMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 获取元数据
     *
     * @param key 键
     * @param <T>  值类型
     * @return 值，如果不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    /**
     * 创建空的上下文
     *
     * @return 空 Context 实例
     */
    public static Context empty() {
        return new Context(null, null, null, null);
    }

    /**
     * 创建构建器
     *
     * @return ContextBuilder 实例
     */
    public static ContextBuilder builder() {
        return new ContextBuilder();
    }

    /**
     * 上下文构建器
     */
    public static class ContextBuilder {
        private String knowledge;
        private String system;
        private String session;
        private String tools;
        private final Map<String, Object> metadata = new HashMap<>();

        public ContextBuilder knowledge(String knowledge) {
            this.knowledge = knowledge;
            return this;
        }

        public ContextBuilder system(String system) {
            this.system = system;
            return this;
        }

        public ContextBuilder session(String session) {
            this.session = session;
            return this;
        }

        public ContextBuilder tools(String tools) {
            this.tools = tools;
            return this;
        }

        public ContextBuilder putMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Context build() {
            return new Context(knowledge, system, session, tools, metadata);
        }
    }
}
