package com.jimuqu.solon.claw.gateway.platform.yuanbao;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.noear.snack4.ONode;

/** 腾讯元宝 Bot 渠道适配器。协议层保留 JSON/REST 可测边界，媒体只做传输与附件感知。 */
public class YuanbaoChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String DEFAULT_WS_URL = "wss://bot-wss.yuanbao.tencent.com/wss/connection";
    private static final String DEFAULT_API_DOMAIN = "https://bot.yuanbao.tencent.com";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AppConfig.ChannelConfig config;
    private final OkHttpClient client;
    private volatile WebSocket webSocket;
    private ExecutorService callbackExecutor;

    public YuanbaoChannelAdapter(AppConfig.ChannelConfig config) {
        super(PlatformType.YUANBAO, config);
        this.config = config;
        this.client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
        setConnectionMode("websocket");
        setFeatures("text", "attachments", "media-transfer", "platform-asr-text");
        setSetupState(config != null && config.isEnabled() ? "configured" : "disabled");
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            setSetupState("disabled");
            setDetail("disabled");
            return false;
        }
        if (StrUtil.isBlank(config.getAppId()) || StrUtil.isBlank(config.getAppSecret())) {
            setSetupState("missing_config");
            setMissingConfig(
                    "solonclaw.channels.yuanbao.appId", "solonclaw.channels.yuanbao.appSecret");
            setLastError("yuanbao_missing_credentials", "missing appId/appSecret");
            setDetail("missing appId/appSecret");
            return false;
        }
        try {
            callbackExecutor = Executors.newSingleThreadExecutor();
            String wsUrl = StrUtil.blankToDefault(config.getWebsocketUrl(), DEFAULT_WS_URL);
            Request request =
                    new Request.Builder()
                            .url(wsUrl)
                            .header("X-App-Id", config.getAppId())
                            .header("X-Signature", sign(String.valueOf(System.currentTimeMillis())))
                            .build();
            webSocket = client.newWebSocket(request, new Listener());
            setConnected(true);
            setSetupState("connected");
            setMissingConfig(new String[0]);
            clearLastError();
            setDetail("websocket connecting");
            return true;
        } catch (Exception e) {
            setConnected(false);
            setSetupState("error");
            setLastError("yuanbao_connect_failed", e.getMessage());
            setDetail("connect failed: " + e.getMessage());
            log.warn("[YUANBAO] connect failed: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "normal");
            webSocket = null;
        }
        if (callbackExecutor != null) {
            callbackExecutor.shutdownNow();
            callbackExecutor = null;
        }
        setConnected(false);
        setDetail("disconnected");
    }

    @Override
    public void send(DeliveryRequest request) throws Exception {
        if (StrUtil.isBlank(request.getChatId())) {
            throw new IllegalArgumentException("Yuanbao chatId is required");
        }
        if (StrUtil.isNotBlank(request.getText())) {
            ONode body =
                    baseSendBody(request)
                            .set("msg_type", "text")
                            .getOrNew("text")
                            .set("content", request.getText())
                            .parent();
            sendPayload(body);
        }
        if (request.getAttachments() != null) {
            for (MessageAttachment attachment : request.getAttachments()) {
                sendAttachment(request, attachment);
            }
        }
    }

    private void sendAttachment(DeliveryRequest request, MessageAttachment attachment)
            throws Exception {
        File file = new File(attachment.getLocalPath());
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "Yuanbao attachment file not found: " + attachment.getLocalPath());
        }
        String kind =
                AttachmentCacheService.normalizeKind(
                        attachment.getKind(),
                        attachment.getOriginalName(),
                        attachment.getMimeType());
        ONode body =
                baseSendBody(request)
                        .set("msg_type", kind)
                        .getOrNew(kind)
                        .set(
                                "file_name",
                                StrUtil.blankToDefault(
                                        attachment.getOriginalName(), file.getName()))
                        .set(
                                "mime_type",
                                AttachmentCacheService.normalizeMimeType(
                                        attachment.getMimeType(), file.getName()))
                        .set("file_data", Base64.encode(FileUtil.readBytes(file)))
                        .parent();
        sendPayload(body);
    }

    private ONode baseSendBody(DeliveryRequest request) {
        return new ONode()
                .set("request_id", UUID.randomUUID().toString())
                .set("bot_id", config.getBotId())
                .set("chat_id", request.getChatId())
                .set(
                        "chat_type",
                        StrUtil.blankToDefault(
                                request.getChatType(), GatewayBehaviorConstants.CHAT_TYPE_DM))
                .set("reply_to", request.getThreadId());
    }

    private void sendPayload(ONode body) throws Exception {
        String payload = body.toJson();
        if (webSocket != null && isConnected()) {
            if (!webSocket.send(payload)) {
                throw new IllegalStateException("Yuanbao websocket send failed");
            }
            return;
        }
        postJson("/openapi/bot/messages", payload);
    }

    private ONode postJson(String path, String body) throws Exception {
        Request request =
                new Request.Builder()
                        .url(apiDomain() + path)
                        .header("X-App-Id", config.getAppId())
                        .header("X-Signature", sign(body))
                        .post(RequestBody.create(JSON, body))
                        .build();
        Response response = client.newCall(request).execute();
        try {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Yuanbao HTTP " + response.code() + ": " + raw);
            }
            return StrUtil.isBlank(raw) ? new ONode() : ONode.ofJson(raw);
        } finally {
            response.close();
        }
    }

    private String apiDomain() {
        return StrUtil.blankToDefault(config.getApiDomain(), DEFAULT_API_DOMAIN);
    }

    private String sign(String payload) {
        HMac hmac =
                new HMac(
                        HmacAlgorithm.HmacSHA256,
                        StrUtil.nullToEmpty(config.getAppSecret())
                                .getBytes(StandardCharsets.UTF_8));
        return hmac.digestHex(StrUtil.nullToEmpty(payload));
    }

    private class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            setConnected(true);
            setSetupState("connected");
            setDetail("websocket connected");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            dispatchInbound(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            dispatchInbound(bytes.utf8());
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            YuanbaoChannelAdapter.this.webSocket = null;
            setConnected(false);
            setSetupState("error");
            setLastError("yuanbao_websocket_failure", t == null ? "unknown" : t.getMessage());
            setDetail("websocket disconnected");
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            YuanbaoChannelAdapter.this.webSocket = null;
            setConnected(false);
            setSetupState("disconnected");
            setDetail("websocket closed: " + code + " " + reason);
        }
    }

    protected void dispatchInbound(final String raw) {
        if (callbackExecutor == null || inboundMessageHandler() == null || StrUtil.isBlank(raw)) {
            return;
        }
        callbackExecutor.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        GatewayMessage message = toGatewayMessage(raw);
                        if (message == null) {
                            return;
                        }
                        try {
                            inboundMessageHandler().handle(message);
                        } catch (Exception e) {
                            log.warn("[YUANBAO] inbound dispatch failed: {}", e.getMessage(), e);
                        }
                    }
                });
    }

    protected GatewayMessage toGatewayMessage(String raw) {
        ONode node = ONode.ofJson(raw);
        ONode body = node.get("body");
        if (body == null || body.isNull()) {
            body = node;
        }
        String chatId =
                firstNonBlank(
                        body.get("chat_id").getString(),
                        body.get("group_id").getString(),
                        body.get("openid").getString());
        String userId =
                firstNonBlank(
                        body.get("user_id").getString(),
                        body.get("from_openid").getString(),
                        body.get("from").getString());
        String chatType =
                StrUtil.blankToDefault(
                        body.get("chat_type").getString(), GatewayBehaviorConstants.CHAT_TYPE_DM);
        String text =
                firstNonBlank(
                        body.get("text").get("content").getString(),
                        body.get("content").getString(),
                        body.get("voice").get("text").getString(),
                        body.get("asr_text").getString());
        if (StrUtil.isBlank(chatId)
                || StrUtil.isBlank(text)
                || !allowInbound(chatType, chatId, userId)) {
            return null;
        }
        GatewayMessage message =
                new GatewayMessage(PlatformType.YUANBAO, chatId, userId, text.trim());
        message.setChatType(chatType);
        message.setChatName(chatId);
        message.setUserName(userId);
        message.setThreadId(
                firstNonBlank(body.get("message_id").getString(), body.get("msg_id").getString()));
        return message;
    }

    private boolean allowInbound(String chatType, String chatId, String userId) {
        if (config.isAllowAllUsers()) {
            return true;
        }
        if (GatewayBehaviorConstants.CHAT_TYPE_GROUP.equalsIgnoreCase(chatType)) {
            String policy =
                    StrUtil.blankToDefault(
                                    config.getGroupPolicy(),
                                    GatewayBehaviorConstants.GROUP_POLICY_OPEN)
                            .toLowerCase();
            if (GatewayBehaviorConstants.GROUP_POLICY_DISABLED.equals(policy)) {
                return false;
            }
            return !GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST.equals(policy)
                    || contains(config.getGroupAllowedUsers(), chatId);
        }
        String policy =
                StrUtil.blankToDefault(
                                config.getDmPolicy(), GatewayBehaviorConstants.DM_POLICY_OPEN)
                        .toLowerCase();
        if (GatewayBehaviorConstants.DM_POLICY_DISABLED.equals(policy)) {
            return false;
        }
        return !GatewayBehaviorConstants.DM_POLICY_ALLOWLIST.equals(policy)
                || contains(config.getAllowedUsers(), userId);
    }

    private boolean contains(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            String normalized = StrUtil.nullToEmpty(value).trim();
            if ("*".equals(normalized) || target.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
