package com.jimuqu.solon.claw.core.service;

/** Raised when a user cancels the active Agent run. */
public class AgentRunCancelledException extends RuntimeException {
    public AgentRunCancelledException() {
        super("当前任务已停止。");
    }
}
