package com.jimuqu.claw.agent.runtime.impl;

import com.jimuqu.claw.agent.runtime.registry.ActiveTaskRegistry;
import org.noear.solon.ai.agent.Agent;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.chat.message.AssistantMessage;

import java.util.Map;

/**
 * 在 ReAct 执行过程中检查合作取消标志，必要时提前结束当前运行。
 */
public class CancellationInterceptor implements ReActInterceptor {
    private final ActiveTaskRegistry activeTaskRegistry;

    public CancellationInterceptor(ActiveTaskRegistry activeTaskRegistry) {
        this.activeTaskRegistry = activeTaskRegistry;
    }

    @Override
    public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
        checkCancelled(trace);
    }

    @Override
    public void onReason(ReActTrace trace, AssistantMessage message) {
        checkCancelled(trace);
    }

    private void checkCancelled(ReActTrace trace) {
        String runId = resolveRunId(trace);
        if (runId == null) {
            return;
        }
        if (activeTaskRegistry.isCancelRequested(runId)) {
            trace.setFinalAnswer("任务已被取消。");
            trace.setRoute(Agent.ID_END);
        }
    }

    private String resolveRunId(ReActTrace trace) {
        if (trace == null || trace.getContext() == null) {
            return null;
        }
        Object runId = trace.getContext().get("runId");
        return runId == null ? null : String.valueOf(runId);
    }
}
