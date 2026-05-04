package com.jimuqu.solon.claw.gateway.platform.qqbot;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.BoundedAttachmentIO;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import java.io.File;
import java.util.List;
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

/** QQBot 官方 API v2 适配器。当前覆盖文本、媒体传输与附件感知主链。 */
public class QQBotChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    private static final String DEFAULT_API_DOMAIN = "https://api.sgroup.qq.com";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AppConfig.ChannelConfig config;
    private final AttachmentCacheService attachmentCacheService;
    private final OkHttpClient client;
    private volatile WebSocket webSocket;
    private volatile String accessToken;
    private volatile long accessTokenExpireAt;
    private ExecutorService callbackExecutor;

    public QQBotChannelAdapter(
            AppConfig.ChannelConfig config, AttachmentCacheService attachmentCacheService) {
        super(PlatformType.QQBOT, config);
        this.config = config;
        this.attachmentCacheService = attachmentCacheService;
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
        if (StrUtil.isBlank(config.getAppId()) || StrUtil.isBlank(config.getClientSecret())) {
            setSetupState("missing_config");
            setMissingConfig(
                    "solonclaw.channels.qqbot.appId", "solonclaw.channels.qqbot.clientSecret");
            setLastError("qqbot_missing_credentials", "missing appId/clientSecret");
            setDetail("missing appId/clientSecret");
            return false;
        }
        try {
            refreshAccessTokenIfNecessary();
            String gateway = StrUtil.blankToDefault(config.getWebsocketUrl(), fetchGatewayUrl());
            if (StrUtil.isBlank(gateway)) {
                setConnected(true);
                setSetupState("configured");
                setDetail("REST ready; websocket gateway unavailable");
                return true;
            }
            callbackExecutor = Executors.newSingleThreadExecutor();
            Request request =
                    new Request.Builder()
                            .url(gateway)
                            .header("Authorization", "QQBot " + accessToken)
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
            setLastError("qqbot_connect_failed", e.getMessage());
            setDetail("connect failed: " + e.getMessage());
            log.warn("[QQBOT] connect failed: {}", e.getMessage(), e);
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
            throw new IllegalArgumentException("QQBot chatId is required");
        }
        refreshAccessTokenIfNecessary();
        if (StrUtil.isNotBlank(request.getText())) {
            postJson(
                    resolveMessagePath(request),
                    buildTextBody(request.getText(), request.getThreadId()).toJson());
        }
        List<MessageAttachment> attachments = request.getAttachments();
        if (attachments != null) {
            for (MessageAttachment attachment : attachments) {
                sendAttachment(request, attachment);
            }
        }
    }

    private ONode buildTextBody(String text, String replyTo) {
        ONode body =
                new ONode()
                        .set("msg_type", config.isMarkdownSupport() ? 2 : 0)
                        .set("content", text)
                        .set("msg_seq", Long.valueOf(System.currentTimeMillis()));
        if (StrUtil.isNotBlank(replyTo)) {
            body.set("msg_id", replyTo);
        }
        return body;
    }

    private void sendAttachment(DeliveryRequest request, MessageAttachment attachment)
            throws Exception {
        File file = new File(attachment.getLocalPath());
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "QQBot attachment file not found: " + attachment.getLocalPath());
        }
        String kind =
                AttachmentCacheService.normalizeKind(
                        attachment.getKind(),
                        attachment.getOriginalName(),
                        attachment.getMimeType());
        int fileType =
                "image".equals(kind)
                        ? 1
                        : ("video".equals(kind) ? 2 : ("voice".equals(kind) ? 3 : 4));
        ONode uploadBody =
                new ONode()
                        .set("file_type", Integer.valueOf(fileType))
                        .set("file_data", Base64.encode(FileUtil.readBytes(file)))
                        .set(
                                "file_name",
                                StrUtil.blankToDefault(
                                        attachment.getOriginalName(), file.getName()))
                        .set("srv_send_msg", Boolean.FALSE);
        ONode uploaded = postJson(resolveUploadPath(request), uploadBody.toJson());
        String fileInfo = uploaded.get("file_info").getString();
        if (StrUtil.isBlank(fileInfo)) {
            fileInfo = uploaded.get("data").get("file_info").getString();
        }
        if (StrUtil.isBlank(fileInfo)) {
            throw new IllegalStateException(
                    "QQBot media upload missing file_info: " + uploaded.toJson());
        }
        ONode body =
                new ONode()
                        .set("msg_type", Integer.valueOf(7))
                        .getOrNew("media")
                        .set("file_info", fileInfo)
                        .parent()
                        .set("msg_seq", Long.valueOf(System.currentTimeMillis()));
        postJson(resolveMessagePath(request), body.toJson());
    }

    private String resolveMessagePath(DeliveryRequest request) {
        if (GatewayBehaviorConstants.CHAT_TYPE_GROUP.equalsIgnoreCase(request.getChatType())) {
            return "/v2/groups/" + request.getChatId() + "/messages";
        }
        if ("guild".equalsIgnoreCase(request.getChatType())) {
            return "/channels/" + request.getChatId() + "/messages";
        }
        return "/v2/users/" + request.getChatId() + "/messages";
    }

    private String resolveUploadPath(DeliveryRequest request) {
        if (GatewayBehaviorConstants.CHAT_TYPE_GROUP.equalsIgnoreCase(request.getChatType())) {
            return "/v2/groups/" + request.getChatId() + "/files";
        }
        return "/v2/users/" + request.getChatId() + "/files";
    }

    private String apiDomain() {
        return StrUtil.blankToDefault(config.getApiDomain(), DEFAULT_API_DOMAIN);
    }

    private synchronized void refreshAccessTokenIfNecessary() throws Exception {
        long now = System.currentTimeMillis();
        if (StrUtil.isNotBlank(accessToken) && accessTokenExpireAt > now + 60000L) {
            return;
        }
        String body =
                new ONode()
                        .set("appId", config.getAppId())
                        .set("clientSecret", config.getClientSecret())
                        .toJson();
        Request request =
                new Request.Builder().url(TOKEN_URL).post(RequestBody.create(JSON, body)).build();
        Response response = client.newCall(request).execute();
        try {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("QQBot token HTTP " + response.code());
            }
            ONode node = ONode.ofJson(response.body() == null ? "{}" : response.body().string());
            accessToken =
                    firstNonBlank(
                            node.get("access_token").getString(),
                            node.get("data").get("access_token").getString());
            if (StrUtil.isBlank(accessToken)) {
                throw new IllegalStateException(
                        "QQBot token response missing access_token: " + node.toJson());
            }
            long expires = Math.max(60L, node.get("expires_in").getLong(7200L));
            accessTokenExpireAt = now + expires * 1000L;
        } finally {
            response.close();
        }
    }

    private String fetchGatewayUrl() {
        try {
            ONode node = getJson("/gateway");
            return firstNonBlank(
                    node.get("url").getString(), node.get("data").get("url").getString());
        } catch (Exception e) {
            log.debug("[QQBOT] gateway lookup failed: {}", e.getMessage(), e);
            return "";
        }
    }

    private ONode getJson(String path) throws Exception {
        Request request =
                new Request.Builder()
                        .url(apiDomain() + path)
                        .header("Authorization", "QQBot " + accessToken)
                        .build();
        Response response = client.newCall(request).execute();
        try {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "QQBot HTTP " + response.code() + ": " + safeBody(response));
            }
            return ONode.ofJson(safeBody(response));
        } finally {
            response.close();
        }
    }

    private ONode postJson(String path, String body) throws Exception {
        Request request =
                new Request.Builder()
                        .url(apiDomain() + path)
                        .header("Authorization", "QQBot " + accessToken)
                        .post(RequestBody.create(JSON, body))
                        .build();
        Response response = client.newCall(request).execute();
        try {
            String raw = safeBody(response);
            if (!response.isSuccessful()) {
                throw new IllegalStateException("QQBot HTTP " + response.code() + ": " + raw);
            }
            return StrUtil.isBlank(raw) ? new ONode() : ONode.ofJson(raw);
        } finally {
            response.close();
        }
    }

    private String safeBody(Response response) throws Exception {
        return response.body() == null ? "" : response.body().string();
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
            QQBotChannelAdapter.this.webSocket = null;
            setConnected(false);
            setSetupState("error");
            setLastError("qqbot_websocket_failure", t == null ? "unknown" : t.getMessage());
            setDetail("websocket disconnected");
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            QQBotChannelAdapter.this.webSocket = null;
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
                            log.warn("[QQBOT] inbound dispatch failed: {}", e.getMessage(), e);
                        }
                    }
                });
    }

    protected GatewayMessage toGatewayMessage(String raw) {
        ONode root = ONode.ofJson(raw);
        ONode data = root.get("d");
        if (data == null || data.isNull()) {
            data = root;
        }
        String eventType = StrUtil.nullToEmpty(root.get("t").getString()).toLowerCase();
        String chatType =
                eventType.contains("group")
                                || StrUtil.isNotBlank(data.get("group_openid").getString())
                        ? GatewayBehaviorConstants.CHAT_TYPE_GROUP
                        : (eventType.contains("guild")
                                ? "guild"
                                : GatewayBehaviorConstants.CHAT_TYPE_DM);
        String chatId =
                firstNonBlank(
                        data.get("group_openid").getString(),
                        data.get("group_id").getString(),
                        data.get("channel_id").getString(),
                        data.get("openid").getString(),
                        data.get("author").get("id").getString());
        String userId =
                firstNonBlank(
                        data.get("author").get("user_openid").getString(),
                        data.get("author").get("id").getString(),
                        data.get("user_openid").getString(),
                        data.get("openid").getString());
        String text =
                firstNonBlank(
                        data.get("content").getString(),
                        data.get("text").getString(),
                        data.get("asr_refer_text").getString());
        if (!allowInbound(chatType, chatId, userId)
                || StrUtil.isBlank(chatId)
                || StrUtil.isBlank(text)) {
            return null;
        }
        GatewayMessage message =
                new GatewayMessage(PlatformType.QQBOT, chatId, userId, text.trim());
        message.setChatType(chatType);
        message.setChatName(chatId);
        message.setUserName(userId);
        message.setThreadId(data.get("id").getString());
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

    @SuppressWarnings("unused")
    private MessageAttachment cacheRemoteAttachment(
            String url, String kind, String fileName, String mimeType) throws Exception {
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        try {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "QQBot media download failed: HTTP " + response.code());
            }
            return attachmentCacheService.cacheBytes(
                    PlatformType.QQBOT,
                    AttachmentCacheService.normalizeKind(kind, fileName, mimeType),
                    StrUtil.blankToDefault(fileName, "qqbot-attachment.bin"),
                    AttachmentCacheService.normalizeMimeType(
                            response.header("Content-Type"), fileName),
                    false,
                    null,
                    BoundedAttachmentIO.readOkHttpResponse(
                            response, BoundedAttachmentIO.DEFAULT_MAX_BYTES));
        } finally {
            response.close();
        }
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
