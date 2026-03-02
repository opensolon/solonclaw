package com.jimuqu.solonclaw.monitor;

/**
 * 性能统计数据
 */
public class PerformanceStats {
    private long totalRequests;
    private long successRequests;
    private long failedRequests;
    private long averageResponseTime;
    private double successRate;
    private long totalConversations;
    private long activeConversations;
    private java.util.Map<String, Long> toolCallCounts;
    private java.util.Map<String, Long> toolCallErrors;

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getSuccessRequests() {
        return successRequests;
    }

    public void setSuccessRequests(long successRequests) {
        this.successRequests = successRequests;
    }

    public long getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(long failedRequests) {
        this.failedRequests = failedRequests;
    }

    public long getAverageResponseTime() {
        return averageResponseTime;
    }

    public void setAverageResponseTime(long averageResponseTime) {
        this.averageResponseTime = averageResponseTime;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public long getTotalConversations() {
        return totalConversations;
    }

    public void setTotalConversations(long totalConversations) {
        this.totalConversations = totalConversations;
    }

    public long getActiveConversations() {
        return activeConversations;
    }

    public void setActiveConversations(long activeConversations) {
        this.activeConversations = activeConversations;
    }

    public java.util.Map<String, Long> getToolCallCounts() {
        return toolCallCounts;
    }

    public void setToolCallCounts(java.util.Map<String, Long> toolCallCounts) {
        this.toolCallCounts = toolCallCounts;
    }

    public java.util.Map<String, Long> getToolCallErrors() {
        return toolCallErrors;
    }

    public void setToolCallErrors(java.util.Map<String, Long> toolCallErrors) {
        this.toolCallErrors = toolCallErrors;
    }
}