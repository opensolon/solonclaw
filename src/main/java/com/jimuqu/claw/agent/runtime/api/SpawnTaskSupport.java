package com.jimuqu.claw.agent.runtime.api;

import com.jimuqu.claw.agent.runtime.support.SpawnTaskResult;

/**
 * 为当前运行提供派生子任务的能力。
 */
public interface SpawnTaskSupport {
    /**
     * 创建一个新的子任务运行。
     *
     * @param taskDescription 子任务描述
     * @return 子任务创建结果
     */
    default SpawnTaskResult spawnTask(String taskDescription) {
        return spawnTask(taskDescription, null);
    }

    /**
     * 创建一个新的子任务运行。
     *
     * @param taskDescription 子任务描述
     * @param batchKey 子任务批次键
     * @return 子任务创建结果
     */
    SpawnTaskResult spawnTask(String taskDescription, String batchKey);
}

