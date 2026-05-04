package com.jimuqu.solon.claw.core.model;

/** Result of requesting an active Agent run to stop. */
public class AgentRunStopResult {
    private boolean activeRun;
    private String runId;
    private String sessionId;
    private boolean interruptSent;
    private long startedAt;

    public static AgentRunStopResult none() {
        return new AgentRunStopResult();
    }

    public static AgentRunStopResult stopped(
            String runId, String sessionId, boolean interruptSent, long startedAt) {
        AgentRunStopResult result = new AgentRunStopResult();
        result.setActiveRun(true);
        result.setRunId(runId);
        result.setSessionId(sessionId);
        result.setInterruptSent(interruptSent);
        result.setStartedAt(startedAt);
        return result;
    }

    public boolean isActiveRun() {
        return activeRun;
    }

    public void setActiveRun(boolean activeRun) {
        this.activeRun = activeRun;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isInterruptSent() {
        return interruptSent;
    }

    public void setInterruptSent(boolean interruptSent) {
        this.interruptSent = interruptSent;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }
}
