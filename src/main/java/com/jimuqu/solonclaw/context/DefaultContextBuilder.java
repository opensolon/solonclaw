package com.jimuqu.solonclaw.context;

import com.jimuqu.solonclaw.context.components.*;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 默认上下文构建器实现
 * <p>
 * 整合各个上下文组件，构建完整的对话上下文
 *
 * @author SolonClaw
 */
@Component
public class DefaultContextBuilder implements ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextBuilder.class);

    @Inject
    private KnowledgeContext knowledgeContext;

    @Inject
    private SystemContext systemContext;

    @Inject
    private SessionContext sessionContext;

    @Inject
    private ToolContext toolContext;

    @Inject
    private com.jimuqu.solonclaw.context.config.ContextBuilderConfig config;

    @Init
    public void init() {
        log.info("默认上下文构建器初始化完成: config={}", config);
    }

    @Override
    public Context build(String sessionId, String userMessage, Map<String, Object> options) {
        log.debug("开始构建上下文: sessionId={}, messageLength={}", sessionId,
            userMessage != null ? userMessage.length() : 0);

        try {
            // 构建系统上下文
            String system = buildSystemContext(sessionId, userMessage, options);

            // 构建工具上下文
            String tools = buildToolContext(sessionId, userMessage, options);

            // 构建知识上下文
            String knowledge = buildKnowledgeContext(sessionId, userMessage, options);

            // 构建会话上下文
            String session = buildSessionContext(sessionId, userMessage, options);

            // 创建 Context 对象
            Context context = new Context(knowledge, system, session, tools);

            // 添加元数据
            context.putMetadata("sessionId", sessionId);
            context.putMetadata("userMessageLength", userMessage != null ? userMessage.length() : 0);
            context.putMetadata("buildTimestamp", System.currentTimeMillis());

            log.debug("上下文构建完成: sessionId={}, lengths=[system={}, tools={}, knowledge={}, session={}]",
                sessionId,
                system != null ? system.length() : 0,
                tools != null ? tools.length() : 0,
                knowledge != null ? knowledge.length() : 0,
                session != null ? session.length() : 0
            );

            return context;

        } catch (Exception e) {
            log.error("构建上下文失败: sessionId={}", sessionId, e);
            // 返回空上下文
            return Context.empty();
        }
    }

    /**
     * 构建系统上下文
     */
    private String buildSystemContext(String sessionId, String userMessage, Map<String, Object> options) {
        boolean systemEnabled = config != null && config.isSystemEnabled();
        if (!systemEnabled) {
            log.debug("系统上下文已禁用");
            return "";
        }

        if (systemContext == null) {
            log.debug("SystemContext 未注入");
            return "";
        }

        boolean includeTools = config != null && config.isIncludeToolsInSystem();
        if (includeTools) {
            // 将工具描述包含在系统上下文中
            return systemContext.buildWithTools(sessionId, userMessage, options);
        } else {
            return systemContext.build(sessionId, userMessage, options);
        }
    }

    /**
     * 构建工具上下文
     */
    private String buildToolContext(String sessionId, String userMessage, Map<String, Object> options) {
        boolean toolsEnabled = config != null && config.isToolsEnabled();
        if (!toolsEnabled) {
            log.debug("工具上下文已禁用");
            return "";
        }

        if (toolContext == null) {
            log.debug("ToolContext 未注入");
            return "";
        }

        boolean includeTools = config != null && config.isIncludeToolsInSystem();
        if (includeTools) {
            // 如果工具已包含在系统上下文中，这里返回空
            return "";
        }

        return toolContext.build(sessionId, userMessage, options);
    }

    /**
     * 构建知识上下文
     */
    private String buildKnowledgeContext(String sessionId, String userMessage, Map<String, Object> options) {
        boolean knowledgeEnabled = config != null && config.isKnowledgeEnabled();
        if (!knowledgeEnabled) {
            log.debug("知识上下文已禁用");
            return "";
        }

        if (knowledgeContext == null) {
            log.debug("KnowledgeContext 未注入");
            return "";
        }

        return knowledgeContext.build(sessionId, userMessage, options);
    }

    /**
     * 构建会话上下文
     */
    private String buildSessionContext(String sessionId, String userMessage, Map<String, Object> options) {
        boolean sessionEnabled = config != null && config.isSessionEnabled();
        if (!sessionEnabled) {
            log.debug("会话上下文已禁用");
            return "";
        }

        if (sessionContext == null) {
            log.debug("SessionContext 未注入");
            return "";
        }

        return sessionContext.build(sessionId, userMessage, options);
    }

    // Getters and Setters

    public com.jimuqu.solonclaw.context.config.ContextBuilderConfig getConfig() {
        return config;
    }

    public void setConfig(com.jimuqu.solonclaw.context.config.ContextBuilderConfig config) {
        this.config = config;
    }
}