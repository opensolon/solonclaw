package com.jimuqu.claw.channel.dingtalk;

import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.channel.dingtalk.adapter.DingTalkChannelAdapter;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证钉钉回调消息到统一模型的映射逻辑。
 */
class DingTalkChannelAdapterTest {
    /**
     * 验证群消息会映射到群会话。
     */
    @Test
    void mapsGroupMessageIntoGroupSession() {
        SolonClawProperties.DingTalk properties = new SolonClawProperties.DingTalk();
        properties.setGroupAllowFrom(Collections.singletonList("cid-group"));

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(null, null, null, properties);
        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(groupMessage("cid-group", "staff-1", "群消息"));

        assertEquals(ConversationType.GROUP, inboundEnvelope.getConversationType());
        assertEquals("dingtalk:group:cid-group", inboundEnvelope.getSessionKey());
        assertEquals("cid-group", inboundEnvelope.getReplyTarget().getConversationId());
    }

    /**
     * 验证私聊消息会映射到私聊会话。
     */
    @Test
    void mapsPrivateMessageIntoPrivateSession() {
        SolonClawProperties.DingTalk properties = new SolonClawProperties.DingTalk();
        properties.setAllowFrom(Collections.singletonList("staff-private"));

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(null, null, null, properties);
        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(privateMessage("cid-private", "staff-private", "私聊消息"));

        assertEquals(ConversationType.PRIVATE, inboundEnvelope.getConversationType());
        assertEquals("dingtalk:private:cid-private", inboundEnvelope.getSessionKey());
        assertEquals("staff-private", inboundEnvelope.getReplyTarget().getUserId());
    }

    /**
     * 验证白名单为空时默认放行群消息。
     */
    @Test
    void allowsGroupMessageWhenAllowListIsEmpty() {
        SolonClawProperties.DingTalk properties = new SolonClawProperties.DingTalk();

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(null, null, null, properties);
        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(groupMessage("cid-other", "staff-1", "未授权群"));

        assertNotNull(inboundEnvelope);
        assertEquals(ConversationType.GROUP, inboundEnvelope.getConversationType());
    }

    /**
     * 验证配置白名单后，未命中的消息会被拒绝。
     */
    @Test
    void rejectsMessageOutsideAllowListWhenConfigured() {
        SolonClawProperties.DingTalk properties = new SolonClawProperties.DingTalk();
        properties.setGroupAllowFrom(Collections.singletonList("cid-group"));

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(null, null, null, properties);

        assertNull(adapter.toInboundEnvelope(groupMessage("cid-other", "staff-1", "未授权群")));
    }

    /**
     * 构造一条群消息。
     *
     * @param conversationId 会话标识
     * @param senderStaffId 发送者标识
     * @param content 文本内容
     * @return 钉钉消息
     */
    private ChatbotMessage groupMessage(String conversationId, String senderStaffId, String content) {
        ChatbotMessage message = baseMessage(conversationId, senderStaffId, content);
        message.setConversationType("2");
        return message;
    }

    /**
     * 构造一条私聊消息。
     *
     * @param conversationId 会话标识
     * @param senderStaffId 发送者标识
     * @param content 文本内容
     * @return 钉钉消息
     */
    private ChatbotMessage privateMessage(String conversationId, String senderStaffId, String content) {
        ChatbotMessage message = baseMessage(conversationId, senderStaffId, content);
        message.setConversationType("1");
        return message;
    }

    /**
     * 构造一条基础消息对象。
     *
     * @param conversationId 会话标识
     * @param senderStaffId 发送者标识
     * @param content 文本内容
     * @return 钉钉消息
     */
    private ChatbotMessage baseMessage(String conversationId, String senderStaffId, String content) {
        ChatbotMessage message = new ChatbotMessage();
        message.setConversationId(conversationId);
        message.setSenderStaffId(senderStaffId);
        message.setMsgId("msg-" + conversationId);
        message.setCreateAt(System.currentTimeMillis());
        MessageContent text = new MessageContent();
        text.setContent(content);
        message.setText(text);
        return message;
    }
}

