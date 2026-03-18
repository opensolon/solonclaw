package com.jimuqu.claw.agent.runtime;

/**
 * 描述一次主动通知的结果。
 */
public class NotificationResult {
    /** 是否成功发送。 */
    private boolean delivered;
    /** 实际投递的会话键。 */
    private String sessionKey;
    /** 结果说明。 */
    private String message;

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
