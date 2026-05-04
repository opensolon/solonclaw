package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ContextBudgetDecision;
import com.jimuqu.solon.claw.core.model.SessionRecord;

/** 模型调用前的上下文预算服务。 */
public interface ContextBudgetService {
    ContextBudgetDecision decide(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            AppConfig.LlmConfig resolved);
}
