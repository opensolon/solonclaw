package com.jimuqu.claw.agent.runtime.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.store.RuntimeStoreService;

/**
 * 消除 AgentRuntimeService / SystemEventRunner / IsolatedAgentRunService 中重复的投递与响应工具方法。
 */
public final class RuntimeDeliveryHelper {
    private RuntimeDeliveryHelper() {
    }

    public static void recordDeliveryResult(RuntimeStoreService store, String runId, DeliveryResult deliveryResult) {
        if (deliveryResult == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("channel=").append(deliveryResult.getChannelType())
                .append(", segmentCount=").append(deliveryResult.getSegmentCount())
                .append(", originalLength=").append(deliveryResult.getOriginalLength())
                .append(", finalLength=").append(deliveryResult.getFinalLength());
        if (StrUtil.isNotBlank(deliveryResult.getMessage())) {
            message.append(", detail=").append(deliveryResult.getMessage());
        }

        if (!deliveryResult.isDelivered()) {
            store.appendRunEvent(runId, "delivery_failed", message.toString());
            return;
        }
        store.appendRunEvent(runId, "delivery_sent", message.toString());
        if (deliveryResult.isSegmented()) {
            store.appendRunEvent(runId, "delivery_segmented", message.toString());
        }
        if (deliveryResult.isTruncated()) {
            store.appendRunEvent(runId, "delivery_truncated", message.toString());
        }
    }

    public static void applyDeliveryResult(NotificationResult result, DeliveryResult deliveryResult) {
        if (result == null || deliveryResult == null) {
            return;
        }
        result.setTruncated(deliveryResult.isTruncated());
        result.setSegmented(deliveryResult.isSegmented());
        result.setSegmentCount(deliveryResult.getSegmentCount());
        result.setOriginalLength(deliveryResult.getOriginalLength());
        result.setFinalLength(deliveryResult.getFinalLength());
        result.setChannelType(deliveryResult.getChannelType() == null ? null : deliveryResult.getChannelType().name());
        result.setMessage(deliveryResult.getMessage());
    }

    public static String normalizeVisibleResponse(String response) {
        return RuntimeReplyProtocol.normalizeVisibleResponse(response);
    }

    public static boolean isNoReply(String response) {
        return RuntimeReplyProtocol.isNoReply(response);
    }

    public static boolean isFinalReplyOnce(String response) {
        return RuntimeReplyProtocol.isFinalReplyOnce(response);
    }
}
