package com.jimuqu.solon.claw.gateway.feedback;

import java.util.Map;

/** 对话中间态反馈接收器。 */
public interface ConversationFeedbackSink {
    ConversationFeedbackSink NOOP = new ConversationFeedbackSink() {};

    /** 工具开始执行。 */
    default void onToolStarted(String toolName, Map<String, Object> args) {}

    /** 工具执行结束。 */
    default void onToolFinished(String toolName, String result, long durationMs) {}

    /** reasoning/thought 中间态。 */
    default void onReasoning(String thought) {}

    /** 本轮最终回复。 */
    default void onFinalReply(String finalReply) {}

    /** 返回空实现。 */
    static ConversationFeedbackSink noop() {
        return NOOP;
    }
}
