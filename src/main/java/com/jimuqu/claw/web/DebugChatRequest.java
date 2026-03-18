package com.jimuqu.claw.web;

/**
 * 描述调试页发起聊天请求时的参数。
 */
public class DebugChatRequest {
    /** 调试会话标识。 */
    private String sessionId;
    /** 用户输入文本。 */
    private String message;

    /**
     * 返回调试会话标识。
     *
     * @return 调试会话标识
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置调试会话标识。
     *
     * @param sessionId 调试会话标识
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 返回用户输入文本。
     *
     * @return 用户输入文本
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置用户输入文本。
     *
     * @param message 用户输入文本
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
