package com.jimuqu.claw.channel.weixin;

import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.channel.weixin.adapter.WeixinChannelAdapter;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;
import com.jimuqu.claw.channel.weixin.model.WeixinMessage;
import com.jimuqu.claw.channel.weixin.model.WeixinMessageItem;
import com.jimuqu.claw.channel.weixin.model.WeixinTextItem;
import com.jimuqu.claw.channel.weixin.model.WeixinVoiceItem;
import com.jimuqu.claw.config.props.WeixinProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeixinChannelAdapterTest {
    @Test
    void mapsTextMessageIntoPrivateSession() {
        WeixinChannelAdapter adapter = new WeixinChannelAdapter(null, null, null, null, new WeixinProperties());
        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(account("acc-1"), textMessage("user-1", "你好", "ctx-1", 1));

        assertNotNull(inboundEnvelope);
        assertEquals(ConversationType.PRIVATE, inboundEnvelope.getConversationType());
        assertEquals("weixin:acc-1:private:user-1", inboundEnvelope.getSessionKey());
        assertEquals("acc-1", inboundEnvelope.getReplyTarget().getChannelInstanceId());
        assertEquals("ctx-1", inboundEnvelope.getReplyTarget().getContextToken());
    }

    @Test
    void rejectsMessageOutsideAllowListWhenConfigured() {
        WeixinProperties properties = new WeixinProperties();
        properties.getAllowFrom().add("user-allowed");
        WeixinChannelAdapter adapter = new WeixinChannelAdapter(null, null, null, null, properties);

        assertNull(adapter.toInboundEnvelope(account("acc-1"), textMessage("user-other", "你好", "ctx-1", 1)));
    }

    @Test
    void ignoresBotMessage() {
        WeixinChannelAdapter adapter = new WeixinChannelAdapter(null, null, null, null, new WeixinProperties());

        assertNull(adapter.toInboundEnvelope(account("acc-1"), textMessage("user-1", "机器人回声", "ctx-1", 2)));
    }

    @Test
    void fallsBackToVoiceTranscriptWhenTextIsMissing() {
        WeixinChannelAdapter adapter = new WeixinChannelAdapter(null, null, null, null, new WeixinProperties());
        WeixinMessage message = new WeixinMessage();
        message.setFrom_user_id("user-1");
        message.setContext_token("ctx-1");
        message.setMessage_type(1);
        WeixinMessageItem voiceItem = new WeixinMessageItem();
        voiceItem.setType(3);
        WeixinVoiceItem voice = new WeixinVoiceItem();
        voice.setText("这是语音转写");
        voiceItem.setVoice_item(voice);
        message.setItem_list(Arrays.asList(voiceItem));

        InboundEnvelope inboundEnvelope = adapter.toInboundEnvelope(account("acc-1"), message);

        assertNotNull(inboundEnvelope);
        assertEquals("这是语音转写", inboundEnvelope.getContent());
    }

    private WeixinAccount account(String accountId) {
        WeixinAccount account = new WeixinAccount();
        account.setAccountId(accountId);
        return account;
    }

    private WeixinMessage textMessage(String fromUserId, String text, String contextToken, int messageType) {
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(123L);
        message.setFrom_user_id(fromUserId);
        message.setContext_token(contextToken);
        message.setMessage_type(messageType);

        WeixinMessageItem textItem = new WeixinMessageItem();
        textItem.setType(1);
        WeixinTextItem body = new WeixinTextItem();
        body.setText(text);
        textItem.setText_item(body);
        message.setItem_list(Arrays.asList(textItem));
        return message;
    }
}
