package com.jimuqu.claw.channel.feishu.sender;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.channel.feishu.gateway.FeishuMessageGateway;
import com.jimuqu.claw.channel.feishu.gateway.FeishuSdkMessageGateway;
import com.jimuqu.claw.config.props.FeishuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责向飞书发送 markdown 卡片，并在支持时对同一运行任务做卡片 patch 更新。
 */
public class FeishuBotSender {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(FeishuBotSender.class);
    /** 飞书配置。 */
    private final FeishuProperties properties;
    /** 飞书消息网关。 */
    private final FeishuMessageGateway messageGateway;
    /** 运行任务对应的飞书消息 ID，用于流式 patch 更新。 */
    private final Map<String, String> progressMessageIds = new ConcurrentHashMap<>();

    /**
     * 创建飞书消息发送器。
     *
     * @param properties 飞书配置
     */
    public FeishuBotSender(FeishuProperties properties) {
        this(properties, new FeishuSdkMessageGateway(properties));
    }

    /**
     * 使用显式网关创建发送器。
     *
     * @param properties 飞书配置
     * @param messageGateway 消息网关
     */
    public FeishuBotSender(FeishuProperties properties, FeishuMessageGateway messageGateway) {
        this.properties = properties;
        this.messageGateway = messageGateway;
    }

    /**
     * 发送或更新一条飞书消息。
     *
     * @param outboundEnvelope 出站消息
     */
    public void send(OutboundEnvelope outboundEnvelope) {
        if (outboundEnvelope == null || outboundEnvelope.getReplyTarget() == null) {
            return;
        }

        String content = normalizeContent(outboundEnvelope);
        if (StrUtil.isBlank(content)) {
            return;
        }

        ReplyTarget replyTarget = outboundEnvelope.getReplyTarget();
        if (StrUtil.isBlank(replyTarget.getConversationId())) {
            log.warn("Skip Feishu send because conversationId is missing.");
            return;
        }

        try {
            String runId = StrUtil.blankToDefault(outboundEnvelope.getRunId(), "runless:" + System.nanoTime());
            String cardContent = cardMessageParam(content);
            if (outboundEnvelope.isProgress() && properties.isStreamingReply()) {
                sendProgress(runId, replyTarget, cardContent);
                return;
            }
            sendFinal(runId, replyTarget, cardContent);
        } catch (Exception exception) {
            log.warn("Failed to send Feishu message: {}", exception.getMessage(), exception);
        }
    }

    /**
     * 发送一条运行中的增量卡片；若同 run 已存在消息则更新。
     *
     * @param runId 运行任务标识
     * @param replyTarget 回复目标
     * @param cardContent 卡片 JSON
     * @throws Exception 发送异常
     */
    private void sendProgress(String runId, ReplyTarget replyTarget, String cardContent) throws Exception {
        String messageId = progressMessageIds.get(runId);
        if (StrUtil.isNotBlank(messageId)) {
            messageGateway.patchCardMessage(messageId, cardContent);
            return;
        }

        String createdMessageId = messageGateway.createCardMessage(replyTarget.getConversationId(), cardContent);
        if (StrUtil.isNotBlank(createdMessageId)) {
            progressMessageIds.put(runId, createdMessageId);
        }
    }

    /**
     * 发送最终结果；若此前已有进度卡片则直接 patch 为最终内容。
     *
     * @param runId 运行任务标识
     * @param replyTarget 回复目标
     * @param cardContent 卡片 JSON
     * @throws Exception 发送异常
     */
    private void sendFinal(String runId, ReplyTarget replyTarget, String cardContent) throws Exception {
        String messageId = progressMessageIds.get(runId);
        if (StrUtil.isNotBlank(messageId)) {
            messageGateway.patchCardMessage(messageId, cardContent);
            progressMessageIds.remove(runId, messageId);
            return;
        }

        messageGateway.createCardMessage(replyTarget.getConversationId(), cardContent);
    }

    /**
     * 将消息内容包装成飞书交互式卡片 JSON。
     *
     * @param content markdown 文本
     * @return 卡片 JSON 字符串
     */
    public String cardMessageParam(String content) {
        JSONObject root = new JSONObject();
        root.put("schema", "2.0");

        JSONObject config = new JSONObject();
        config.put("update_multi", true);
        config.put("streaming_mode", false);
        root.put("config", config);

        JSONObject body = new JSONObject();
        body.put("direction", "vertical");
        body.put("padding", "12px 12px 12px 12px");

        JSONObject markdown = new JSONObject();
        markdown.put("tag", "markdown");
        markdown.put("content", StrUtil.blankToDefault(content, ""));
        markdown.put("text_align", "left");
        markdown.put("text_size", "normal");
        markdown.put("margin", "0px 0px 0px 0px");

        JSONArray elements = new JSONArray();
        elements.add(markdown);
        body.put("elements", elements);
        root.put("body", body);
        return root.toJSONString();
    }

    /**
     * 将附件退化信息拼接到正文中。
     *
     * @param outboundEnvelope 出站消息
     * @return 归一化后的文本
     */
    private String normalizeContent(OutboundEnvelope outboundEnvelope) {
        String content = StrUtil.blankToDefault(outboundEnvelope.getContent(), "");
        if (outboundEnvelope.getMedia() == null || outboundEnvelope.getMedia().isEmpty()) {
            return content;
        }

        StringBuilder builder = new StringBuilder(content);
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("附件暂以文本回退发送：\n");
        for (String media : outboundEnvelope.getMedia()) {
            builder.append("- ").append(media).append('\n');
        }
        return builder.toString().trim();
    }
}



