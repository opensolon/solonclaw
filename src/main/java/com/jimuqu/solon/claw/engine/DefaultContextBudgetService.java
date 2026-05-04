package com.jimuqu.solon.claw.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ContextBudgetDecision;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.service.ContextBudgetService;

/** 默认上下文预算估算服务。 */
public class DefaultContextBudgetService implements ContextBudgetService {
    private final AppConfig appConfig;

    public DefaultContextBudgetService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public ContextBudgetDecision decide(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            AppConfig.LlmConfig resolved) {
        int contextWindow =
                resolved == null || resolved.getContextWindowTokens() <= 0
                        ? Math.max(1024, appConfig.getLlm().getContextWindowTokens())
                        : Math.max(1024, resolved.getContextWindowTokens());
        int threshold = (int) (contextWindow * appConfig.getCompression().getThresholdPercent());
        int estimated =
                estimate(systemPrompt)
                        + estimate(userMessage)
                        + estimate(session == null ? null : session.getNdjson());

        ContextBudgetDecision decision = new ContextBudgetDecision();
        decision.setContextWindowTokens(contextWindow);
        decision.setThresholdTokens(threshold);
        decision.setEstimatedTokens(estimated);
        decision.setShouldCompress(
                appConfig.getCompression().isEnabled() && estimated >= threshold);
        decision.setReason(decision.isShouldCompress() ? "预计上下文已达到压缩阈值" : "预计上下文未达到压缩阈值");
        return decision;
    }

    private int estimate(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        int cjk = 0;
        int ascii = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch <= 127) {
                ascii++;
            } else {
                cjk++;
            }
        }
        return Math.max(1, cjk + Math.max(1, ascii / 4));
    }
}
