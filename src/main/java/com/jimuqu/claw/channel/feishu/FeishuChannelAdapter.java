package com.jimuqu.claw.channel.feishu;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.model.ChannelType;
import com.jimuqu.claw.agent.model.ConversationType;
import com.jimuqu.claw.agent.model.InboundEnvelope;
import com.jimuqu.claw.agent.model.OutboundEnvelope;
import com.jimuqu.claw.agent.model.ReplyTarget;
import com.jimuqu.claw.agent.runtime.AgentRuntimeService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 负责接入飞书长连接机器人消息，并将其映射到统一运行时。
 */
public class FeishuChannelAdapter implements ChannelAdapter {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(FeishuChannelAdapter.class);
    /** Agent 运行时服务。 */
    private final AgentRuntimeService agentRuntimeService;
    /** 飞书消息发送服务。 */
    private final FeishuBotSender feishuBotSender;
    /** 飞书渠道配置。 */
    private final SolonClawProperties.Feishu properties;
    /** 飞书长连接客户端。 */
    private com.lark.oapi.ws.Client wsClient;

    /**
     * 创建飞书渠道适配器。
     *
     * @param agentRuntimeService Agent 运行时服务
     * @param feishuBotSender 飞书消息发送服务
     * @param properties 飞书配置
     */
    public FeishuChannelAdapter(
            AgentRuntimeService agentRuntimeService,
            FeishuBotSender feishuBotSender,
            SolonClawProperties.Feishu properties
    ) {
        this.agentRuntimeService = agentRuntimeService;
        this.feishuBotSender = feishuBotSender;
        this.properties = properties;
    }

