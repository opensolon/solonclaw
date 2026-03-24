package com.jimuqu.claw.channel.weixin;

import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;
import com.jimuqu.claw.channel.weixin.model.WeixinGetUpdatesResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrCodeResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStatusResponse;
import com.jimuqu.claw.channel.weixin.sender.WeixinRobotSender;
import com.jimuqu.claw.channel.weixin.service.WeixinAccountStoreService;
import com.jimuqu.claw.channel.weixin.service.WeixinApiGateway;
import com.jimuqu.claw.config.props.WeixinProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeixinRobotSenderTest {
    @TempDir
    Path tempDir;

    @Test
    void sendsTextUsingAccountAndContextToken() {
        WeixinAccountStoreService accountStoreService = new WeixinAccountStoreService(new AgentWorkspaceService(tempDir.toString()));
        WeixinAccount account = new WeixinAccount();
        account.setAccountId("acc-1");
        account.setBaseUrl("https://ilinkai.weixin.qq.com");
        account.setToken("token-1");
        accountStoreService.saveAccount(account);

        AtomicReference<String> capturedText = new AtomicReference<String>();
        AtomicReference<String> capturedContextToken = new AtomicReference<String>();
        WeixinApiGateway apiGateway = new WeixinApiGateway() {
            @Override
            public WeixinGetUpdatesResponse getUpdates(String baseUrl, String token, String cursor, int timeoutMs) {
                return null;
            }

            @Override
            public void sendTextMessage(String baseUrl, String token, String toUserId, String contextToken, String text) {
                capturedText.set(text);
                capturedContextToken.set(contextToken);
            }

            @Override
            public WeixinQrCodeResponse getBotQrCode(String baseUrl, String botType) {
                return null;
            }

            @Override
            public WeixinQrStatusResponse getQrCodeStatus(String baseUrl, String qrcode, int timeoutMs) {
                return null;
            }
        };

        WeixinRobotSender sender = new WeixinRobotSender(apiGateway, accountStoreService, new WeixinProperties());
        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setContent("你好，微信");
        outboundEnvelope.setReplyTarget(new ReplyTarget(ChannelType.WEIXIN, ConversationType.PRIVATE, "user-1", "user-1", "acc-1", "ctx-1"));

        DeliveryResult result = sender.send(outboundEnvelope);

        assertTrue(result.isDelivered());
        assertEquals("你好，微信", capturedText.get());
        assertEquals("ctx-1", capturedContextToken.get());
    }

    @Test
    void rejectsMessageWhenContextTokenMissing() {
        WeixinAccountStoreService accountStoreService = new WeixinAccountStoreService(new AgentWorkspaceService(tempDir.toString()));
        WeixinAccount account = new WeixinAccount();
        account.setAccountId("acc-1");
        account.setBaseUrl("https://ilinkai.weixin.qq.com");
        account.setToken("token-1");
        accountStoreService.saveAccount(account);

        WeixinApiGateway apiGateway = new WeixinApiGateway() {
            @Override
            public WeixinGetUpdatesResponse getUpdates(String baseUrl, String token, String cursor, int timeoutMs) {
                return null;
            }

            @Override
            public void sendTextMessage(String baseUrl, String token, String toUserId, String contextToken, String text) {
            }

            @Override
            public WeixinQrCodeResponse getBotQrCode(String baseUrl, String botType) {
                return null;
            }

            @Override
            public WeixinQrStatusResponse getQrCodeStatus(String baseUrl, String qrcode, int timeoutMs) {
                return null;
            }
        };

        WeixinRobotSender sender = new WeixinRobotSender(apiGateway, accountStoreService, new WeixinProperties());
        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setContent("你好，微信");
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.WEIXIN, ConversationType.PRIVATE, "user-1", "user-1");
        replyTarget.setChannelInstanceId("acc-1");
        outboundEnvelope.setReplyTarget(replyTarget);

        DeliveryResult result = sender.send(outboundEnvelope);

        assertFalse(result.isDelivered());
        assertEquals("contextToken is missing", result.getMessage());
    }
}
