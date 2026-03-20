package com.jimuqu.claw.channel.dingtalk.adapter;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.impl.AgentRuntimeService;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.channel.dingtalk.sender.DingTalkRobotSender;
import com.jimuqu.claw.config.props.DingTalkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 负责接入钉钉 Stream Robot 回调并将消息映射到统一运行时。
 */
public class DingTalkChannelAdapter implements
        ChannelAdapter,
        OpenDingTalkCallbackListener<ChatbotMessage, JSONObject> {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(DingTalkChannelAdapter.class);
    /** Agent 运行时服务。 */
    private final AgentRuntimeService agentRuntimeService;
    /** 钉钉消息发送服务。 */
    private final DingTalkRobotSender dingTalkRobotSender;
    /** 钉钉渠道配置。 */
    private final DingTalkProperties properties;
    /** 钉钉 Stream 客户端。 */
    private OpenDingTalkClient client;

    /**
     * 创建钉钉渠道适配器。
     *
     * @param agentRuntimeService Agent 运行时服务
     * @param dingTalkRobotSender 钉钉消息发送服务
     * @param properties 钉钉渠道配置
     */
    public DingTalkChannelAdapter(
            AgentRuntimeService agentRuntimeService,
            DingTalkRobotSender dingTalkRobotSender,
            DingTalkProperties properties
    ) {
        this.agentRuntimeService = agentRuntimeService;
        this.dingTalkRobotSender = dingTalkRobotSender;
        this.properties = properties;
    }

    /**
     * 启动钉钉 Stream 客户端。
     *
     * @throws Exception 启动异常
     */
    public void start() throws Exception {
        if (!properties.isEnabled()) {
            log.info("DingTalk channel disabled.");
            return;
        }

        if (!isConfigured()) {
            log.warn("DingTalk channel is enabled, but clientId/clientSecret/robotCode is incomplete.");
            return;
        }

        if (client != null) {
            return;
        }

        client = OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(properties.getClientId(), properties.getClientSecret()))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, this)
                .build();
        client.start();
        log.info("DingTalk stream robot client started.");
    }

    /**
     * 停止钉钉 Stream 客户端。
     *
     * @throws Exception 停止异常
     */
    public void stop() throws Exception {
        if (client != null) {
            client.stop();
            client = null;
            log.info("DingTalk stream robot client stopped.");
        }
    }

    /**
     * 返回适配器负责的渠道类型。
     *
     * @return 钉钉渠道类型
     */
    @Override
    public ChannelType channelType() {
        return ChannelType.DINGTALK;
    }

    /**
     * 发送一条钉钉出站消息。
     *
     * @param outboundEnvelope 出站消息
     */
    @Override
    public DeliveryResult send(OutboundEnvelope outboundEnvelope) {
        if (outboundEnvelope == null || outboundEnvelope.getReplyTarget() == null) {
            DeliveryResult result = new DeliveryResult();
            result.setDelivered(false);
            result.setMessage("missing reply target");
            result.setChannelType(ChannelType.DINGTALK);
            return result;
        }

        return dingTalkRobotSender.sendText(outboundEnvelope.getReplyTarget(), normalizeOutboundContent(outboundEnvelope));
    }

    /**
     * 处理钉钉机器人回调消息。
     *
     * @param message 钉钉机器人消息
     * @return 空 JSON 响应
     */
    @Override
    public JSONObject execute(ChatbotMessage message) {
        try {
            InboundEnvelope inboundEnvelope = toInboundEnvelope(message);
            if (inboundEnvelope != null) {
                agentRuntimeService.submitInbound(inboundEnvelope);
            }
        } catch (Throwable throwable) {
            log.warn("Failed to consume DingTalk bot message {}: {}", message == null ? null : message.getMsgId(), throwable.getMessage(), throwable);
        }
        return new JSONObject();
    }

    /**
     * 将钉钉回调消息转换为统一入站模型。
     *
     * @param message 钉钉机器人消息
     * @return 入站消息；若不应处理则返回 null
     */
    public InboundEnvelope toInboundEnvelope(ChatbotMessage message) {
        if (message == null) {
            return null;
        }

        String senderId = firstNonBlank(message.getSenderStaffId(), message.getSenderId());
        String conversationId = message.getConversationId();
        if (isBlank(senderId) || isBlank(conversationId)) {
            return null;
        }

        ConversationType conversationType = resolveConversationType(message);
        if (!isAllowed(conversationType, senderId, conversationId)) {
            log.info(
                    "Ignore DingTalk message because whitelist does not match. senderId={}, conversationId={}, conversationType={}",
                    senderId,
                    conversationId,
                    conversationType
            );
            return null;
        }

        String content = extractContent(message);
        if (isBlank(content)) {
            return null;
        }

        InboundEnvelope inboundEnvelope = new InboundEnvelope();
        inboundEnvelope.setMessageId(firstNonBlank(message.getMsgId(), "dingtalk-" + System.nanoTime()));
        inboundEnvelope.setChannelType(ChannelType.DINGTALK);
        inboundEnvelope.setChannelInstanceId("dingtalk-default");
        inboundEnvelope.setSenderId(senderId);
        inboundEnvelope.setConversationId(conversationId);
        inboundEnvelope.setConversationType(conversationType);
        inboundEnvelope.setContent(content);
        inboundEnvelope.setReceivedAt(message.getCreateAt() == null ? System.currentTimeMillis() : message.getCreateAt());
        inboundEnvelope.setSessionKey("dingtalk:" + conversationType.name().toLowerCase() + ":" + conversationId);
        inboundEnvelope.setReplyTarget(new ReplyTarget(ChannelType.DINGTALK, conversationType, conversationId, senderId));
        return inboundEnvelope;
    }

    /**
     * 推断钉钉消息属于私聊还是群聊。
     *
     * @param message 钉钉机器人消息
     * @return 会话类型
     */
    private ConversationType resolveConversationType(ChatbotMessage message) {
        String conversationId = message.getConversationId();
        String senderId = firstNonBlank(message.getSenderStaffId(), message.getSenderId());
        if (!properties.getGroupAllowFrom().isEmpty() && properties.getGroupAllowFrom().contains(conversationId)) {
            return ConversationType.GROUP;
        }
        if (!properties.getAllowFrom().isEmpty() && properties.getAllowFrom().contains(senderId)) {
            return ConversationType.PRIVATE;
        }

        String rawType = message.getConversationType();
        if (rawType == null) {
            return ConversationType.PRIVATE;
        }

        String normalized = rawType.trim().toLowerCase();
        if ("2".equals(normalized) || normalized.contains("group") || normalized.contains("chat")) {
            return ConversationType.GROUP;
        }
        return ConversationType.PRIVATE;
    }

    /**
     * 判断该消息是否命中允许列表。
     *
     * @param conversationType 会话类型
     * @param senderId 发送者标识
     * @param conversationId 会话标识
     * @return 若允许处理则返回 true
     */
    private boolean isAllowed(ConversationType conversationType, String senderId, String conversationId) {
        if (conversationType == ConversationType.GROUP) {
            return properties.getGroupAllowFrom().isEmpty() || properties.getGroupAllowFrom().contains(conversationId);
        }
        return properties.getAllowFrom().isEmpty() || properties.getAllowFrom().contains(senderId);
    }

    /**
     * 提取钉钉消息中的文本内容。
     *
     * @param message 钉钉机器人消息
     * @return 文本内容
     */
    private String extractContent(ChatbotMessage message) {
        MessageContent text = message.getText();
        if (text != null && !isBlank(text.getContent())) {
            return text.getContent();
        }

        MessageContent content = message.getContent();
        if (content == null) {
            return null;
        }

        if (!isBlank(content.getContent())) {
            return content.getContent();
        }
        if (!isBlank(content.getRecognition())) {
            return content.getRecognition();
        }
        if (!isBlank(content.getFileName())) {
            return "收到文件：" + content.getFileName();
        }
        if (!isBlank(content.getDownloadCode())) {
            return "收到附件消息，downloadCode=" + content.getDownloadCode();
        }
        return null;
    }

    /**
     * 将附件退化信息拼接到文本消息中。
     *
     * @param outboundEnvelope 出站消息
     * @return 归一化后的文本
     */
    private String normalizeOutboundContent(OutboundEnvelope outboundEnvelope) {
        String content = outboundEnvelope.getContent();
        if (content == null) {
            content = "";
        }

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

    /**
     * 判断钉钉必需配置是否完整。
     *
     * @return 若完整则返回 true
     */
    private boolean isConfigured() {
        return !isBlank(properties.getClientId())
                && !isBlank(properties.getClientSecret())
                && !isBlank(properties.getRobotCode());
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待检查字符串
     * @return 若为空白则返回 true
     */
    private boolean isBlank(String value) {
        return StrUtil.isBlank(value);
    }

    /**
     * 返回两个候选值中第一个非空白值。
     *
     * @param first 第一候选值
     * @param second 第二候选值
     * @return 第一个非空白值
     */
    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? null : second;
    }
}