    /**
     * 启动飞书长连接客户端。
     */
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Feishu channel disabled.");
            return;
        }

        if (!isConfigured()) {
            log.warn("Feishu channel is enabled, but appId/appSecret is incomplete.");
            return;
        }

        if (wsClient != null) {
            return;
        }

        EventDispatcher eventHandler = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        consumeInboundEvent(event);
                    }
                })
                .build();

        wsClient = new com.lark.oapi.ws.Client.Builder(properties.getAppId(), properties.getAppSecret())
                .eventHandler(eventHandler)
                .autoReconnect(Boolean.TRUE)
                .domain(StrUtil.blankToDefault(properties.getBaseDomain(), "https://open.feishu.cn"))
                .build();
        wsClient.start();
        log.info("Feishu ws bot client started.");
    }

    /**
     * 停止飞书长连接客户端。
     */
    public void stop() {
        if (wsClient == null) {
            return;
        }

        try {
            Method disconnect = wsClient.getClass().getDeclaredMethod("disconnect");
            disconnect.setAccessible(true);
            disconnect.invoke(wsClient);
            log.info("Feishu ws bot client stopped.");
        } catch (Exception exception) {
            log.warn("Failed to stop Feishu ws client cleanly: {}", exception.getMessage(), exception);
        } finally {
            wsClient = null;
        }
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.FEISHU;
    }

    @Override
    public boolean supportsProgressUpdates() {
        return properties.isStreamingReply();
    }

    @Override
    public void send(OutboundEnvelope outboundEnvelope) {
        feishuBotSender.send(outboundEnvelope);
    }

    /**
     * 处理飞书接收到的消息事件。
     *
     * @param event 飞书消息事件
     */
    private void consumeInboundEvent(P2MessageReceiveV1 event) {
        try {
            InboundEnvelope inboundEnvelope = toInboundEnvelope(event);
            if (inboundEnvelope != null) {
                agentRuntimeService.submitInbound(inboundEnvelope);
            }
        } catch (Throwable throwable) {
            log.warn("Failed to consume Feishu bot message: {}", throwable.getMessage(), throwable);
        }
    }

    /**
     * 将飞书消息事件转换为统一入站模型。
     *
     * @param event 飞书消息事件
     * @return 入站消息；若不应处理则返回 null
     */
    InboundEnvelope toInboundEnvelope(P2MessageReceiveV1 event) {
        if (event == null || event.getEvent() == null || event.getEvent().getMessage() == null) {
            return null;
        }

        P2MessageReceiveV1Data data = event.getEvent();
        EventMessage message = data.getMessage();
        EventSender sender = data.getSender();
        if (sender != null && StrUtil.equalsIgnoreCase(sender.getSenderType(), "app")) {
            return null;
        }

        String senderId = resolveSenderId(sender == null ? null : sender.getSenderId());
        String conversationId = message.getChatId();
        if (StrUtil.isBlank(senderId) || StrUtil.isBlank(conversationId)) {
            return null;
        }

        ConversationType conversationType = resolveConversationType(message);
        if (!isAllowed(conversationType, senderId, conversationId)) {
            log.info(
                    "Ignore Feishu message because whitelist does not match. senderId={}, conversationId={}, conversationType={}",
                    senderId,
                    conversationId,
                    conversationType
            );
            return null;
        }

        String content = extractContent(message);
        if (StrUtil.isBlank(content)) {
            return null;
        }

        InboundEnvelope inboundEnvelope = new InboundEnvelope();
        inboundEnvelope.setMessageId(StrUtil.blankToDefault(message.getMessageId(), "feishu-" + System.nanoTime()));
        inboundEnvelope.setChannelType(ChannelType.FEISHU);
        inboundEnvelope.setChannelInstanceId("feishu-default");
        inboundEnvelope.setSenderId(senderId);
        inboundEnvelope.setConversationId(conversationId);
        inboundEnvelope.setConversationType(conversationType);
        inboundEnvelope.setContent(content);
        inboundEnvelope.setReceivedAt(parseTime(message.getCreateTime()));
        inboundEnvelope.setSessionKey("feishu:" + conversationType.name().toLowerCase() + ":" + conversationId);
        inboundEnvelope.setReplyTarget(new ReplyTarget(ChannelType.FEISHU, conversationType, conversationId, senderId));
        return inboundEnvelope;
    }

    /**
     * 提取飞书消息中的文本内容。
     *
     * @param message 飞书事件消息
     * @return 文本内容
     */
    private String extractContent(EventMessage message) {
        if (message == null || StrUtil.isBlank(message.getMessageType()) || StrUtil.isBlank(message.getContent())) {
            return null;
        }

        String messageType = message.getMessageType().trim().toLowerCase();
        if ("text".equals(messageType)) {
            JSONObject jsonObject = parseObject(message.getContent());
            return jsonObject == null ? null : StrUtil.trim(jsonObject.getString("text"));
        }
        if ("post".equals(messageType)) {
            return extractPostText(parseObject(message.getContent())).trim();
        }
        if ("file".equals(messageType)) {
            return "收到文件消息";
        }
        if ("image".equals(messageType)) {
            return "收到图片消息";
        }
        return null;
    }

    /**
     * 将 post 富文本中的 text 片段抽取为普通文本。
     *
     * @param jsonObject post 内容 JSON
     * @return 抽取后的文本
     */
    private String extractPostText(JSONObject jsonObject) {
        if (jsonObject == null || jsonObject.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        appendPostText(jsonObject, builder);
        return builder.toString().trim();
    }

    /**
     * 递归收集富文本中的标题与文本节点。
     *
     * @param node 当前节点
     * @param builder 文本累积器
     */
    private void appendPostText(Object node, StringBuilder builder) {
        if (node instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) node;
            String title = StrUtil.trim(jsonObject.getString("title"));
            if (StrUtil.isNotBlank(title)) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(title);
            }

            String tag = StrUtil.blankToDefault(jsonObject.getString("tag"), "");
            if ("text".equals(tag)) {
                String text = StrUtil.trim(jsonObject.getString("text"));
                if (StrUtil.isNotBlank(text)) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }

            for (String key : jsonObject.keySet()) {
                appendPostText(jsonObject.get(key), builder);
            }
            return;
        }

        if (node instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) node;
            for (Object item : jsonArray) {
                appendPostText(item, builder);
            }
        }
    }

    /**
     * 推断飞书消息属于私聊还是群聊。
     *
     * @param message 飞书事件消息
     * @return 会话类型
     */
    private ConversationType resolveConversationType(EventMessage message) {
        return StrUtil.equalsIgnoreCase(message.getChatType(), "p2p")
                ? ConversationType.PRIVATE
                : ConversationType.GROUP;
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
     * 解析飞书事件时间戳。
     *
     * @param createTime 时间文本
     * @return 时间戳
     */
    private long parseTime(String createTime) {
        try {
            return StrUtil.isBlank(createTime) ? System.currentTimeMillis() : Long.parseLong(createTime);
        } catch (NumberFormatException exception) {
            return System.currentTimeMillis();
        }
    }

    /**
     * 解析发送者标识。
     *
     * @param userId 用户标识对象
     * @return 发送者标识
     */
    private String resolveSenderId(UserId userId) {
        if (userId == null) {
            return null;
        }
        String senderId = firstNonBlank(userId.getOpenId(), userId.getUserId());
        return firstNonBlank(senderId, userId.getUnionId());
    }

    /**
     * 判断飞书配置是否完整。
     *
     * @return 若完整则返回 true
     */
    private boolean isConfigured() {
        return StrUtil.isNotBlank(properties.getAppId()) && StrUtil.isNotBlank(properties.getAppSecret());
    }

    /**
     * 将 JSON 文本解析为对象。
     *
     * @param content JSON 文本
     * @return JSON 对象
     */
    private JSONObject parseObject(String content) {
        try {
            Object node = JSON.parse(content);
            return node instanceof JSONObject ? (JSONObject) node : null;
        } catch (Exception exception) {
            log.debug("Failed to parse Feishu message content: {}", content, exception);
            return null;
        }
    }

    /**
     * 返回多个候选值中第一个非空白值。
     *
     * @param values 候选值列表
     * @return 第一个非空白值
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
