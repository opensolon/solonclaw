package com.jimuqu.claw.agent.runtime.impl;

import cn.hutool.core.util.StrUtil;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.chat.ChatRequestDesc;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 为 ReAct 推理过程输出后台监控日志。
 */
public class ReActLoggingInterceptor implements ReActInterceptor {
    private static final String SUB_AGENT_MARKER = ":subtask:";
    private static final String TASK_TITLE_KEY = "taskTitle";
    private static final Logger log = LoggerFactory.getLogger(ReActLoggingInterceptor.class);
    private static final int MAX_LOG_TEXT_LENGTH = 500;

    @Override
    public void onAgentStart(ReActTrace trace) {
        log.info(
                "[{}] 任务开始, agent={}, session={}, prompt={}",
                roleLabel(trace),
                trace.getAgentName(),
                sessionId(trace),
                compact(trace.getOriginalPrompt() == null ? null : trace.getOriginalPrompt().getUserContent())
        );
    }

    @Override
    public void onModelStart(ReActTrace trace, ChatRequestDesc req) {
        log.info(
                "[{}] 模型请求, agent={}, session={}, step={}, memoryMessages={}, toolCount={}, requestType={}",
                roleLabel(trace),
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                trace.getWorkingMemory() == null || trace.getWorkingMemory().getMessages() == null
                        ? 0
                        : trace.getWorkingMemory().getMessages().size(),
                trace.getToolCallCount(),
                req == null ? "" : req.getClass().getSimpleName()
        );
    }

    @Override
    public void onModelEnd(ReActTrace trace, ChatResponse resp) {
        log.info(
                "[{}] 模型响应, agent={}, session={}, step={}, hasChoices={}, isStream={}, aggregated={}",
                roleLabel(trace),
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                resp != null && resp.hasChoices(),
                resp != null && resp.isStream(),
                compact(resp == null || resp.getAggregationMessage() == null ? null : resp.getAggregationMessage().getContent())
        );
    }

    @Override
    public void onPlan(ReActTrace trace, AssistantMessage message) {
        log.info(
                "[{}] 计划输出, agent={}, session={}, step={}, content={}",
                roleLabel(trace),
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                compact(message == null ? null : message.getContent())
        );
    }

    @Override
    public void onReason(ReActTrace trace, AssistantMessage message) {
        log.info(
                "[{}] 推理输出, agent={}, session={}, step={}, content={}",
                roleLabel(trace),
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                compact(message == null ? null : message.getResultContent())
        );
    }

    @Override
    public void onThought(ReActTrace trace, String thought) {
        if (StrUtil.isBlank(thought)) {
            return;
        }

        log.info(
                "[{}] 思考过程, agent={}, session={}, step={}, content={}",
                roleLabel(trace),
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                compact(thought)
        );
    }

    @Override
    public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
        log.info(
                "[{}] 调用工具, agent={}, session={}, step={}, tool={}, args={}",
                roleLabel(trace),
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
                "[{}] 工具结果, agent={}, session={}, step={}, tool={}, durationMs={}, result={}",
                roleLabel(trace),
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
                "[{}] 任务结束, agent={}, session={}, steps={}, tools={}, durationMs={}, promptTokens={}, completionTokens={}, totalTokens={}, finalAnswer={}",
                roleLabel(trace),
                trace.getAgentName(),
                sessionId(trace),
                trace.getStepCount(),
                trace.getToolCallCount(),
                trace.getMetrics() == null ? null : trace.getMetrics().getTotalDuration(),
                trace.getMetrics() == null ? null : trace.getMetrics().getPromptTokens(),
                trace.getMetrics() == null ? null : trace.getMetrics().getCompletionTokens(),
                trace.getMetrics() == null ? null : trace.getMetrics().getTotalTokens(),
                compact(trace.getFinalAnswer())
        );

        if (log.isDebugEnabled()) {
            log.debug(
                    "[{}] 格式化历史, agent={}, session={}\n{}",
                    roleLabel(trace),
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

    private String roleLabel(ReActTrace trace) {
        String sessionId = sessionId(trace);
        String role = StrUtil.contains(sessionId, SUB_AGENT_MARKER) ? "SubAgent" : "Agent";
        String taskTitle = taskTitle(trace);
        if (StrUtil.isBlank(taskTitle)) {
            return role;
        }
        return role + ":" + compact(taskTitle);
    }

    private String taskTitle(ReActTrace trace) {
        if (trace == null || trace.getContext() == null) {
            return null;
        }
        return trace.getContext().getAs(TASK_TITLE_KEY);
    }
}
