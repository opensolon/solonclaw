package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.service.InboundMessageHandler;
import com.jimuqu.solon.claw.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class FeishuWebsocketInboundTest {
    @Test
    void shouldConvertWebsocketMessageEventToGatewayMessage() {
        AppConfig.ChannelConfig channelConfig = new AppConfig.ChannelConfig();
        channelConfig.setEnabled(true);
        channelConfig.setAppId("app");
        channelConfig.setAppSecret("secret");
        FeishuChannelAdapter adapter =
                new FeishuChannelAdapter(
                        channelConfig, new AttachmentCacheService(new AppConfig()));

        AtomicReference<GatewayMessage> captured = new AtomicReference<GatewayMessage>();
        adapter.setInboundMessageHandler(
                new InboundMessageHandler() {
                    @Override
                    public void handle(GatewayMessage message) {
                        captured.set(message);
                    }
                });

        EventMessage eventMessage =
                EventMessage.newBuilder()
                        .messageId("om_ws_1")
                        .chatId("oc_chat")
                        .chatType("p2p")
                        .messageType("text")
                        .content("{\"text\":\"hello websocket\"}")
                        .build();
        UserId userId =
                UserId.newBuilder().openId("ou_user").userId("u_user").unionId("on_union").build();
        EventSender sender = EventSender.newBuilder().senderId(userId).build();
        P2MessageReceiveV1Data data = new P2MessageReceiveV1Data();
        data.setMessage(eventMessage);
        data.setSender(sender);

        adapter.handleWebsocketEvent(data);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getChatId()).isEqualTo("oc_chat");
        assertThat(captured.get().getUserId()).isEqualTo("ou_user");
        assertThat(captured.get().getText()).isEqualTo("hello websocket");
        assertThat(captured.get().getThreadId()).isEqualTo("om_ws_1");
    }
}
