package com.jimuqu.solonclaw.agent;

import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Configuration;

/**
 * Agent 配置
 * <p>
 * 从配置文件读取 Agent 相关参数
 *
 * @author SolonClaw
 */
@Configuration
public class AgentConfig {

    @Inject("${nullclaw.agent.model.primary}")
    private String primaryModel;

    @Inject("${nullclaw.agent.maxHistoryMessages}")
    private int maxHistoryMessages;

    @Inject("${nullclaw.agent.maxToolIterations}")
    private int maxToolIterations;

    public String getPrimaryModel() {
        return primaryModel;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public int getMaxToolIterations() {
        return maxToolIterations;
    }
}
