package com.jimuqu.claw.agent.runtime.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import org.noear.solon.ai.agent.Agent;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.tool.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理单次 agent turn 的运行时控制信号。
 */
public final class RunTurnControl {
    private static final ThreadLocal<RunTurnControl> HOLDER = new ThreadLocal<RunTurnControl>();

    private final AgentSession session;
    private final RuntimeSourceKind sourceKind;
    private final List<SpawnTaskResult> spawnedTasks = new ArrayList<SpawnTaskResult>();
    private String forcedResponse;
    private boolean finalizeAfterSpawn;

    private RunTurnControl(AgentSession session, RuntimeSourceKind sourceKind) {
        this.session = session;
        this.sourceKind = sourceKind;
    }

    public static RunTurnControl begin(AgentSession session, RuntimeSourceKind sourceKind) {
        RunTurnControl control = new RunTurnControl(session, sourceKind);
        HOLDER.set(control);
        return control;
    }

    public static RunTurnControl current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public void onTaskSpawned(SpawnTaskResult result) {
        if (result == null) {
            return;
        }

        spawnedTasks.add(result);
        tryFinishCurrentTurnAfterSpawnBatch();
    }

    public String getForcedResponse() {
        return forcedResponse;
    }

    public boolean isFinalizeAfterSpawn() {
        return finalizeAfterSpawn;
    }

    private void tryFinishCurrentTurnAfterSpawnBatch() {
        if (sourceKind != RuntimeSourceKind.USER_MESSAGE || session == null || spawnedTasks.isEmpty()) {
            return;
        }

        ReActTrace trace = ReActTrace.getCurrent(session.getSnapshot());
        if (trace == null || trace.getLastReasonMessage() == null || trace.getLastReasonMessage().getToolCalls() == null) {
            return;
        }

        int totalSpawnCalls = 0;
        for (ToolCall toolCall : trace.getLastReasonMessage().getToolCalls()) {
            if (toolCall != null && StrUtil.equals("spawn_task", toolCall.getName())) {
                totalSpawnCalls++;
            }
        }
        if (totalSpawnCalls <= 0 || spawnedTasks.size() < totalSpawnCalls) {
            return;
        }

        finalizeAfterSpawn = true;
        forcedResponse = buildSpawnArrangementFallback();
        trace.getWorkingMemory().addMessage(ChatMessage.ofUser(buildSpawnArrangementInstruction()));
    }

    private String buildSpawnArrangementFallback() {
        StringBuilder builder = new StringBuilder();
        builder.append("已安排 ").append(spawnedTasks.size()).append(" 个子任务并行处理。");
        return builder.toString();
    }

    private String buildSpawnArrangementInstruction() {
        StringBuilder builder = new StringBuilder();
        builder.append("[运行约束]").append('\n');
        builder.append("你刚刚已经成功创建了这一批子任务：").append('\n');
        int index = 1;
        for (SpawnTaskResult task : spawnedTasks) {
            builder.append(index++)
                    .append(". ")
                    .append(StrUtil.blankToDefault(
                            StrUtil.blankToDefault(task.getTaskTitle(), task.getTaskDescription()),
                            "(未记录任务描述)"
                    ));
            if (StrUtil.isNotBlank(task.getBatchKey())) {
                builder.append(" [batchKey=").append(task.getBatchKey()).append(']');
            }
            builder.append('\n');
        }
        builder.append("现在请直接面向用户，用自然语言说明你已经安排了哪些工作、接下来会如何同步结果。").append('\n');
        builder.append("不要继续调用工具，不要轮询子任务状态，不要输出 childRunId、sessionKey 这类内部细节。").append('\n');
        builder.append("保持正常人格与语气，直接结束本轮。");
        return builder.toString().trim();
    }
}
