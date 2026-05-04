package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import java.util.List;

/** 工具注册与启停控制接口。 */
public interface ToolRegistry {
    /** 列出全部可见工具名。 */
    List<String> listToolNames();

    /** 解析某个来源键当前启用的工具对象。 */
    List<Object> resolveEnabledTools(String sourceKey);

    default List<Object> resolveEnabledTools(String sourceKey, AgentRuntimeScope agentScope) {
        return resolveEnabledTools(sourceKey);
    }

    /** 列出某个来源键当前启用的工具名。 */
    List<String> resolveEnabledToolNames(String sourceKey);

    default List<String> resolveEnabledToolNames(String sourceKey, AgentRuntimeScope agentScope) {
        return resolveEnabledToolNames(sourceKey);
    }

    /** 启用指定工具。 */
    void enableTools(String sourceKey, List<String> toolNames);

    /** 禁用指定工具。 */
    void disableTools(String sourceKey, List<String> toolNames);
}
