package com.jimuqu.claw.web;

import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import com.jimuqu.claw.channel.weixin.model.WeixinGetUpdatesResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrCodeResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStartResult;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStatusResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrWaitResult;
import com.jimuqu.claw.channel.weixin.service.WeixinAccountStoreService;
import com.jimuqu.claw.channel.weixin.service.WeixinApiGateway;
import com.jimuqu.claw.channel.weixin.service.WeixinLoginService;
import com.jimuqu.claw.config.props.WeixinProperties;
import com.jimuqu.claw.web.controller.WeixinChannelController;
import com.jimuqu.claw.web.dto.WeixinAccountSummary;
import com.jimuqu.claw.web.dto.WeixinLoginStartRequest;
import com.jimuqu.claw.web.dto.WeixinLoginWaitRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeixinChannelControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void startsAndCompletesLoginThenListsAccounts() {
        WeixinAccountStoreService storeService = new WeixinAccountStoreService(new AgentWorkspaceService(tempDir.toString()));
        WeixinProperties properties = new WeixinProperties();
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
                WeixinQrCodeResponse response = new WeixinQrCodeResponse();
                response.setQrcode("qr-1");
                response.setQrcode_img_content("https://example.com/qr.png");
                return response;
            }

            @Override
            public WeixinQrStatusResponse getQrCodeStatus(String baseUrl, String qrcode, int timeoutMs) {
                WeixinQrStatusResponse response = new WeixinQrStatusResponse();
                response.setStatus("confirmed");
                response.setBot_token("token-1");
                response.setIlink_bot_id("abc@im.bot");
                response.setBaseurl("https://ilinkai.weixin.qq.com");
                response.setIlink_user_id("wxid-user-1");
                return response;
            }
        };
        WeixinLoginService loginService = new WeixinLoginService(apiGateway, storeService, properties);
        WeixinChannelController controller = new WeixinChannelController(loginService, storeService);

        WeixinLoginStartRequest startRequest = new WeixinLoginStartRequest();
        startRequest.setSessionKey("session-1");

        WeixinQrStartResult startResult = controller.startLogin(startRequest);

        assertEquals("session-1", startResult.getSessionKey());
        assertEquals("https://example.com/qr.png", startResult.getQrCodeUrl());

        WeixinLoginWaitRequest waitRequest = new WeixinLoginWaitRequest();
        waitRequest.setSessionKey("session-1");
        WeixinQrWaitResult waitResult = controller.waitLogin(waitRequest);

        assertTrue(waitResult.isConnected());
        assertEquals("abc-im.bot", waitResult.getAccountId());

        List<WeixinAccountSummary> accounts = controller.listAccounts();
        assertEquals(1, accounts.size());
        assertEquals("abc-im.bot", accounts.get(0).getAccountId());
        assertEquals("wxid-user-1", accounts.get(0).getUserId());
    }

    @Test
    void rejectsBlankSessionKeyWhenWaitingLogin() {
        WeixinAccountStoreService storeService = new WeixinAccountStoreService(new AgentWorkspaceService(tempDir.toString()));
        WeixinLoginService loginService = new WeixinLoginService(new NoopWeixinApiGateway(), storeService, new WeixinProperties());
        WeixinChannelController controller = new WeixinChannelController(loginService, storeService);
        WeixinLoginWaitRequest waitRequest = new WeixinLoginWaitRequest();
        waitRequest.setSessionKey("   ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> controller.waitLogin(waitRequest));

        assertEquals("sessionKey 不能为空", exception.getMessage());
    }

    private static class NoopWeixinApiGateway implements WeixinApiGateway {
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
    }
}
