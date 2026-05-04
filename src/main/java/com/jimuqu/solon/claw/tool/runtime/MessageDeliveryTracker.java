package com.jimuqu.solon.claw.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 跟踪当前来源键上由 send_message 发起的回送，避免最终答复重复发送。 */
public final class MessageDeliveryTracker {
    private static final long TTL_MILLIS = 5 * 60 * 1000L;
    private static final Map<String, DeliveryEchoRecord> RECORDS =
            new ConcurrentHashMap<String, DeliveryEchoRecord>();

    private MessageDeliveryTracker() {}

    public static void recordEcho(
            String sourceKey,
            PlatformType sourcePlatform,
            String sourceChatId,
            PlatformType targetPlatform,
            String targetChatId,
            String text,
            boolean hasAttachments) {
        if (StrUtil.isBlank(sourceKey) || sourcePlatform == null || targetPlatform == null) {
            return;
        }
        if (sourcePlatform != targetPlatform) {
            return;
        }
        if (!StrUtil.equals(
                StrUtil.nullToEmpty(sourceChatId).trim(),
                StrUtil.nullToEmpty(targetChatId).trim())) {
            return;
        }
        if (!hasAttachments) {
            return;
        }
        pruneExpired();
        DeliveryEchoRecord record = new DeliveryEchoRecord();
        record.sourceKey = sourceKey;
        record.normalizedText = normalize(text);
        record.hasAttachments = true;
        record.createdAt = System.currentTimeMillis();
        RECORDS.put(sourceKey, record);
    }

    public static boolean consumeDuplicateFinalReply(String sourceKey, String finalReply) {
        if (StrUtil.isBlank(sourceKey)) {
            return false;
        }
        pruneExpired();
        DeliveryEchoRecord record = RECORDS.get(sourceKey);
        if (record == null || !record.hasAttachments) {
            return false;
        }
        String normalizedReply = normalize(finalReply);
        if (normalizedReply.length() == 0) {
            return false;
        }
        if (!normalizedReply.equals(record.normalizedText)) {
            return false;
        }
        RECORDS.remove(sourceKey);
        return true;
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, DeliveryEchoRecord> entry : RECORDS.entrySet()) {
            if (now - entry.getValue().createdAt >= TTL_MILLIS) {
                RECORDS.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String normalize(String text) {
        return StrUtil.nullToEmpty(text)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static class DeliveryEchoRecord {
        private String sourceKey;
        private String normalizedText;
        private boolean hasAttachments;
        private long createdAt;
    }
}
