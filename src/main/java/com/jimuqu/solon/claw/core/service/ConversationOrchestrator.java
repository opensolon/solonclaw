package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;

/** Agent 主循环调度接口。 */
public interface ConversationOrchestrator {
    /** 处理普通入站消息。 */
    GatewayReply handleIncoming(GatewayMessage message) throws Exception;

    /** 处理普通入站消息，并向事件接收器输出运行过程。 */
    default GatewayReply handleIncoming(GatewayMessage message, ConversationEventSink eventSink)
            throws Exception {
        return handleIncoming(message);
    }

    /** 处理定时任务触发的消息。 */
    GatewayReply runScheduled(GatewayMessage syntheticMessage) throws Exception;

    /** 处理定时任务触发的消息，并向事件接收器输出运行过程。 */
    default GatewayReply runScheduled(
            GatewayMessage syntheticMessage, ConversationEventSink eventSink) throws Exception {
        return runScheduled(syntheticMessage);
    }

    /** 恢复当前来源键下因危险命令审批而挂起的会话。 */
    GatewayReply resumePending(String sourceKey) throws Exception;

    /** 恢复当前来源键下因危险命令审批而挂起的会话，并输出运行过程。 */
    default GatewayReply resumePending(String sourceKey, ConversationEventSink eventSink)
            throws Exception {
        return resumePending(sourceKey);
    }
}
