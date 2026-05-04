package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.LlmResult;
import java.util.Map;

/** 面向 dashboard chat 的运行事件接收器。 */
public interface ConversationEventSink {
    ConversationEventSink NOOP = new ConversationEventSink() {};

    /** 运行开始。 */
    default void onRunStarted(String sessionId) {}

    default void onAttemptStarted(String runId, int attemptNo, String provider, String model) {}

    default void onAttemptCompleted(String runId, int attemptNo, String status, String reason) {}

    default void onCompressionDecision(
            String runId,
            boolean compressed,
            String reason,
            int estimatedTokens,
            int thresholdTokens) {}

    default void onRecoveryStarted(String runId, String recoveryType) {}

    default void onFallback(String runId, String fromProvider, String toProvider, String reason) {}

    default void onDeliveryEvent(String runId, String status, String detail) {}

    /** assistant 文本增量。 */
    default void onAssistantDelta(String delta) {}

    /** assistant reasoning 文本增量。 */
    default void onReasoningDelta(String delta) {}

    /** 工具开始。 */
    default void onToolStarted(String toolName, Map<String, Object> args) {}

    /** 工具结束。 */
    default void onToolCompleted(String toolName, String result, long durationMs) {}

    /** 运行成功完成。 */
    default void onRunCompleted(String sessionId, String finalReply, LlmResult result) {}

    /** 运行失败。 */
    default void onRunFailed(String sessionId, Throwable error) {}

    /** 返回空实现。 */
    static ConversationEventSink noop() {
        return NOOP;
    }
}
