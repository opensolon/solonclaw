package com.jimuqu.solon.claw.gateway.feedback;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.support.DisplaySettingsService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 面向消息渠道的中间态反馈 sink。 */
public class GatewayConversationFeedbackSink implements ConversationFeedbackSink {
    private static final Logger log =
            LoggerFactory.getLogger(GatewayConversationFeedbackSink.class);

    private final GatewayMessage message;
    private final DeliveryService deliveryService;
    private final DisplaySettingsService displaySettingsService;

    private String lastToolName;
    private String lastReasoning;
    private long lastReasoningAt;
    private int toolStartedCount;
    private int toolFinishedCount;
    private String dingtalkCardBizId;
    private boolean dingtalkCardSent;

    public GatewayConversationFeedbackSink(
            GatewayMessage message,
            DeliveryService deliveryService,
            DisplaySettingsService displaySettingsService) {
        this.message = message;
        this.deliveryService = deliveryService;
        this.displaySettingsService = displaySettingsService;
    }

    @Override
    public void onToolStarted(String toolName, Map<String, Object> args) {
        String progressMode = displaySettingsService.resolveToolProgress(message.getPlatform());
        if ("off".equals(progressMode)) {
            return;
        }

        toolStartedCount++;
        boolean verbose = "verbose".equals(progressMode);
        boolean emit =
                "all".equals(progressMode)
                        || verbose
                        || ("new".equals(progressMode) && !StrUtil.equals(toolName, lastToolName));
        lastToolName = toolName;
        if (!emit) {
            return;
        }

        String preview =
                ToolPreviewSupport.buildPreview(
                        toolName, args, displaySettingsService.toolPreviewLength(), verbose);

        if (message.getPlatform() == PlatformType.DINGTALK
                && StrUtil.isNotBlank(displaySettingsService.dingtalkProgressCardTemplateId())) {
            if (!sendDingtalkProgressCard("进行中", toolName, preview, false)) {
                sendText(buildToolProgressText(toolName, preview));
            }
            return;
        }

        sendText(buildToolProgressText(toolName, preview));
    }

    @Override
    public void onToolFinished(String toolName, String result, long durationMs) {
        toolFinishedCount++;
    }

    @Override
    public void onReasoning(String thought) {
        if (!displaySettingsService.isReasoningVisible(
                message.sourceKey(), message.getPlatform())) {
            return;
        }

        String normalized = normalize(thought);
        if (normalized.length() == 0 || StrUtil.equals(normalized, lastReasoning)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (displaySettingsService.progressThrottleMs() > 0
                && lastReasoningAt > 0
                && now - lastReasoningAt < displaySettingsService.progressThrottleMs()) {
            return;
        }

        lastReasoning = normalized;
        lastReasoningAt = now;
        sendText(
                "【思考】"
                        + truncate(
                                normalized,
                                Math.max(200, displaySettingsService.toolPreviewLength() * 4)));
    }

    @Override
    public void onFinalReply(String finalReply) {
        if (!dingtalkCardSent || message.getPlatform() != PlatformType.DINGTALK) {
            return;
        }

        String summary = "本轮共调用 " + toolStartedCount + " 个工具，完成 " + toolFinishedCount + " 个结果回填";
        sendDingtalkProgressCard(
                "已完成",
                "final_reply",
                truncate(
                        normalize(finalReply),
                        Math.max(120, displaySettingsService.toolPreviewLength() * 3)),
                true,
                summary);
    }

    private String buildToolProgressText(String toolName, String preview) {
        if (StrUtil.isBlank(preview)) {
            return "【工具】" + toolName;
        }
        return "【工具】" + toolName + " · " + preview;
    }

    private boolean sendDingtalkProgressCard(
            String status, String toolName, String preview, boolean updateOnly) {
        String summary = "当前步骤：" + toolName + "，已启动 " + toolStartedCount + " 个工具";
        return sendDingtalkProgressCard(status, toolName, preview, updateOnly, summary);
    }

    private boolean sendDingtalkProgressCard(
            String status, String toolName, String preview, boolean updateOnly, String summary) {
        try {
            DeliveryRequest request = baseRequest();
            String templateId = displaySettingsService.dingtalkProgressCardTemplateId();
            request.getChannelExtras().put("mode", "ai_card");
            request.getChannelExtras().put("cardTemplateId", templateId);
            request.getChannelExtras().put("cardBizId", ensureDingtalkCardBizId());
            request.getChannelExtras()
                    .put(
                            "cardData",
                            DingTalkProgressCardSupport.buildCardData(
                                    "Jimuqu 长任务进度",
                                    status,
                                    summary,
                                    StrUtil.blankToDefault(preview, toolName),
                                    DateUtil.now()));
            if (updateOnly || dingtalkCardSent) {
                request.getChannelExtras().put("updateExisting", true);
            }
            deliveryService.deliver(request);
            dingtalkCardSent = true;
            return true;
        } catch (Exception e) {
            log.warn(
                    "DingTalk progress card delivery failed: chatId={}, toolName={}",
                    message.getChatId(),
                    toolName,
                    e);
            return false;
        }
    }

    private void sendText(String text) {
        if (StrUtil.isBlank(text)) {
            return;
        }

        try {
            DeliveryRequest request = baseRequest();
            request.setText(text);
            deliveryService.deliver(request);
        } catch (Exception e) {
            log.warn(
                    "Conversation feedback delivery failed: platform={}, chatId={}",
                    message.getPlatform(),
                    message.getChatId(),
                    e);
        }
    }

    private DeliveryRequest baseRequest() {
        DeliveryRequest request = new DeliveryRequest();
        request.setPlatform(message.getPlatform());
        request.setChatId(message.getChatId());
        request.setUserId(message.getUserId());
        request.setChatType(message.getChatType());
        request.setThreadId(message.getThreadId());
        return request;
    }

    private String ensureDingtalkCardBizId() {
        if (StrUtil.isBlank(dingtalkCardBizId)) {
            dingtalkCardBizId =
                    "jimuqu-progress-"
                            + Integer.toHexString(
                                    StrUtil.nullToEmpty(message.sourceKey()).hashCode())
                            + "-"
                            + Integer.toHexString(
                                    StrUtil.nullToEmpty(message.getThreadId()).hashCode())
                            + "-"
                            + Long.toHexString(System.currentTimeMillis());
        }
        return dingtalkCardBizId;
    }

    private String normalize(String text) {
        return StrUtil.nullToEmpty(text).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return StrUtil.nullToEmpty(text);
        }
        return text.substring(0, Math.max(0, limit - 3)) + "...";
    }
}
