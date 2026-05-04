package com.jimuqu.solon.claw.gateway.platform.weixin;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.gateway.platform.base.AbstractConfigurableChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.BoundedAttachmentIO;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.noear.snack4.ONode;

/** WeiXinChannelAdapter 实现。 */
public class WeiXinChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String DEFAULT_CDN_BASE_URL = "https://novac2c.cdn.weixin.qq.com/c2c";
    private static final String SEND_ENDPOINT = "ilink/bot/sendmessage";
    private static final String GET_UPDATES_ENDPOINT = "ilink/bot/getupdates";
    private static final String SEND_TYPING_ENDPOINT = "ilink/bot/sendtyping";
    private static final String GET_CONFIG_ENDPOINT = "ilink/bot/getconfig";
    private static final String GET_UPLOAD_URL_ENDPOINT = "ilink/bot/getuploadurl";
    private static final String CONTEXT_TOKEN_KEY = "context_token";
    private static final String SYNC_BUF_KEY = "sync_buf";
    private static final int LONG_POLL_TIMEOUT_MS = 35_000;
    private static final int CONFIG_TIMEOUT_MS = 10_000;
    private static final int MESSAGE_DEDUP_TTL_MILLIS = 5 * 60 * 1000;

    private static final int MSG_TYPE_BOT = 2;
    private static final int MSG_STATE_FINISH = 2;
    private static final int ITEM_TEXT = 1;
    private static final int ITEM_IMAGE = 2;
    private static final int ITEM_FILE = 4;
    private static final int ITEM_VIDEO = 5;
    private static final int MEDIA_IMAGE = 1;
    private static final int MEDIA_VIDEO = 2;
    private static final int MEDIA_FILE = 3;
    private static final int TYPING_START = 1;
    private static final int TYPING_STOP = 2;
    private static final int MAX_TEXT_CHUNK_LENGTH = 2000;

    private final AppConfig.ChannelConfig config;
    private final ChannelStateRepository channelStateRepository;
    private final AttachmentCacheService attachmentCacheService;
    private final ConcurrentMap<String, Long> recentMessageIds =
            new ConcurrentHashMap<String, Long>();
    private final ConcurrentMap<String, TypingTicketState> typingTickets =
            new ConcurrentHashMap<String, TypingTicketState>();
    private volatile ExecutorService pollExecutor;
    private volatile ExecutorService inboundExecutor;
    private volatile boolean polling;

    public WeiXinChannelAdapter(
            AppConfig.ChannelConfig config,
            ChannelStateRepository channelStateRepository,
            AttachmentCacheService attachmentCacheService) {
        super(PlatformType.WEIXIN, config);
        this.config = config;
        this.channelStateRepository = channelStateRepository;
        this.attachmentCacheService = attachmentCacheService;
        setConnectionMode("long-poll");
        setFeatures("text", "attachments", "quoted-media", "typing", "qr-login");
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
        if (StrUtil.isBlank(config.getToken())) {
            missing.add("solonclaw.channels.weixin.token");
        }
        if (StrUtil.isBlank(config.getAccountId())) {
            missing.add("solonclaw.channels.weixin.accountId");
        }
        if (!missing.isEmpty()) {
            setConnected(false);
            setSetupState("missing_config");
            setMissingConfig(missing);
            setLastError("weixin_missing_credentials", "missing token/accountId");
            setDetail("missing token/accountId");
            log.warn("[WEIXIN] Missing token/accountId");
            return false;
        }
        setMissingConfig(new String[0]);
        clearLastError();
        setConnected(true);
        setSetupState("connected");
        setDetail("long-poll configured");
        ensureInboundExecutor();
        startPolling();
        return true;
    }

    @Override
    public void disconnect() {
        polling = false;
        if (pollExecutor != null) {
            pollExecutor.shutdownNow();
            pollExecutor = null;
        }
        if (inboundExecutor != null) {
            inboundExecutor.shutdown();
            inboundExecutor = null;
        }
        setConnected(false);
        setDetail("disconnected");
    }

    @Override
    public void send(DeliveryRequest request) {
        if (StrUtil.isBlank(request.getChatId())) {
            throw new IllegalArgumentException("Weixin chatId is required");
        }

        if (StrUtil.isNotBlank(request.getText())) {
            sendText(request.getChatId(), request.getText());
        }
        List<MessageAttachment> attachments = request.getAttachments();
        if (attachments != null) {
            for (MessageAttachment attachment : attachments) {
                sendAttachment(request.getChatId(), attachment);
            }
        }
    }

    private void sendText(String chatId, String text) {
        List<String> chunks = splitTextForDelivery(text);
        for (int i = 0; i < chunks.size(); i++) {
            sendTextChunk(chatId, chunks.get(i));
            if (i + 1 < chunks.size()) {
                sleepQuietlyMillis((long) (config.getSendChunkDelaySeconds() * 1000L));
            }
        }
    }

    private void sendTextChunk(String chatId, String text) {
        int attempts = Math.max(1, config.getSendChunkRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ONode message = baseMessage(chatId);
                String contextToken = loadContextToken(chatId);
                if (StrUtil.isNotBlank(contextToken)) {
                    message.set("context_token", contextToken);
                }
                ONode textItem = new ONode();
                textItem.set("text", text);
                ONode item = new ONode();
                item.set("type", ITEM_TEXT);
                item.set("text_item", textItem);
                message.get("item_list").add(item);
                ONode response = apiPost(SEND_ENDPOINT, new ONode().set("msg", message).asObject());
                if (response.get("errcode").getInt(0) == -14 && StrUtil.isNotBlank(contextToken)) {
                    message.remove("context_token");
                    response = apiPost(SEND_ENDPOINT, new ONode().set("msg", message).asObject());
                }
                ensureSuccess(response, "Weixin text send failed");
                clearLastError();
                return;
            } catch (Exception e) {
                setLastError("weixin_send_text_failed", safeMessage(e));
                if (attempt >= attempts) {
                    throw new IllegalStateException(
                            "Weixin text send failed after "
                                    + attempts
                                    + " attempt(s): "
                                    + safeMessage(e),
                            e);
                }
                sleepQuietlyMillis((long) (config.getSendChunkRetryDelaySeconds() * 1000L));
            }
        }
    }

    private List<String> splitTextForDelivery(String text) {
        ArrayList<String> chunks = new ArrayList<String>();
        String normalized = StrUtil.nullToEmpty(text).trim();
        if (normalized.length() == 0) {
            return chunks;
        }
        if (!config.isSplitMultilineMessages() && normalized.length() <= MAX_TEXT_CHUNK_LENGTH) {
            chunks.add(normalized);
            return chunks;
        }
        if (config.isSplitMultilineMessages()
                && normalized.indexOf('\n') >= 0
                && normalized.length() <= MAX_TEXT_CHUNK_LENGTH) {
            String[] lines = normalized.split("\\R");
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.length() > 0) {
                    chunks.add(trimmed);
                }
            }
            if (!chunks.isEmpty()) {
                return chunks;
            }
        }

        String[] paragraphs = normalized.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph == null ? "" : paragraph.trim();
            if (trimmed.length() == 0) {
                continue;
            }
            if (trimmed.length() > MAX_TEXT_CHUNK_LENGTH) {
                appendChunk(chunks, current);
                splitHard(trimmed, chunks);
                continue;
            }
            String candidate =
                    current.length() == 0 ? trimmed : current.toString() + "\n\n" + trimmed;
            if (candidate.length() > MAX_TEXT_CHUNK_LENGTH) {
                appendChunk(chunks, current);
                current.append(trimmed);
            } else {
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(trimmed);
            }
        }
        appendChunk(chunks, current);
        if (chunks.isEmpty()) {
            splitHard(normalized, chunks);
        }
        return chunks;
    }

    private void appendChunk(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        chunks.add(current.toString());
        current.setLength(0);
    }

    private void splitHard(String text, List<String> chunks) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_TEXT_CHUNK_LENGTH, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return StrUtil.isBlank(message) ? e.getClass().getSimpleName() : message.trim();
    }

    private void sendAttachment(String chatId, MessageAttachment attachment) {
        File file = new File(attachment.getLocalPath());
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "Weixin attachment file not found: " + attachment.getLocalPath());
        }

        String kind =
                AttachmentCacheService.normalizeKind(
                        attachment.getKind(),
                        attachment.getOriginalName(),
                        attachment.getMimeType());
        int mediaType =
                "image".equals(kind)
                        ? MEDIA_IMAGE
                        : ("video".equals(kind) ? MEDIA_VIDEO : MEDIA_FILE);
        byte[] plaintext = FileUtil.readBytes(file);
        byte[] aesKey = RandomUtil.randomBytes(16);
        byte[] ciphertext = encryptAesEcb(plaintext, aesKey);
        String fileKey = IdUtil.fastSimpleUUID();

        ONode uploadInfo =
                apiPost(
                        GET_UPLOAD_URL_ENDPOINT,
                        new ONode()
                                .set("filekey", fileKey)
                                .set("media_type", mediaType)
                                .set("to_user_id", chatId)
                                .set("rawsize", plaintext.length)
                                .set("rawfilemd5", DigestUtil.md5Hex(plaintext))
                                .set("filesize", ciphertext.length)
                                .set("no_need_thumb", true)
                                .set("aeskey", HexUtil.encodeHexStr(aesKey))
                                .asObject());
        ensureSuccess(uploadInfo, "Weixin upload init failed");

        String uploadUrl = resolveUploadUrl(uploadInfo, fileKey);
        String encryptedParam = uploadCiphertext(uploadUrl, ciphertext);

        ONode message = baseMessage(chatId);
        String contextToken = loadContextToken(chatId);
        if (StrUtil.isNotBlank(contextToken)) {
            message.set("context_token", contextToken);
        }
        message.getOrNew("item_list")
                .asArray()
                .add(
                        buildMediaItem(
                                mediaType,
                                attachment,
                                plaintext.length,
                                ciphertext.length,
                                encryptedParam,
                                aesKey));
        ONode sendResponse = apiPost(SEND_ENDPOINT, new ONode().set("msg", message).asObject());
        ensureSuccess(sendResponse, "Weixin media send failed");
    }

    private ONode baseMessage(String chatId) {
        ONode message = new ONode();
        message.asObject();
        message.set("from_user_id", "");
        message.set("to_user_id", chatId);
        message.set("client_id", "jimuqu-weixin-" + UUID.randomUUID().toString().replace("-", ""));
        message.set("message_type", MSG_TYPE_BOT);
        message.set("message_state", MSG_STATE_FINISH);
        message.getOrNew("item_list").asArray();
        return message;
    }

    private ONode buildMediaItem(
            int mediaType,
            MessageAttachment attachment,
            int plaintextSize,
            int ciphertextSize,
            String encryptedParam,
            byte[] aesKey) {
        String encodedAesKey =
                Base64.getEncoder()
                        .encodeToString(
                                HexUtil.encodeHexStr(aesKey).getBytes(StandardCharsets.UTF_8));
        if (mediaType == MEDIA_IMAGE) {
            ONode media = new ONode();
            media.set("encrypt_query_param", encryptedParam);
            media.set("aes_key", encodedAesKey);
            media.set("encrypt_type", 1);

            ONode imageItem = new ONode();
            imageItem.set("media", media);
            imageItem.set("mid_size", ciphertextSize);

            ONode item = new ONode();
            item.set("type", ITEM_IMAGE);
            item.set("image_item", imageItem);
            return item;
        }
        if (mediaType == MEDIA_VIDEO) {
            ONode media = new ONode();
            media.set("encrypt_query_param", encryptedParam);
            media.set("aes_key", encodedAesKey);
            media.set("encrypt_type", 1);

            ONode videoItem = new ONode();
            videoItem.set("media", media);
            videoItem.set("video_size", ciphertextSize);
            videoItem.set("play_length", 0);
            videoItem.set("video_md5", DigestUtil.md5Hex(new File(attachment.getLocalPath())));

            ONode item = new ONode();
            item.set("type", ITEM_VIDEO);
            item.set("video_item", videoItem);
            return item;
        }
        ONode media = new ONode();
        media.set("encrypt_query_param", encryptedParam);
        media.set("aes_key", encodedAesKey);
        media.set("encrypt_type", 1);

        ONode fileItem = new ONode();
        fileItem.set("media", media);
        fileItem.set("file_name", fileNameOf(attachment));
        fileItem.set("len", String.valueOf(plaintextSize));

        ONode item = new ONode();
        item.set("type", ITEM_FILE);
        item.set("file_item", fileItem);
        return item;
    }

    private ONode apiPost(String endpoint, ONode payload) {
        return apiPost(endpoint, payload, LONG_POLL_TIMEOUT_MS + 5_000);
    }

    private String resolveUploadUrl(ONode uploadInfo, String fileKey) {
        String uploadFullUrl = uploadInfo.get("upload_full_url").getString();
        if (StrUtil.isNotBlank(uploadFullUrl)) {
            return uploadFullUrl;
        }
        String uploadParam = uploadInfo.get("upload_param").getString();
        if (StrUtil.isBlank(uploadParam)) {
            throw new IllegalStateException(
                    "Weixin upload init missing upload url: " + uploadInfo.toJson());
        }
        String cdnBaseUrl =
                StrUtil.blankToDefault(config.getCdnBaseUrl(), DEFAULT_CDN_BASE_URL)
                        .replaceAll("/+$", "");
        return cdnBaseUrl
                + "/upload?encrypted_query_param="
                + cn.hutool.core.net.URLEncodeUtil.encodeAll(uploadParam)
                + "&filekey="
                + cn.hutool.core.net.URLEncodeUtil.encodeAll(fileKey);
    }

    private String uploadCiphertext(String uploadUrl, byte[] ciphertext) {
        HttpResponse response =
                HttpRequest.post(uploadUrl)
                        .body(ciphertext)
                        .header("Content-Type", "application/octet-stream")
                        .timeout(120000)
                        .execute();
        try {
            if (response.getStatus() != 200) {
                throw new IllegalStateException(
                        "Weixin CDN upload failed: " + response.getStatus());
            }
            String encryptedParam = response.header("x-encrypted-param");
            if (StrUtil.isBlank(encryptedParam)) {
                throw new IllegalStateException(
                        "Weixin CDN upload missing x-encrypted-param header");
            }
            return encryptedParam;
        } finally {
            response.close();
        }
    }

    private byte[] encryptAesEcb(byte[] plaintext, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new IllegalStateException("Weixin encrypt failed", e);
        }
    }

    private void ensureSuccess(ONode node, String defaultMessage) {
        int errCode = node.get("errcode").getInt(0);
        int ret = node.get("ret").getInt(0);
        if (errCode != 0 || ret != 0) {
            throw new IllegalStateException(defaultMessage + ": " + node.toJson());
        }
    }

    private String loadContextToken(String chatId) {
        try {
            return channelStateRepository.get(
                    PlatformType.WEIXIN, config.getAccountId() + ":" + chatId, CONTEXT_TOKEN_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    private String fileNameOf(MessageAttachment attachment) {
        return StrUtil.blankToDefault(
                attachment.getOriginalName(), new File(attachment.getLocalPath()).getName());
    }

    private void startPolling() {
        if (polling) {
            return;
        }
        polling = true;
        pollExecutor = Executors.newSingleThreadExecutor();
        pollExecutor.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        pollLoop();
                    }
                });
    }

    private synchronized ExecutorService ensureInboundExecutor() {
        if (inboundExecutor == null
                || inboundExecutor.isShutdown()
                || inboundExecutor.isTerminated()) {
            inboundExecutor = Executors.newSingleThreadExecutor();
        }
        return inboundExecutor;
    }

    private void pollLoop() {
        String syncBuf = loadSyncBuf();
        while (polling && !Thread.currentThread().isInterrupted()) {
            try {
                pruneRecentMessageIds();
                ONode response =
                        apiPost(
                                GET_UPDATES_ENDPOINT,
                                new ONode().set("get_updates_buf", syncBuf).asObject());
                int errCode = response.get("errcode").getInt(0);
                int ret = response.get("ret").getInt(0);
                if (errCode != 0 || ret != 0) {
                    log.warn("[WEIXIN] getupdates failed: {}", response.toJson());
                    if (errCode == -14 || ret == -14) {
                        setLastError("weixin_session_expired", response.toJson());
                        setDetail("session expired");
                        sleepQuietly(10);
                        continue;
                    }
                    setLastError("weixin_poll_failed", response.toJson());
                    setDetail("poll failed");
                    sleepQuietly(2);
                    continue;
                }
                clearLastError();
                setDetail("long-poll active");
                String nextSyncBuf = response.get("get_updates_buf").getString();
                ONode msgs = response.get("msgs");
                for (int i = 0; i < msgs.size(); i++) {
                    processInboundMessage(msgs.get(i));
                }
                if (StrUtil.isNotBlank(nextSyncBuf)) {
                    syncBuf = nextSyncBuf;
                    saveSyncBuf(syncBuf);
                }
            } catch (Exception e) {
                log.warn("[WEIXIN] poll failed: {}", e.getMessage());
                sleepQuietly(2);
            }
        }
    }

    private void processInboundMessage(ONode message) {
        String senderId = message.get("from_user_id").getString();
        if (StrUtil.isBlank(senderId) || senderId.equals(config.getAccountId())) {
            return;
        }
        String messageId = message.get("message_id").getString();
        if (isDuplicate(messageId)) {
            return;
        }

        ChatTarget chatTarget = guessChatTarget(message);
        if ("group".equals(chatTarget.chatType) && !allowGroup(chatTarget.chatId)) {
            return;
        }
        if ("dm".equals(chatTarget.chatType) && !allowDm(senderId)) {
            return;
        }

        String contextToken = message.get("context_token").getString();
        if (StrUtil.isNotBlank(contextToken)) {
            saveContextToken(chatTarget.chatId, contextToken);
        }

        ONode itemList = message.get("item_list");
        String text = extractInboundText(itemList);
        java.util.ArrayList<MessageAttachment> attachments =
                new java.util.ArrayList<MessageAttachment>();
        for (int i = 0; i < itemList.size(); i++) {
            collectMedia(itemList.get(i), attachments, false);
            ONode refItem = itemList.get(i).get("ref_msg").get("message_item");
            if (refItem != null && refItem.isObject()) {
                collectMedia(refItem, attachments, true);
            }
        }
        if (StrUtil.isBlank(text) && attachments.isEmpty()) {
            return;
        }

        GatewayMessage gatewayMessage =
                new GatewayMessage(PlatformType.WEIXIN, chatTarget.chatId, senderId, text);
        gatewayMessage.setChatType(chatTarget.chatType);
        gatewayMessage.setChatName(chatTarget.chatId);
        gatewayMessage.setUserName(senderId);
        gatewayMessage.setThreadId(messageId);
        gatewayMessage.setAttachments(attachments);
        dispatchInboundMessage(
                gatewayMessage, chatTarget.chatType, chatTarget.chatId, contextToken);
    }

    private void dispatchInboundMessage(
            final GatewayMessage gatewayMessage,
            final String chatType,
            final String chatId,
            final String contextToken) {
        try {
            ensureInboundExecutor()
                    .submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if ("dm".equals(chatType)) {
                                            maybeFetchTypingTicket(chatId, contextToken);
                                            sendTyping(chatId, TYPING_START);
                                        }
                                        inboundMessageHandler().handle(gatewayMessage);
                                    } catch (Exception e) {
                                        log.warn(
                                                "[WEIXIN] inbound dispatch failed: {}",
                                                e.getMessage(),
                                                e);
                                    } finally {
                                        if ("dm".equals(chatType)) {
                                            sendTyping(chatId, TYPING_STOP);
                                        }
                                    }
                                }
                            });
        } catch (Exception e) {
            log.warn("[WEIXIN] inbound submit failed: {}", e.getMessage(), e);
        }
    }

    private String extractInboundText(ONode itemList) {
        for (int i = 0; i < itemList.size(); i++) {
            ONode item = itemList.get(i);
            if (item.get("type").getInt() == ITEM_TEXT) {
                String text = item.get("text_item").get("text").getString();
                if (StrUtil.isBlank(text)) {
                    text = item.get("text_item").get("content").getString();
                }
                if (StrUtil.isNotBlank(text)) {
                    return text.trim();
                }
            }
        }
        for (int i = 0; i < itemList.size(); i++) {
            ONode item = itemList.get(i);
            if (item.get("type").getInt() == 3) {
                String voiceText = item.get("voice_item").get("text").getString();
                if (StrUtil.isNotBlank(voiceText)) {
                    return voiceText.trim();
                }
            }
        }
        return "";
    }

    private void collectMedia(ONode item, List<MessageAttachment> attachments, boolean fromQuote) {
        int type = item.get("type").getInt();
        try {
            if (type == ITEM_IMAGE) {
                MessageAttachment attachment =
                        downloadAttachment(
                                "image",
                                item.get("image_item"),
                                "image.jpg",
                                "image/jpeg",
                                fromQuote);
                if (attachment != null) {
                    attachments.add(attachment);
                }
            } else if (type == ITEM_VIDEO) {
                MessageAttachment attachment =
                        downloadAttachment(
                                "video",
                                item.get("video_item"),
                                "video.mp4",
                                "video/mp4",
                                fromQuote);
                if (attachment != null) {
                    attachments.add(attachment);
                }
            } else if (type == ITEM_FILE) {
                String originalName = item.get("file_item").get("file_name").getString();
                MessageAttachment attachment =
                        downloadAttachment(
                                "file",
                                item.get("file_item"),
                                StrUtil.blankToDefault(originalName, "document.bin"),
                                null,
                                fromQuote);
                if (attachment != null) {
                    attachments.add(attachment);
                }
            } else if (type == 3) {
                MessageAttachment attachment =
                        downloadAttachment(
                                "voice",
                                item.get("voice_item"),
                                "voice.silk",
                                "audio/silk",
                                fromQuote);
                if (attachment != null) {
                    String voiceText = item.get("voice_item").get("text").getString();
                    attachment.setTranscribedText(StrUtil.nullToEmpty(voiceText).trim());
                    attachments.add(attachment);
                }
            }
        } catch (Exception e) {
            log.warn("[WEIXIN] collect media failed: {}", e.getMessage());
        }
    }

    private MessageAttachment downloadAttachment(
            String kind,
            ONode payload,
            String fallbackName,
            String fallbackMime,
            boolean fromQuote) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        ONode media = payload.get("media");
        String encryptedQuery = media.get("encrypt_query_param").getString();
        String fullUrl = media.get("full_url").getString();
        byte[] raw = downloadBytes(resolveInboundUrl(encryptedQuery, fullUrl));
        byte[] key =
                parseAesKey(payload.get("aeskey").getString(), media.get("aes_key").getString());
        if (key != null) {
            raw = decryptAesEcb(raw, key);
        }
        String originalName = fallbackName;
        if ("file".equals(kind)) {
            originalName =
                    StrUtil.blankToDefault(payload.get("file_name").getString(), fallbackName);
        }
        String mimeType = AttachmentCacheService.normalizeMimeType(fallbackMime, originalName);
        return attachmentCacheService.cacheBytes(
                PlatformType.WEIXIN,
                kind,
                originalName,
                mimeType,
                fromQuote,
                payload.get("text").getString(),
                raw);
    }

    private String resolveInboundUrl(String encryptedQuery, String fullUrl) {
        if (StrUtil.isNotBlank(encryptedQuery)) {
            String cdnBaseUrl =
                    StrUtil.blankToDefault(config.getCdnBaseUrl(), DEFAULT_CDN_BASE_URL)
                            .replaceAll("/+$", "");
            return cdnBaseUrl
                    + "/download?encrypted_query_param="
                    + cn.hutool.core.net.URLEncodeUtil.encodeAll(encryptedQuery);
        }
        if (StrUtil.isNotBlank(fullUrl)) {
            return fullUrl;
        }
        throw new IllegalStateException("Weixin media item missing download url");
    }

    private byte[] downloadBytes(String url) {
        return BoundedAttachmentIO.downloadHutool(
                url, 60000, BoundedAttachmentIO.DEFAULT_MAX_BYTES);
    }

    private byte[] parseAesKey(String hexAesKey, String encodedAesKey) {
        if (StrUtil.isNotBlank(hexAesKey)) {
            return HexUtil.decodeHex(hexAesKey);
        }
        if (StrUtil.isBlank(encodedAesKey)) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(encodedAesKey);
        if (decoded.length == 16) {
            return decoded;
        }
        String candidate = new String(decoded, StandardCharsets.UTF_8);
        if (candidate.matches("(?i)[0-9a-f]{32}")) {
            return HexUtil.decodeHex(candidate);
        }
        return null;
    }

    private byte[] decryptAesEcb(byte[] ciphertext, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Weixin decrypt failed", e);
        }
    }

    private ChatTarget guessChatTarget(ONode message) {
        String roomId = message.get("room_id").getString();
        if (StrUtil.isBlank(roomId)) {
            roomId = message.get("chat_room_id").getString();
        }
        String toUserId = message.get("to_user_id").getString();
        boolean isGroup =
                StrUtil.isNotBlank(roomId)
                        || (StrUtil.isNotBlank(toUserId)
                                && !toUserId.equals(config.getAccountId())
                                && message.get("msg_type").getInt() == 1);
        if (isGroup) {
            return new ChatTarget(
                    "group",
                    StrUtil.blankToDefault(
                            roomId,
                            StrUtil.blankToDefault(
                                    toUserId, message.get("from_user_id").getString())));
        }
        return new ChatTarget("dm", message.get("from_user_id").getString());
    }

    private boolean allowDm(String userId) {
        if (config.isAllowAllUsers()) {
            return true;
        }
        String policy =
                StrUtil.blankToDefault(
                                config.getDmPolicy(), GatewayBehaviorConstants.DM_POLICY_OPEN)
                        .toLowerCase();
        if (GatewayBehaviorConstants.DM_POLICY_DISABLED.equals(policy)) {
            return false;
        }
        if (GatewayBehaviorConstants.DM_POLICY_ALLOWLIST.equals(policy)) {
            return contains(config.getAllowedUsers(), userId);
        }
        return true;
    }

    private boolean allowGroup(String chatId) {
        if (config.isAllowAllUsers()) {
            return true;
        }
        String policy =
                StrUtil.blankToDefault(
                                config.getGroupPolicy(),
                                GatewayBehaviorConstants.GROUP_POLICY_DISABLED)
                        .toLowerCase();
        if (GatewayBehaviorConstants.GROUP_POLICY_DISABLED.equals(policy)) {
            return false;
        }
        if (GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST.equals(policy)) {
            return contains(config.getGroupAllowedUsers(), chatId);
        }
        return true;
    }

    private boolean contains(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        if (values.contains(GatewayBehaviorConstants.ALLOW_ALL_MARKER)) {
            return true;
        }
        return values.contains(target);
    }

    private void maybeFetchTypingTicket(String userId, String contextToken) {
        TypingTicketState existing = typingTickets.get(userId);
        if (existing != null && existing.isValid()) {
            return;
        }
        try {
            ONode response =
                    apiPost(
                            GET_CONFIG_ENDPOINT,
                            new ONode()
                                    .set("ilink_user_id", userId)
                                    .set("context_token", contextToken)
                                    .asObject(),
                            CONFIG_TIMEOUT_MS);
            String typingTicket = response.get("typing_ticket").getString();
            if (StrUtil.isNotBlank(typingTicket)) {
                typingTickets.put(
                        userId,
                        new TypingTicketState(
                                typingTicket, System.currentTimeMillis() + 10L * 60L * 1000L));
            }
        } catch (Exception e) {
            log.debug("[WEIXIN] fetch typing ticket failed: {}", e.getMessage());
        }
    }

    private void sendTyping(String userId, int status) {
        TypingTicketState state = typingTickets.get(userId);
        if (state == null || !state.isValid()) {
            return;
        }
        try {
            apiPost(
                    SEND_TYPING_ENDPOINT,
                    new ONode()
                            .set("ilink_user_id", userId)
                            .set("typing_ticket", state.ticket)
                            .set("status", status)
                            .asObject(),
                    CONFIG_TIMEOUT_MS);
        } catch (Exception e) {
            log.debug("[WEIXIN] send typing failed: {}", e.getMessage());
        }
    }

    private boolean isDuplicate(String messageId) {
        if (StrUtil.isBlank(messageId)) {
            return false;
        }
        Long previous = recentMessageIds.putIfAbsent(messageId, System.currentTimeMillis());
        return previous != null;
    }

    private void pruneRecentMessageIds() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, Long> entry : recentMessageIds.entrySet()) {
            if (now - entry.getValue() >= MESSAGE_DEDUP_TTL_MILLIS) {
                recentMessageIds.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private void saveSyncBuf(String syncBuf) {
        try {
            channelStateRepository.put(
                    PlatformType.WEIXIN, config.getAccountId(), SYNC_BUF_KEY, syncBuf);
        } catch (Exception ignored) {
        }
    }

    private String loadSyncBuf() {
        try {
            return StrUtil.nullToEmpty(
                    channelStateRepository.get(
                            PlatformType.WEIXIN, config.getAccountId(), SYNC_BUF_KEY));
        } catch (Exception e) {
            return "";
        }
    }

    private void saveContextToken(String chatId, String contextToken) {
        try {
            channelStateRepository.put(
                    PlatformType.WEIXIN,
                    config.getAccountId() + ":" + chatId,
                    CONTEXT_TOKEN_KEY,
                    contextToken);
        } catch (Exception ignored) {
        }
    }

    private void sleepQuietly(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietlyMillis(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ONode apiPost(String endpoint, ONode payload, int timeoutMs) {
        String baseUrl = resolveBaseUrl(endpoint);
        payload.set("base_info", new ONode().set("channel_version", "2.2.0").asObject());
        String body = payload.toJson();
        String response =
                HttpRequest.post(baseUrl + "/" + endpoint)
                        .header("AuthorizationType", "ilink_bot_token")
                        .header("Authorization", "Bearer " + config.getToken())
                        .header("iLink-App-Id", "bot")
                        .header("iLink-App-ClientVersion", String.valueOf((2 << 16) | (2 << 8)))
                        .header(
                                "X-WECHAT-UIN",
                                Base64.getEncoder()
                                        .encodeToString(
                                                String.valueOf(Math.abs(RandomUtil.randomInt()))
                                                        .getBytes(StandardCharsets.UTF_8)))
                        .contentType(ContentType.JSON.toString())
                        .body(body)
                        .timeout(Math.max(20_000, timeoutMs))
                        .execute()
                        .body();
        return ONode.ofJson(response);
    }

    private String resolveBaseUrl(String endpoint) {
        String configured =
                StrUtil.blankToDefault(config.getBaseUrl(), DEFAULT_BASE_URL).replaceAll("/+$", "");
        if (GET_UPDATES_ENDPOINT.equals(endpoint) && StrUtil.isNotBlank(config.getLongPollUrl())) {
            String longPoll = config.getLongPollUrl().trim();
            if (longPoll.endsWith("/" + GET_UPDATES_ENDPOINT)) {
                return longPoll.substring(0, longPoll.length() - GET_UPDATES_ENDPOINT.length() - 1);
            }
            if (longPoll.startsWith("http://") || longPoll.startsWith("https://")) {
                return longPoll.replaceAll("/+$", "");
            }
        }
        return configured;
    }

    private static class ChatTarget {
        private final String chatType;
        private final String chatId;

        private ChatTarget(String chatType, String chatId) {
            this.chatType = chatType;
            this.chatId = chatId;
        }
    }

    private static class TypingTicketState {
        private final String ticket;
        private final long expiresAt;

        private TypingTicketState(String ticket, long expiresAt) {
            this.ticket = ticket;
            this.expiresAt = expiresAt;
        }

        private boolean isValid() {
            return ticket != null && ticket.length() > 0 && expiresAt > System.currentTimeMillis();
        }
    }
}
