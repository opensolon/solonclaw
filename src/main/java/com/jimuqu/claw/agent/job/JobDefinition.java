package com.jimuqu.claw.agent.job;

import com.jimuqu.claw.agent.model.route.ReplyTarget;

/**
 * 定义一个可持久化的定时任务。
 */
public class JobDefinition {
    private String name;
    private String mode;
    private String scheduleValue;
    private long initialDelay;
    private String zone;
    private boolean enabled = true;
    private String prompt;
    private String sessionKey;
    private ReplyTarget replyTarget;
    private long createdAt;
    private long updatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getScheduleValue() {
        return scheduleValue;
    }

    public void setScheduleValue(String scheduleValue) {
        this.scheduleValue = scheduleValue;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public ReplyTarget getReplyTarget() {
        return replyTarget;
    }

    public void setReplyTarget(ReplyTarget replyTarget) {
        this.replyTarget = replyTarget;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

