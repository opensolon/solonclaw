package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.agent.AgentRuntimeScope;

/** 上下文拼装服务接口。 */
public interface ContextService {
    /** 构建来源键对应的系统提示词。 */
    String buildSystemPrompt(String sourceKey);

    default String buildSystemPrompt(String sourceKey, AgentRuntimeScope agentScope) {
        return buildSystemPrompt(sourceKey);
    }
}
