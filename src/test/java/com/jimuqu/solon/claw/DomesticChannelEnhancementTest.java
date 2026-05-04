package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.solon.claw.gateway.platform.qqbot.QQBotChannelAdapter;
import com.jimuqu.solon.claw.gateway.platform.yuanbao.YuanbaoChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.lark.oapi.core.request.EventReq;
import org.junit.jupiter.api.Test;

public class DomesticChannelEnhancementTest {
    @Test
    void shouldParseQqbotInboundTextAndPlatformAsrText() {
        AppConfig config = new AppConfig();
        config.getChannels().getQqbot().setAllowAllUsers(true);
        TestQQBotAdapter adapter = new TestQQBotAdapter(config);

        GatewayMessage text =
                adapter.parse(
                        "{\"t\":\"C2C_MESSAGE_CREATE\",\"d\":{\"id\":\"m1\",\"openid\":\"user-a\",\"content\":\"hello\"}}");
        GatewayMessage voice =
                adapter.parse(
                        "{\"t\":\"GROUP_AT_MESSAGE_CREATE\",\"d\":{\"id\":\"m2\",\"group_openid\":\"group-a\",\"author\":{\"user_openid\":\"user-a\"},\"asr_refer_text\":\"语音文本\"}}");

        assertThat(text.getText()).isEqualTo("hello");
        assertThat(text.getChatId()).isEqualTo("user-a");
        assertThat(voice.getChatType()).isEqualTo("group");
        assertThat(voice.getText()).isEqualTo("语音文本");
    }

    @Test
    void shouldParseYuanbaoInboundTextAndPlatformAsrText() {
        AppConfig config = new AppConfig();
        config.getChannels().getYuanbao().setAllowAllUsers(true);
        TestYuanbaoAdapter adapter = new TestYuanbaoAdapter(config);

        GatewayMessage message =
                adapter.parse(
                        "{\"body\":{\"chat_id\":\"room-a\",\"user_id\":\"user-a\",\"chat_type\":\"group\",\"voice\":{\"text\":\"平台转写\"},\"message_id\":\"m1\"}}");

        assertThat(message.getText()).isEqualTo("平台转写");
        assertThat(message.getChatId()).isEqualTo("room-a");
        assertThat(message.getThreadId()).isEqualTo("m1");
    }

    @Test
    void shouldParseFeishuDocumentCommentEvent() {
        AppConfig config = new AppConfig();
        config.getChannels().getFeishu().setCommentEnabled(true);
        config.getChannels().getFeishu().setAllowAllUsers(true);
        TestFeishuAdapter adapter = new TestFeishuAdapter(config);
        EventReq req = new EventReq();
        req.setPlain(
                "{\"event\":{\"comment_id\":\"c1\",\"reply_id\":\"r1\",\"notice_meta\":{\"notice_type\":\"add_reply\",\"file_token\":\"ft\",\"file_type\":\"docx\",\"from_user_id\":{\"open_id\":\"ou_1\"}},\"reply_content\":{\"elements\":[{\"type\":\"text_run\",\"text_run\":{\"text\":\"请总结这一段\"}}]}}}");

        GatewayMessage message = adapter.parseComment(req);

        assertThat(message.getChatId()).startsWith("comment|docx|ft|c1|r1|");
        assertThat(message.getSourceKeyOverride()).isEqualTo("FEISHU_COMMENT:docx:ft:c1");
        assertThat(message.getText()).contains("请总结这一段");
    }

    private static class TestQQBotAdapter extends QQBotChannelAdapter {
        private TestQQBotAdapter(AppConfig config) {
            super(config.getChannels().getQqbot(), new AttachmentCacheService(config));
        }

        private GatewayMessage parse(String raw) {
            return toGatewayMessage(raw);
        }
    }

    private static class TestYuanbaoAdapter extends YuanbaoChannelAdapter {
        private TestYuanbaoAdapter(AppConfig config) {
            super(config.getChannels().getYuanbao());
        }

        private GatewayMessage parse(String raw) {
            return toGatewayMessage(raw);
        }
    }

    private static class TestFeishuAdapter extends FeishuChannelAdapter {
        private TestFeishuAdapter(AppConfig config) {
            super(config.getChannels().getFeishu(), new AttachmentCacheService(config));
        }

        private GatewayMessage parseComment(EventReq req) {
            return toCommentGatewayMessage(req);
        }
    }
}
