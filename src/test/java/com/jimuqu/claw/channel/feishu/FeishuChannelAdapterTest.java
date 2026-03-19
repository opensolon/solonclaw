package com.jimuqu.claw.channel.feishu;

import com.alibaba.fastjson.JSONObject;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import com.jimuqu.claw.agent.model.ConversationType;
import com.jimuqu.claw.agent.model.InboundEnvelope;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FeishuChannelAdapterTest {
    @Test
    void mapsGroupMessageIntoGroupSession() {
        SolonClawProperties.Feishu properties = new SolonClawProperties.Feishu();
        properties.setGroupAllowFrom(Collections.singletonList("oc-group"));

        FeishuChannelAdapter adapter = new FeishuChannelAdapter(null, null, properties);
        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(messageEvent("oc-group", "ou-1", "group", "text", textContent("群消息")));

        assertNotNull(inboundEnvelope);
        assertEquals(ConversationType.GROUP, inboundEnvelope.getConversationType());
        assertEquals("feishu:group:oc-group", inboundEnvelope.getSessionKey());
        assertEquals("oc-group", inboundEnvelope.getReplyTarget().getConversationId());
    }

    @Test
    void mapsPrivateMessageIntoPrivateSession() {
        SolonClawProperties.Feishu properties = new SolonClawProperties.Feishu();
        properties.setAllowFrom(Collections.singletonList("ou-private"));

        FeishuChannelAdapter adapter = new FeishuChannelAdapter(null, null, properties);
        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(messageEvent("oc-private", "ou-private", "p2p", "text", textContent("私聊消息")));

        assertNotNull(inboundEnvelope);
        assertEquals(ConversationType.PRIVATE, inboundEnvelope.getConversationType());
        assertEquals("feishu:private:oc-private", inboundEnvelope.getSessionKey());
        assertEquals("ou-private", inboundEnvelope.getReplyTarget().getUserId());
    }

    @Test
    void rejectsMessageOutsideAllowListWhenConfigured() {
        SolonClawProperties.Feishu properties = new SolonClawProperties.Feishu();
        properties.setGroupAllowFrom(Collections.singletonList("oc-group"));

        FeishuChannelAdapter adapter = new FeishuChannelAdapter(null, null, properties);

        assertNull(adapter.toInboundEnvelope(messageEvent("oc-other", "ou-1", "group", "text", textContent("未授权群"))));
    }

    @Test
    void extractsPlainTextFromPostMessage() {
        SolonClawProperties.Feishu properties = new SolonClawProperties.Feishu();

        FeishuChannelAdapter adapter = new FeishuChannelAdapter(null, null, properties);
        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(messageEvent("oc-post", "ou-2", "group", "post", postContent("标题", "正文")));

        assertNotNull(inboundEnvelope);
        assertEquals("标题\n正文", inboundEnvelope.getContent());
    }

    private P2MessageReceiveV1 messageEvent(String chatId, String openId, String chatType, String messageType, String content) {
        P2MessageReceiveV1 event = new P2MessageReceiveV1();
        P2MessageReceiveV1Data data = new P2MessageReceiveV1Data();
        EventMessage message = new EventMessage();
        message.setMessageId("msg-" + chatId);
        message.setChatId(chatId);
        message.setChatType(chatType);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setCreateTime(String.valueOf(System.currentTimeMillis()));
        data.setMessage(message);

        UserId userId = new UserId();
        userId.setOpenId(openId);
        EventSender sender = new EventSender();
        sender.setSenderId(userId);
        data.setSender(sender);

        event.setEvent(data);
        return event;
    }

    private String textContent(String text) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("text", text);
        return jsonObject.toJSONString();
    }

    private String postContent(String title, String text) {
        JSONObject textNode = new JSONObject();
        textNode.put("tag", "text");
        textNode.put("text", text);

        JSONObject zhCn = new JSONObject();
        zhCn.put("title", title);
        zhCn.put("content", Collections.singletonList(Collections.singletonList(textNode)));

        JSONObject post = new JSONObject();
        post.put("zh_cn", zhCn);
        return post.toJSONString();
    }
}
