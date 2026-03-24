package com.jimuqu.claw.agent.runtime.api;

/**
 * 子任务进度上报能力。
 */
public interface ProgressReportSupport {
    /**
     * 上报当前子任务的进度。
     *
     * @param phase  阶段标签（如"分析中"、"编写代码"）
     * @param detail 详细进度说明
     */
    void reportProgress(String phase, String detail);
}
