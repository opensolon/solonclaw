package com.jimuqu.solonclaw.heartbeat;

/**
 * Heartbeat 执行结果
 *
 * @author SolonClaw
 */
public class HeartbeatResult {

    private final boolean skipped;
    private final String reason;
    private final String response;
    private final long duration;
    private final boolean needsNotify;

    private HeartbeatResult(boolean skipped, String reason, String response, long duration, boolean needsNotify) {
        this.skipped = skipped;
        this.reason = reason;
        this.response = response;
        this.duration = duration;
        this.needsNotify = needsNotify;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public String getReason() {
        return reason;
    }

    public String getResponse() {
        return response;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isNeedsNotify() {
        return needsNotify;
    }

    /**
     * 创建跳过结果
     */
    public static HeartbeatResult skipped(String reason) {
        return new HeartbeatResult(true, reason, null, 0, false);
    }

    /**
     * 创建成功结果（需要通知）
     */
    public static HeartbeatResult success(String response, long duration, boolean needsNotify) {
        return new HeartbeatResult(false, null, response, duration, needsNotify);
    }

    /**
     * 创建成功结果（不需要通知）
     */
    public static HeartbeatResult successNoNotify(String response, long duration) {
        return new HeartbeatResult(false, null, response, duration, false);
    }

    /**
     * 创建失败结果
     */
    public static HeartbeatResult error(String errorMessage, long duration) {
        return new HeartbeatResult(false, "error: " + errorMessage, null, duration, true);
    }

    @Override
    public String toString() {
        if (skipped) {
            return "HeartbeatResult{skipped=true, reason='" + reason + "'}";
        }
        return "HeartbeatResult{response='" + (response != null ? response.substring(0, Math.min(50, response.length())) + "..." : null) +
                "', duration=" + duration + "ms, needsNotify=" + needsNotify + "}";
    }
}
