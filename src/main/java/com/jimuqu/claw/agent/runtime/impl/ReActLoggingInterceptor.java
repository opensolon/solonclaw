package com.jimuqu.claw.agent.runtime.impl;

import cn.hutool.core.util.StrUtil;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 为 ReAct 推理过程输出后台监控日志。
 */
public class ReActLoggingInterceptor implements ReActInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReActLoggingInterceptor.class);
    private static final int MAX_LOG_TEXT_LENGTH = 500;

    @Override
    public void onAgentStart(ReActTrace trace) {
        log.info(
                "[AiAgent] 任务开始, agent={}, session={}, prompt={}",
                trace.getAgentName(),
                sessionId(trace),
                compact(trace.getOriginalPrompt() == null ? null : trace.getOriginalPrompt().getUserContent())
        );
    }

    @Override
    public void onThought(ReActTrace trace, String thought) {
        if (StrUtil.isBlank(thought)) {
            return;
        }

        log.info(
                "[AiAgent] 思考过程, agent={}, session={}, step={}, content={}",
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                compact(thought)
        );
    }

    @Override
    public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
        log.info(
                "[AiAgent] 调用工具, agent={}, session={}, step={}, tool={}, args={}",
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                toolName,
                compact(String.valueOf(args))
        );
    }

    @Override
    public void onObservation(ReActTrace trace, String toolName, String result, long durationMs) {
        log.info(
                "[AiAgent] 工具结果, agent={}, session={}, step={}, tool={}, durationMs={}, result={}",
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                toolName,
                durationMs,
                compact(result)
        );
    }

    @Override
    public void onAgentEnd(ReActTrace trace) {
        log.info(
                "[AiAgent] 任务结束, agent={}, session={}, steps={}, tools={}, durationMs={}, promptTokens={}, completionTokens={}, totalTokens={}, finalAnswer={}",
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                trace.getToolCallCount(),
                trace.getMetrics().getTotalDuration(),
                trace.getMetrics().getPromptTokens(),
                trace.getMetrics().getCompletionTokens(),
                trace.getMetrics().getTotalTokens(),
                compact(trace.getFinalAnswer())
        );

        if (log.isDebugEnabled()) {
            log.debug(
                    "[AiAgent] 格式化历史, agent={}, session={}\n{}",
                    trace.getAgentName(),
                    sessionId(trace),
                    trace.getFormattedHistory()
            );
        }
    }

    private String sessionId(ReActTrace trace) {
        return trace.getSession() == null ? "未知会话" : trace.getSession().getSessionId();
    }

    private String compact(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }

        String normalized = text.replace("\r", " ").replace("\n", "\\n").trim();
        return StrUtil.maxLength(normalized, MAX_LOG_TEXT_LENGTH);
    }
}
