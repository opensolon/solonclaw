package com.jimuqu.solon.claw.gateway.platform.wecom;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.noear.snack4.ONode;

/** WeComChannelAdapter 实现。 */
public class WeComChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String DEFAULT_WS_URL = "wss://openws.work.weixin.qq.com";
    private static final String APP_CMD_CALLBACK = "aibot_msg_callback";
    private static final String APP_CMD_SEND = "aibot_send_msg";
    private static final String APP_CMD_RESPONSE = "aibot_respond_msg";
    private static final String APP_CMD_UPLOAD_MEDIA_INIT = "aibot_upload_media_init";
    private static final String APP_CMD_UPLOAD_MEDIA_CHUNK = "aibot_upload_media_chunk";
    private static final String APP_CMD_UPLOAD_MEDIA_FINISH = "aibot_upload_media_finish";
    private static final int UPLOAD_CHUNK_SIZE = 512 * 1024;
    private static final int IMAGE_MAX_BYTES = 10 * 1024 * 1024;
    private static final int VIDEO_MAX_BYTES = 10 * 1024 * 1024;
    private static final int VOICE_MAX_BYTES = 2 * 1024 * 1024;
    private static final int FILE_MAX_BYTES = 20 * 1024 * 1024;
    private static final long REPLY_REQ_ID_TTL_MILLIS = 5L * 60L * 1000L;

    private final AppConfig.ChannelConfig config;
    private final AttachmentCacheService attachmentCacheService;
    private final OkHttpClient client;
    private final ConcurrentMap<String, CompletableFuture<ONode>> pendingResponses =
            new ConcurrentHashMap<String, CompletableFuture<ONode>>();
    private final ConcurrentMap<String, TimedReqId> replyReqIds =
            new ConcurrentHashMap<String, TimedReqId>();
    private volatile WebSocket webSocket;
    private ExecutorService callbackExecutor;

    public WeComChannelAdapter(
            AppConfig.ChannelConfig config, AttachmentCacheService attachmentCacheService) {
        super(PlatformType.WECOM, config);
        this.config = config;
        this.attachmentCacheService = attachmentCacheService;
        this.client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build();
        setConnectionMode("websocket");
        setFeatures("text", "attachments", "quoted-media", "reply-mode", "aes-media");
        setSetupState(config != null && config.isEnabled() ? "configured" : "disabled");
    }

    @Override
    public boolean connect() {
        if (!isEnabled()) {
            setSetupState("disabled");
            setDetail("disabled");
            return false;
        }
        java.util.ArrayList<String> missing = new java.util.ArrayList<String>();
        if (StrUtil.isBlank(config.getBotId())) {
            missing.add("solonclaw.channels.wecom.botId");
        }
        if (StrUtil.isBlank(config.getSecret())) {
            missing.add("solonclaw.channels.wecom.secret");
        }
        if (!missing.isEmpty()) {
            setConnected(false);
            setSetupState("missing_config");
            setMissingConfig(missing);
            setLastError("wecom_missing_credentials", "missing botId/secret");
            setDetail("missing botId/secret");
            log.warn("[WECOM] Missing botId/secret");
            return false;
        }

        String wsUrl = StrUtil.blankToDefault(config.getWebsocketUrl(), DEFAULT_WS_URL);
        callbackExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new Listener(latch));
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("WeCom websocket open timeout");
            }
            ONode auth =
                    request(
                            "aibot_subscribe",
                            new ONode()
                                    .set("bot_id", config.getBotId())
                                    .set("secret", config.getSecret())
                                    .asObject(),
                            15);
            int ret = auth.get("ret").getInt(0);
            if (ret != 0) {
                throw new IllegalStateException("WeCom subscribe failed: " + auth.toJson());
            }
            setConnected(true);
            setSetupState("connected");
            setMissingConfig(new String[0]);
            clearLastError();
            setDetail("websocket subscribed");
            return true;
        } catch (Exception e) {
            if (webSocket != null) {
                webSocket.cancel();
            }
            webSocket = null;
            setConnected(false);
            setSetupState("error");
            setLastError("wecom_connect_failed", e.getMessage());
            setDetail("connect failed: " + e.getMessage());
            throw new IllegalStateException("WeCom connect failed", e);
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
    public void send(DeliveryRequest request) {
        if (StrUtil.isBlank(request.getChatId())) {
            throw new IllegalArgumentException("WeCom chatId is required");
        }
        if (StrUtil.isNotBlank(request.getText())) {
            ONode response =
                    sendTextMessage(request.getChatId(), request.getText(), request.getThreadId());
            int ret = response.get("ret").getInt(0);
            if (ret != 0) {
                throw new IllegalStateException("WeCom send failed: " + response.toJson());
            }
        }
        if (request.getAttachments() != null) {
            for (MessageAttachment attachment : request.getAttachments()) {
                sendAttachment(request.getChatId(), attachment, request.getThreadId());
            }
        }
    }

    private ONode request(String cmd, ONode body, int timeoutSeconds) {
        if (webSocket == null) {
            throw new IllegalStateException("WeCom websocket is not connected");
        }

        String reqId = UUID.randomUUID().toString();
        CompletableFuture<ONode> future = new CompletableFuture<ONode>();
        pendingResponses.put(reqId, future);
        String payload =
                new ONode()
                        .set("cmd", cmd)
                        .getOrNew("headers")
                        .set("req_id", reqId)
                        .parent()
                        .set("body", body)
                        .toJson();

        if (!webSocket.send(payload)) {
            pendingResponses.remove(reqId);
            throw new IllegalStateException("WeCom websocket send failed");
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingResponses.remove(reqId);
            throw new IllegalStateException("WeCom request timeout", e);
        }
    }

    @RequiredArgsConstructor
    private class Listener extends WebSocketListener {
        private final CountDownLatch openLatch;

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            openLatch.countDown();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            ONode node = ONode.ofJson(text);
            String reqId = node.get("headers").get("req_id").getString();
            if (StrUtil.isBlank(reqId)) {
                reqId = node.get("payload").get("headers").get("req_id").getString();
            }
            CompletableFuture<ONode> future = pendingResponses.remove(reqId);
            if (future != null) {
                future.complete(node);
                return;
            }
            String cmd = node.get("cmd").getString();
            if (APP_CMD_CALLBACK.equals(cmd)) {
                rememberReplyReqId(node.get("body").get("msgid").getString(), payloadReqId(node));
                handleInbound(node);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            failPending(t == null ? new IllegalStateException("WeCom websocket failure") : t);
            WeComChannelAdapter.this.webSocket = null;
            setConnected(false);
            setSetupState("error");
            setLastError("wecom_websocket_failure", t == null ? "unknown" : t.getMessage());
            setDetail("websocket disconnected");
            openLatch.countDown();
            log.warn("[WECOM] websocket failure: {}", t.getMessage());
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            failPending(
                    new IllegalStateException("WeCom websocket closed: " + code + " " + reason));
            WeComChannelAdapter.this.webSocket = null;
            setConnected(false);
            setSetupState("disconnected");
            setDetail("websocket closed: " + code + " " + reason);
        }
    }

    private void handleInbound(final ONode payload) {
        if (callbackExecutor == null || inboundMessageHandler() == null) {
            return;
        }
        callbackExecutor.submit(
                new Runnable() {
                    public void run() {
                        try {
                            GatewayMessage message = toGatewayMessage(payload);
                            if (message != null) {
                                inboundMessageHandler().handle(message);
                            }
                        } catch (Exception e) {
                            log.warn("[WECOM] inbound dispatch failed: {}", e.getMessage(), e);
                        }
                    }
                });
    }

    private GatewayMessage toGatewayMessage(ONode payload) throws Exception {
        ONode body = payload.get("body");
        String msgId = body.get("msgid").getString();
        String chatId = body.get("chatid").getString();
        String userId = body.get("from").get("userid").getString();
        String chatType =
                "group".equalsIgnoreCase(body.get("chattype").getString()) ? "group" : "dm";
        if (!allowChat(chatType, chatId, userId)) {
            return null;
        }
        String text = extractText(body);
        List<MessageAttachment> attachments = extractAttachments(body, false);
        ONode quote = findQuoteNode(body);
        if (quote != null && quote.isObject()) {
            attachments.addAll(extractAttachments(quote, true));
        }
        if (StrUtil.isBlank(text) && attachments.isEmpty()) {
            return null;
        }

        GatewayMessage message = new GatewayMessage(PlatformType.WECOM, chatId, userId, text);
        message.setChatType(chatType);
        message.setChatName(chatId);
        message.setUserName(userId);
        message.setThreadId(msgId);
        message.setAttachments(attachments);
        return message;
    }

    private String extractText(ONode body) {
        String msgType = body.get("msgtype").getString();
        if ("text".equalsIgnoreCase(msgType)) {
            return StrUtil.nullToEmpty(body.get("text").get("content").getString()).trim();
        }
        if ("voice".equalsIgnoreCase(msgType)) {
            return StrUtil.nullToEmpty(body.get("voice").get("content").getString()).trim();
        }
        if ("mixed".equalsIgnoreCase(msgType)) {
            StringBuilder buffer = new StringBuilder();
            ONode items = body.get("mixed").get("msg_item");
            for (int i = 0; i < items.size(); i++) {
                ONode item = items.get(i);
                if ("text".equalsIgnoreCase(item.get("msgtype").getString())) {
                    if (buffer.length() > 0) {
                        buffer.append('\n');
                    }
                    buffer.append(
                            StrUtil.nullToEmpty(item.get("text").get("content").getString())
                                    .trim());
                }
            }
            return buffer.toString().trim();
        }
        return "";
    }

    private List<MessageAttachment> extractAttachments(ONode body, boolean fromQuote)
            throws Exception {
        List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
        String msgType = body.get("msgtype").getString();
        if ("image".equalsIgnoreCase(msgType)) {
            addAttachment(attachments, "image", body.get("image"), fromQuote);
        } else if ("file".equalsIgnoreCase(msgType)) {
            addAttachment(attachments, "file", body.get("file"), fromQuote);
        } else if ("video".equalsIgnoreCase(msgType)) {
            addAttachment(attachments, "video", body.get("video"), fromQuote);
        } else if ("voice".equalsIgnoreCase(msgType)) {
            addAttachment(attachments, "voice", body.get("voice"), fromQuote);
        } else if ("mixed".equalsIgnoreCase(msgType)) {
            ONode items = body.get("mixed").get("msg_item");
            for (int i = 0; i < items.size(); i++) {
                ONode item = items.get(i);
                String itemType = item.get("msgtype").getString();
                if ("image".equalsIgnoreCase(itemType)) {
                    addAttachment(attachments, "image", item.get("image"), fromQuote);
                } else if ("file".equalsIgnoreCase(itemType)) {
                    addAttachment(attachments, "file", item.get("file"), fromQuote);
                } else if ("video".equalsIgnoreCase(itemType)) {
                    addAttachment(attachments, "video", item.get("video"), fromQuote);
                } else if ("voice".equalsIgnoreCase(itemType)) {
                    addAttachment(attachments, "voice", item.get("voice"), fromQuote);
                }
            }
        }
        return attachments;
    }

    private void addAttachment(
            List<MessageAttachment> attachments, String kind, ONode payload, boolean fromQuote)
            throws Exception {
        String url = payload.get("url").getString();
        if (StrUtil.isBlank(url)) {
            return;
        }
        byte[] data = downloadBytes(url, FILE_MAX_BYTES);
        String aesKey = payload.get("aeskey").getString();
        if (StrUtil.isNotBlank(aesKey)) {
            data = decryptFileBytes(data, aesKey);
        }
        String fileName = payload.get("filename").getString();
        if (StrUtil.isBlank(fileName)) {
            fileName = payload.get("name").getString();
        }
        if (StrUtil.isBlank(fileName)) {
            fileName = kind + ".bin";
        }
        String mimeType =
                AttachmentCacheService.normalizeMimeType(
                        payload.get("content_type").getString(), fileName);
        attachments.add(
                attachmentCacheService.cacheBytes(
                        PlatformType.WECOM,
                        kind,
                        fileName,
                        mimeType,
                        fromQuote,
                        payload.get("content").getString(),
                        data));
    }

    private byte[] downloadBytes(String url, long maxBytes) throws Exception {
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        try {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("WeCom download failed: " + response.code());
            }
            return BoundedAttachmentIO.readOkHttpResponse(response, maxBytes);
        } finally {
            response.close();
        }
    }

    private void sendAttachment(
            String chatId, MessageAttachment attachment, String replyToMessageId) {
        File file = new File(attachment.getLocalPath());
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "WeCom attachment file not found: " + attachment.getLocalPath());
        }

        byte[] bytes = cn.hutool.core.io.FileUtil.readBytes(file);
        String mediaType = resolveOutboundMediaType(attachment, bytes.length);
        String mediaId = uploadMediaBytes(bytes, mediaType, file.getName());
        ONode body =
                new ONode()
                        .set("chatid", chatId)
                        .set("msgtype", mediaType)
                        .getOrNew(mediaType)
                        .set("media_id", mediaId)
                        .parent()
                        .asObject();
        ONode response = sendByMode(body, chatId, replyToMessageId, mediaType, 30);
        int errCode = response.get("errcode").getInt(0);
        if (errCode != 0) {
            throw new IllegalStateException("WeCom media send failed: " + response.toJson());
        }
    }

    private ONode sendTextMessage(String chatId, String text, String replyToMessageId) {
        ONode body =
                new ONode()
                        .set("chatid", chatId)
                        .set("msgtype", "markdown")
                        .getOrNew("markdown")
                        .set("content", text)
                        .parent()
                        .asObject();
        return sendByMode(body, chatId, replyToMessageId, "markdown", 15);
    }

    private ONode sendByMode(
            ONode body,
            String chatId,
            String replyToMessageId,
            String description,
            int timeoutSeconds) {
        String replyReqId = replyReqIdForMessage(replyToMessageId);
        try {
            if (StrUtil.isNotBlank(replyReqId)) {
                return requestWithReplyReqId(APP_CMD_RESPONSE, replyReqId, body, timeoutSeconds);
            }
        } catch (Exception e) {
            log.warn(
                    "[WECOM] reply-mode {} failed, fallback to proactive send: {}",
                    description,
                    e.getMessage());
        }
        return request(APP_CMD_SEND, body, timeoutSeconds);
    }

    private ONode requestWithReplyReqId(
            String cmd, String replyReqId, ONode body, int timeoutSeconds) {
        if (webSocket == null) {
            throw new IllegalStateException("WeCom websocket is not connected");
        }
        CompletableFuture<ONode> future = new CompletableFuture<ONode>();
        pendingResponses.put(replyReqId, future);
        String payload =
                new ONode()
                        .set("cmd", cmd)
                        .getOrNew("headers")
                        .set("req_id", replyReqId)
                        .parent()
                        .set("body", body)
                        .toJson();
        if (!webSocket.send(payload)) {
            pendingResponses.remove(replyReqId);
            throw new IllegalStateException("WeCom websocket send failed");
        }
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingResponses.remove(replyReqId);
            throw new IllegalStateException("WeCom reply request timeout", e);
        }
    }

    private String payloadReqId(ONode payload) {
        String reqId = payload.get("headers").get("req_id").getString();
        if (StrUtil.isBlank(reqId)) {
            reqId = payload.get("payload").get("headers").get("req_id").getString();
        }
        return reqId;
    }

    private void rememberReplyReqId(String messageId, String reqId) {
        cleanupReplyReqIds();
        String normalizedMessageId = StrUtil.nullToEmpty(messageId).trim();
        String normalizedReqId = StrUtil.nullToEmpty(reqId).trim();
        if (normalizedMessageId.length() == 0 || normalizedReqId.length() == 0) {
            return;
        }
        replyReqIds.put(
                normalizedMessageId,
                new TimedReqId(
                        normalizedReqId, System.currentTimeMillis() + REPLY_REQ_ID_TTL_MILLIS));
        while (replyReqIds.size() > 1000) {
            String firstKey = replyReqIds.keySet().iterator().next();
            replyReqIds.remove(firstKey);
        }
    }

    private String replyReqIdForMessage(String messageId) {
        cleanupReplyReqIds();
        String normalized = StrUtil.nullToEmpty(messageId).trim();
        if (normalized.length() == 0) {
            return null;
        }
        TimedReqId value = replyReqIds.get(normalized);
        if (value == null || value.isExpired()) {
            replyReqIds.remove(normalized);
            return null;
        }
        return value.reqId;
    }

    private void cleanupReplyReqIds() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, TimedReqId> entry : replyReqIds.entrySet()) {
            TimedReqId value = entry.getValue();
            if (value == null || value.expiresAt <= now) {
                replyReqIds.remove(entry.getKey(), value);
            }
        }
    }

    private void failPending(Throwable throwable) {
        for (CompletableFuture<ONode> future : pendingResponses.values()) {
            future.completeExceptionally(throwable);
        }
        pendingResponses.clear();
    }

    private boolean allowChat(String chatType, String chatId, String userId) {
        if (config.isAllowAllUsers()) {
            return true;
        }
        if ("group".equalsIgnoreCase(chatType)) {
            String policy =
                    StrUtil.blankToDefault(
                                    config.getGroupPolicy(),
                                    GatewayBehaviorConstants.GROUP_POLICY_OPEN)
                            .toLowerCase();
            if (GatewayBehaviorConstants.GROUP_POLICY_DISABLED.equals(policy)) {
                return false;
            }
            if (GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST.equals(policy)
                    && !contains(config.getGroupAllowedUsers(), chatId)) {
                return false;
            }
            return allowGroupSender(chatId, userId);
        }
        String dmPolicy =
                StrUtil.blankToDefault(
                                config.getDmPolicy(), GatewayBehaviorConstants.DM_POLICY_OPEN)
                        .toLowerCase();
        if (GatewayBehaviorConstants.DM_POLICY_DISABLED.equals(dmPolicy)) {
            return false;
        }
        if (GatewayBehaviorConstants.DM_POLICY_ALLOWLIST.equals(dmPolicy)) {
            return contains(config.getAllowedUsers(), userId);
        }
        return true;
    }

    private boolean allowGroupSender(String chatId, String userId) {
        Map<String, List<String>> allowMap = config.getGroupMemberAllowedUsers();
        if (allowMap == null || allowMap.isEmpty()) {
            return true;
        }
        List<String> entries = allowMap.get(chatId);
        if ((entries == null || entries.isEmpty()) && allowMap.containsKey("*")) {
            entries = allowMap.get("*");
        }
        if (entries == null || entries.isEmpty()) {
            return true;
        }
        return contains(entries, userId);
    }

    private boolean contains(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            String normalized = StrUtil.nullToEmpty(value).trim();
            if ("*".equals(normalized)
                    || target.equalsIgnoreCase(normalized)
                    || target.equalsIgnoreCase(
                            normalized
                                    .replaceFirst("(?i)^wecom:", "")
                                    .replaceFirst("(?i)^(user|group):", ""))) {
                return true;
            }
        }
        return false;
    }

    private ONode findQuoteNode(ONode body) {
        for (String key : new String[] {"quote", "quoted", "quote_msg", "reference"}) {
            ONode node = body.get(key);
            if (node != null && node.isObject()) {
                return node;
            }
        }
        return null;
    }

    private String resolveOutboundMediaType(MessageAttachment attachment, int sizeBytes) {
        String kind =
                AttachmentCacheService.normalizeKind(
                        attachment.getKind(),
                        attachment.getOriginalName(),
                        attachment.getMimeType());
        String mime = StrUtil.nullToEmpty(attachment.getMimeType()).toLowerCase();
        if ("image".equals(kind)) {
            return sizeBytes > IMAGE_MAX_BYTES ? "file" : "image";
        }
        if ("video".equals(kind)) {
            return sizeBytes > VIDEO_MAX_BYTES ? "file" : "video";
        }
        if ("voice".equals(kind)) {
            if (sizeBytes > VOICE_MAX_BYTES) {
                return "file";
            }
            return "audio/amr".equals(mime)
                            || StrUtil.endWithIgnoreCase(attachment.getOriginalName(), ".amr")
                    ? "voice"
                    : "file";
        }
        if (sizeBytes > FILE_MAX_BYTES) {
            throw new IllegalStateException("WeCom attachment exceeds 20MB limit");
        }
        return "file";
    }

    private String uploadMediaBytes(byte[] data, String mediaType, String fileName) {
        if (data.length == 0) {
            throw new IllegalArgumentException("WeCom attachment is empty");
        }
        int totalChunks = (data.length + UPLOAD_CHUNK_SIZE - 1) / UPLOAD_CHUNK_SIZE;
        ONode init =
                request(
                        APP_CMD_UPLOAD_MEDIA_INIT,
                        new ONode()
                                .set("type", mediaType)
                                .set("filename", fileName)
                                .set("total_size", data.length)
                                .set("total_chunks", totalChunks)
                                .set("md5", cn.hutool.crypto.digest.DigestUtil.md5Hex(data))
                                .asObject(),
                        30);
        String uploadId = init.get("body").get("upload_id").getString();
        if (StrUtil.isBlank(uploadId)) {
            throw new IllegalStateException(
                    "WeCom media upload init missing upload_id: " + init.toJson());
        }

        for (int index = 0; index < totalChunks; index++) {
            int start = index * UPLOAD_CHUNK_SIZE;
            int end = Math.min(start + UPLOAD_CHUNK_SIZE, data.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(data, start, chunk, 0, chunk.length);
            request(
                    APP_CMD_UPLOAD_MEDIA_CHUNK,
                    new ONode()
                            .set("upload_id", uploadId)
                            .set("chunk_index", index)
                            .set("base64_data", Base64.getEncoder().encodeToString(chunk))
                            .asObject(),
                    30);
        }

        ONode finish =
                request(
                        APP_CMD_UPLOAD_MEDIA_FINISH,
                        new ONode().set("upload_id", uploadId).asObject(),
                        30);
        String mediaId = finish.get("body").get("media_id").getString();
        if (StrUtil.isBlank(mediaId)) {
            throw new IllegalStateException(
                    "WeCom media upload finish missing media_id: " + finish.toJson());
        }
        return mediaId;
    }

    private byte[] decryptFileBytes(byte[] encryptedData, String aesKeyBase64) throws Exception {
        byte[] key = Base64.getDecoder().decode(aesKeyBase64);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec iv = new IvParameterSpec(key, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv);
        return cipher.doFinal(encryptedData);
    }

    private static class TimedReqId {
        private final String reqId;
        private final long expiresAt;

        private TimedReqId(String reqId, long expiresAt) {
            this.reqId = reqId;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return expiresAt <= System.currentTimeMillis();
        }
    }
}
