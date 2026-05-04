package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.DelegationResult;
import com.jimuqu.solon.claw.core.model.DelegationTask;
import java.util.List;
import java.util.Map;

/** 子代理委托服务接口。 */
public interface DelegationService {
    /** 单任务委托。 */
    DelegationResult delegateSingle(String sourceKey, String prompt, String context)
            throws Exception;

    default DelegationResult delegateSingle(String sourceKey, DelegationTask task)
            throws Exception {
        return delegateSingle(
                sourceKey,
                task == null ? null : task.getPrompt(),
                task == null ? null : task.getContext());
    }

    /** 批量并行委托。 */
    List<DelegationResult> delegateBatch(String sourceKey, List<DelegationTask> tasks)
            throws Exception;

    default void setSpawnPaused(boolean paused) {}

    default boolean isSpawnPaused() {
        return false;
    }

    default boolean interruptSubagent(String subagentId) {
        return false;
    }

    default List<Map<String, Object>> activeSubagents() {
        return java.util.Collections.emptyList();
    }
}
