package com.jimuqu.solon.claw.support;

import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;

/** 对话编排器引用持有器，用于解决 Bean 初始化阶段的循环依赖。 */
public class ConversationOrchestratorHolder {
    /** 当前编排器引用。 */
    private volatile ConversationOrchestrator conversationOrchestrator;

    /** 获取当前编排器。 */
    public ConversationOrchestrator get() {
        return conversationOrchestrator;
    }

    /** 设置当前编排器。 */
    public void set(ConversationOrchestrator conversationOrchestrator) {
        this.conversationOrchestrator = conversationOrchestrator;
    }
}
