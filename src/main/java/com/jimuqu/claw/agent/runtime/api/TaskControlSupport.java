package com.jimuqu.claw.agent.runtime.api;

import com.jimuqu.claw.agent.runtime.support.TaskControlResult;

/**
 * 子任务控制能力。
 */
public interface TaskControlSupport {
    TaskControlResult cancelTask(String runId);

    TaskControlResult appendInstruction(String runId, String instruction);
}
