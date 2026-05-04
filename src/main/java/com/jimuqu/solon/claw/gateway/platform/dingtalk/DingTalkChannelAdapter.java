package com.jimuqu.solon.claw.gateway.platform.dingtalk;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.aliyun.dingtalkconv_file_1_0.models.GetSpaceHeaders;
import com.aliyun.dingtalkconv_file_1_0.models.GetSpaceRequest;
import com.aliyun.dingtalkconv_file_1_0.models.GetSpaceResponse;
import com.aliyun.dingtalkconv_file_1_0.models.SendHeaders;
import com.aliyun.dingtalkconv_file_1_0.models.SendRequest;
import com.aliyun.dingtalkconv_file_1_0.models.SendResponse;
import com.aliyun.dingtalkim_1_0.models.SendRobotInteractiveCardHeaders;
import com.aliyun.dingtalkim_1_0.models.SendRobotInteractiveCardRequest;
import com.aliyun.dingtalkim_1_0.models.SendRobotInteractiveCardResponse;
import com.aliyun.dingtalkim_1_0.models.UpdateRobotInteractiveCardHeaders;
import com.aliyun.dingtalkim_1_0.models.UpdateRobotInteractiveCardRequest;
import com.aliyun.dingtalkim_1_0.models.UpdateRobotInteractiveCardResponse;
import com.aliyun.dingtalkoauth2_1_0.Client;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOHeaders;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTORequest;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOResponse;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendResponse;
import com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadHeaders;
import com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadRequest;
import com.aliyun.dingtalkrobot_1_0.models.RobotMessageFileDownloadResponse;
import com.aliyun.dingtalkstorage_2_0.models.CommitFileHeaders;
import com.aliyun.dingtalkstorage_2_0.models.CommitFileRequest;
import com.aliyun.dingtalkstorage_2_0.models.CommitFileResponse;
import com.aliyun.dingtalkstorage_2_0.models.GetFileUploadInfoHeaders;
import com.aliyun.dingtalkstorage_2_0.models.GetFileUploadInfoRequest;
import com.aliyun.dingtalkstorage_2_0.models.GetFileUploadInfoResponse;
import com.aliyun.tea.TeaException;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.noear.snack4.ONode;

/** DingTalkChannelAdapter 实现。 */
public class DingTalkChannelAdapter extends AbstractConfigurableChannelAdapter {
    private static final String MEDIA_UPLOAD_URL = "https://oapi.dingtalk.com/media/upload";
    private static final String STATE_LAST_USER_ID = "last_user_id";
    private static final String STATE_LAST_UNION_ID = "last_union_id";
    private static final String STATE_LAST_SPACE_ID = "last_space_id";
    private static final String STATE_SESSION_WEBHOOK = "session_webhook";
    private static final String STATE_SESSION_WEBHOOK_EXPIRES_AT = "session_webhook_expires_at";
    private final AppConfig.ChannelConfig config;
    private final ChannelStateRepository channelStateRepository;
    private final AttachmentCacheService attachmentCacheService;
    private final Client oauthClient;
    private final com.aliyun.dingtalkrobot_1_0.Client robotClient;
    private final com.aliyun.dingtalkconv_file_1_0.Client convFileClient;
    private final com.aliyun.dingtalkstorage_2_0.Client storageClient;
    private final com.aliyun.dingtalkim_1_0.Client imClient;
    private volatile String accessToken;
    private volatile long accessTokenExpireAt;
    private volatile OpenDingTalkClient streamClient;
    private ExecutorService callbackExecutor;
    private final Map<String, Boolean> conversationGroupFlags =
            new ConcurrentHashMap<String, Boolean>();
    private final Map<String, SessionWebhookState> sessionWebhooks =
            new ConcurrentHashMap<String, SessionWebhookState>();
    private final Map<String, String> cardInstanceBindings =
            new ConcurrentHashMap<String, String>();

    public DingTalkChannelAdapter(
            AppConfig.ChannelConfig config,
            ChannelStateRepository channelStateRepository,
            AttachmentCacheService attachmentCacheService) {
        super(PlatformType.DINGTALK, config);
        this.config = config;
        this.channelStateRepository = channelStateRepository;
        this.attachmentCacheService = attachmentCacheService;
        try {
            com.aliyun.teaopenapi.models.Config teaConfig =
                    new com.aliyun.teaopenapi.models.Config();
            teaConfig.protocol = "https";
            teaConfig.regionId = "central";
            this.oauthClient = new Client(teaConfig);
            this.robotClient = new com.aliyun.dingtalkrobot_1_0.Client(teaConfig);
            this.convFileClient = new com.aliyun.dingtalkconv_file_1_0.Client(teaConfig);
            this.storageClient = new com.aliyun.dingtalkstorage_2_0.Client(teaConfig);
            this.imClient = new com.aliyun.dingtalkim_1_0.Client(teaConfig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize DingTalk SDK clients", e);
        }
        setConnectionMode("stream");
        setFeatures(
                "text",
                "attachments",
                "group-mention",
                "stream",
                "ai-card",
                "card-callback",
                "emoji-reaction");
        setSetupState(config != null && config.isEnabled() ? "configured" : "disabled");
    }

    @Override
    public boolean connect() {
        log.info(
                "[DINGTALK] connect called: enabled={}, hasClientId={}, hasClientSecret={}, hasRobotCode={}",
                isEnabled(),
                !isBlank(config.getClientId()),
                !isBlank(config.getClientSecret()),
                !isBlank(config.getRobotCode()));
        if (!isEnabled()) {
            setSetupState("disabled");
            return false;
        }
        java.util.ArrayList<String> missing = new java.util.ArrayList<String>();
        if (isBlank(config.getClientId())) {
            missing.add("solonclaw.channels.dingtalk.clientId");
        }
        if (isBlank(config.getClientSecret())) {
            missing.add("solonclaw.channels.dingtalk.clientSecret");
        }
        if (isBlank(config.getRobotCode())) {
            missing.add("solonclaw.channels.dingtalk.robotCode");
        }
        if (!missing.isEmpty()) {
            setSetupState("missing_config");
            setMissingConfig(missing);
            setLastError("dingtalk_missing_credentials", "missing clientId/clientSecret/robotCode");
        }
        if (isBlank(config.getClientId()) || isBlank(config.getClientSecret())) {
            setDetail("missing clientId/clientSecret");
            log.warn("[DINGTALK] connect aborted: {}", detail());
            return false;
        }
        if (isBlank(config.getRobotCode())) {
            setDetail("missing robotCode");
            log.warn("[DINGTALK] connect aborted: {}", detail());
            return false;
        }

        try {
            refreshAccessTokenIfNecessary();
            callbackExecutor = Executors.newSingleThreadExecutor();
            streamClient =
                    OpenDingTalkStreamClientBuilder.custom()
                            .credential(
                                    new AuthClientCredential(
                                            config.getClientId(), config.getClientSecret()))
                            .registerCallbackListener(
                                    DingTalkStreamTopics.BOT_MESSAGE_TOPIC,
                                    new OpenDingTalkCallbackListener<
                                            ChatbotMessage, Map<String, Object>>() {
                                        public Map<String, Object> execute(ChatbotMessage message) {
                                            handleInbound(message);
                                            return new HashMap<String, Object>();
                                        }
                                    })
                            .registerCallbackListener(
                                    DingTalkStreamTopics.CARD_CALLBACK_TOPIC,
                                    new OpenDingTalkCallbackListener<
                                            Map<String, Object>, Map<String, Object>>() {
                                        @Override
                                        public Map<String, Object> execute(
                                                Map<String, Object> payload) {
                                            handleCardCallback(payload);
                                            return new HashMap<String, Object>();
                                        }
                                    })
                            .build();
            streamClient.start();
            setConnected(true);
            setSetupState("connected");
            setMissingConfig(new String[0]);
            clearLastError();
            setDetail("stream mode connected");
            log.info("[DINGTALK] stream mode connected");
            return true;
        } catch (Exception e) {
            setConnected(false);
            setSetupState("error");
            setLastError("dingtalk_stream_connect_failed", e.getMessage());
            setDetail("stream mode connect failed: " + e.getMessage());
            log.warn("[DINGTALK] Stream mode connect failed", e);
            return false;
        }
    }

    @Override
    public void disconnect() {
        try {
            if (streamClient != null) {
                streamClient.stop();
            }
        } catch (Exception e) {
            log.warn("[DINGTALK] Stream mode disconnect failed", e);
        } finally {
            if (callbackExecutor != null) {
                callbackExecutor.shutdownNow();
            }
            setConnected(false);
            setDetail("stream mode disconnected");
        }
    }

    @Override
    public void send(DeliveryRequest request) throws Exception {
        if (isBlank(request.getChatId())) {
            throw new IllegalArgumentException("DingTalk openConversationId is required");
        }
        refreshAccessTokenIfNecessary();
        if (isAiCardRequest(request)) {
            sendAiCard(request);
            return;
        }
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            if (notBlank(request.getText())) {
                sendText(request);
            }
            for (MessageAttachment attachment : request.getAttachments()) {
                sendAttachment(request, attachment);
            }
            return;
        }
        if (notBlank(request.getText())) {
            sendText(request);
        }
    }

    private void handleInbound(final ChatbotMessage message) {
        if (callbackExecutor == null || inboundMessageHandler() == null || message == null) {
            return;
        }
        callbackExecutor.submit(
                new Runnable() {
                    public void run() {
                        try {
                            String text = extractText(message);
                            String conversationId =
                                    notBlank(message.getConversationId())
                                            ? message.getConversationId()
                                            : message.getSenderId();
                            String chatType =
                                    "2".equals(String.valueOf(message.getConversationType()))
                                            ? "group"
                                            : "dm";
                            String userId =
                                    notBlank(message.getSenderStaffId())
                                            ? message.getSenderStaffId()
                                            : message.getSenderId();
                            if (!allowInbound(message, conversationId, chatType, userId)) {
                                return;
                            }
                            List<MessageAttachment> attachments = extractAttachments(message);
                            if (isBlank(text) && attachments.isEmpty()) {
                                return;
                            }
                            conversationGroupFlags.put(
                                    conversationId,
                                    "2".equals(String.valueOf(message.getConversationType())));
                            rememberSessionWebhook(
                                    conversationId,
                                    message.getSessionWebhook(),
                                    message.getSessionWebhookExpiredTime());
                            try {
                                channelStateRepository.put(
                                        PlatformType.DINGTALK,
                                        conversationId,
                                        STATE_LAST_USER_ID,
                                        userId);
                                channelStateRepository.put(
                                        PlatformType.DINGTALK,
                                        conversationId,
                                        STATE_LAST_UNION_ID,
                                        StrUtil.nullToEmpty(message.getSenderId()));
                            } catch (Exception ignored) {
                            }
                            log.info(
                                    "[DINGTALK-INBOUND] conversationId={}, senderId={}, senderStaffId={}, type={}, text={}",
                                    conversationId,
                                    message.getSenderId(),
                                    message.getSenderStaffId(),
                                    message.getConversationType(),
                                    text);
                            GatewayMessage gatewayMessage =
                                    new GatewayMessage(
                                            PlatformType.DINGTALK, conversationId, userId, text);
                            gatewayMessage.setChatType(chatType);
                            gatewayMessage.setChatName(
                                    notBlank(message.getConversationTitle())
                                            ? message.getConversationTitle()
                                            : conversationId);
                            gatewayMessage.setUserName(
                                    notBlank(message.getSenderNick())
                                            ? message.getSenderNick()
                                            : userId);
                            gatewayMessage.setThreadId(message.getMsgId());
                            gatewayMessage.setAttachments(attachments);
                            inboundMessageHandler().handle(gatewayMessage);
                        } catch (Exception e) {
                            log.warn("[DINGTALK] inbound dispatch failed: {}", e.getMessage(), e);
                        }
                    }
                });
    }

    private void handleCardCallback(final Map<String, Object> payload) {
        if (callbackExecutor == null || inboundMessageHandler() == null || payload == null) {
            return;
        }
        callbackExecutor.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            GatewayMessage message = toCardCallbackMessage(payload);
                            if (message != null) {
                                inboundMessageHandler().handle(message);
                            }
                        } catch (Exception e) {
                            log.warn(
                                    "[DINGTALK] card callback dispatch failed: {}",
                                    e.getMessage(),
                                    e);
                        }
                    }
                });
    }

    protected synchronized void refreshAccessTokenIfNecessary() throws Exception {
        long now = System.currentTimeMillis();
        if (!isBlank(accessToken) && accessTokenExpireAt > now + 60000L) {
            return;
        }

        GetAccessTokenRequest request =
                new GetAccessTokenRequest()
                        .setAppKey(config.getClientId())
                        .setAppSecret(config.getClientSecret());
        GetAccessTokenResponse response = oauthClient.getAccessToken(request);
        if (response == null || response.getBody() == null) {
            throw new IllegalStateException("DingTalk access token response is empty");
        }

        GetAccessTokenResponseBody body = response.getBody();
        if (isBlank(body.getAccessToken()) || body.getExpireIn() == null) {
            throw new IllegalStateException("DingTalk access token is missing");
        }

        accessToken = body.getAccessToken();
        accessTokenExpireAt = now + (body.getExpireIn() * 1000L);
    }

    private String buildMarkdownParam(String text) {
        return new ONode().set("title", resolveMarkdownTitle(text)).set("text", text).toJson();
    }

    private boolean isGroupConversation(DeliveryRequest request) {
        if ("group".equalsIgnoreCase(request.getChatType())) {
            return true;
        }
        if ("dm".equalsIgnoreCase(request.getChatType())) {
            return false;
        }
        Boolean value = conversationGroupFlags.get(request.getChatId());
        return value == null || value.booleanValue();
    }

    private String extractText(ChatbotMessage message) {
        String messageType = message.getMsgtype();
        if ("reaction".equalsIgnoreCase(messageType) || "emoji".equalsIgnoreCase(messageType)) {
            String contentText =
                    message.getContent() == null ? null : message.getContent().getContent();
            return StrUtil.blankToDefault(contentText, "reaction");
        }
        if ("audio".equalsIgnoreCase(messageType)
                && message.getContent() != null
                && notBlank(message.getContent().getRecognition())) {
            return message.getContent().getRecognition().trim();
        }
        MessageContent text = message.getText();
        if (text != null && !isBlank(text.getContent())) {
            return text.getContent().trim();
        }
        MessageContent content = message.getContent();
        if (content != null && !isBlank(content.getContent())) {
            return content.getContent().trim();
        }
        return "";
    }

    private List<MessageAttachment> extractAttachments(ChatbotMessage message) {
        List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
        MessageContent content = message.getContent();
        String msgType = StrUtil.nullToEmpty(message.getMsgtype()).toLowerCase();
        if ("picture".equals(msgType) && content != null) {
            addAttachment(
                    attachments,
                    "image",
                    content.getPictureDownloadCode(),
                    "image.jpg",
                    "image/jpeg",
                    null);
        } else if ("file".equals(msgType) && content != null) {
            addAttachment(
                    attachments,
                    "file",
                    content.getDownloadCode(),
                    content.getFileName(),
                    AttachmentCacheService.normalizeMimeType(null, content.getFileName()),
                    null);
        } else if ("video".equals(msgType) && content != null) {
            addAttachment(
                    attachments,
                    "video",
                    content.getDownloadCode(),
                    "video.mp4",
                    "video/mp4",
                    null);
        } else if ("audio".equals(msgType) && content != null) {
            addAttachment(
                    attachments,
                    "voice",
                    content.getDownloadCode(),
                    "voice.silk",
                    "audio/silk",
                    content.getRecognition());
        }
        if (content != null && content.getRichText() != null) {
            for (MessageContent item : content.getRichText()) {
                String itemType = StrUtil.nullToEmpty(item.getType()).toLowerCase();
                if ("picture".equals(itemType) || "image".equals(itemType)) {
                    addAttachment(
                            attachments,
                            "image",
                            notBlank(item.getPictureDownloadCode())
                                    ? item.getPictureDownloadCode()
                                    : item.getDownloadCode(),
                            "image.jpg",
                            "image/jpeg",
                            null);
                } else if ("file".equals(itemType)) {
                    addAttachment(
                            attachments,
                            "file",
                            item.getDownloadCode(),
                            item.getFileName(),
                            AttachmentCacheService.normalizeMimeType(null, item.getFileName()),
                            null);
                } else if ("video".equals(itemType)) {
                    addAttachment(
                            attachments,
                            "video",
                            item.getDownloadCode(),
                            "video.mp4",
                            "video/mp4",
                            null);
                } else if ("audio".equals(itemType) || "voice".equals(itemType)) {
                    addAttachment(
                            attachments,
                            "voice",
                            item.getDownloadCode(),
                            "voice.silk",
                            "audio/silk",
                            item.getRecognition());
                }
            }
        }
        return attachments;
    }

    private void addAttachment(
            List<MessageAttachment> attachments,
            String kind,
            String downloadCode,
            String fileName,
            String mimeType,
            String transcribedText) {
        if (isBlank(downloadCode)) {
            return;
        }
        try {
            String downloadUrl = resolveDownloadUrl(downloadCode);
            byte[] data =
                    BoundedAttachmentIO.downloadHutool(
                            downloadUrl, 30000, BoundedAttachmentIO.DEFAULT_MAX_BYTES);
            attachments.add(
                    attachmentCacheService.cacheBytes(
                            PlatformType.DINGTALK,
                            kind,
                            fileName,
                            mimeType,
                            false,
                            transcribedText,
                            data));
        } catch (Exception e) {
            log.warn(
                    "[DINGTALK] attachment download failed: kind={}, code={}, message={}",
                    kind,
                    downloadCode,
                    e.getMessage());
        }
    }

    private String resolveDownloadUrl(String downloadCode) throws Exception {
        RobotMessageFileDownloadHeaders headers = new RobotMessageFileDownloadHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        RobotMessageFileDownloadRequest request =
                new RobotMessageFileDownloadRequest()
                        .setDownloadCode(downloadCode)
                        .setRobotCode(config.getRobotCode());
        RobotMessageFileDownloadResponse response =
                robotClient.robotMessageFileDownloadWithOptions(
                        request, headers, new com.aliyun.teautil.models.RuntimeOptions());
        if (response == null
                || response.getBody() == null
                || isBlank(response.getBody().getDownloadUrl())) {
            throw new IllegalStateException("DingTalk download url missing");
        }
        return response.getBody().getDownloadUrl();
    }

    private void sendText(DeliveryRequest request) throws Exception {
        boolean isGroup = isGroupConversation(request);
        if (isGroup) {
            try {
                OrgGroupSendHeaders headers = new OrgGroupSendHeaders();
                headers.setXAcsDingtalkAccessToken(accessToken);

                OrgGroupSendRequest sendRequest = new OrgGroupSendRequest();
                sendRequest.setRobotCode(config.getRobotCode());
                sendRequest.setOpenConversationId(request.getChatId());
                sendRequest.setMsgKey("sampleMarkdown");
                sendRequest.setMsgParam(buildMarkdownParam(request.getText()));
                if (!isBlank(config.getCoolAppCode())) {
                    sendRequest.setCoolAppCode(config.getCoolAppCode());
                }

                OrgGroupSendResponse response =
                        robotClient.orgGroupSendWithOptions(
                                sendRequest,
                                headers,
                                new com.aliyun.teautil.models.RuntimeOptions());
                if (response == null || response.getBody() == null) {
                    throw new IllegalStateException("DingTalk group send returned empty response");
                }
                log.info(
                        "[DINGTALK:{}] sent processKey={}",
                        request.getChatId(),
                        response.getBody().getProcessQueryKey());
            } catch (TeaException e) {
                log.warn(
                        "[DINGTALK] group send failed: code={}, message={}, data={}",
                        e.getCode(),
                        e.getMessage(),
                        e.getData(),
                        e);
                throw e;
            }
        } else {
            String privateUserId = request.getUserId();
            if (isBlank(privateUserId)) {
                privateUserId =
                        channelStateRepository.get(
                                PlatformType.DINGTALK, request.getChatId(), STATE_LAST_USER_ID);
            }
            if (isBlank(privateUserId)) {
                throw new IllegalStateException(
                        "DingTalk private chat send requires userId from inbound context.");
            }
            try {
                BatchSendOTOHeaders headers = new BatchSendOTOHeaders();
                headers.setXAcsDingtalkAccessToken(accessToken);

                BatchSendOTORequest sendRequest = new BatchSendOTORequest();
                sendRequest.setRobotCode(config.getRobotCode());
                sendRequest.setUserIds(java.util.Collections.singletonList(privateUserId));
                sendRequest.setMsgKey("sampleMarkdown");
                sendRequest.setMsgParam(buildMarkdownParam(request.getText()));

                BatchSendOTOResponse response =
                        robotClient.batchSendOTOWithOptions(
                                sendRequest,
                                headers,
                                new com.aliyun.teautil.models.RuntimeOptions());
                if (response == null || response.getBody() == null) {
                    throw new IllegalStateException(
                            "DingTalk private send returned empty response");
                }
                log.info(
                        "[DINGTALK:{}] sent private batch response={}",
                        request.getUserId(),
                        response.getBody().toMap());
            } catch (TeaException e) {
                log.warn(
                        "[DINGTALK] private send failed: code={}, message={}, data={}",
                        e.getCode(),
                        e.getMessage(),
                        e.getData(),
                        e);
                throw e;
            }
        }
    }

    private void sendAttachment(DeliveryRequest request, MessageAttachment attachment)
            throws Exception {
        DeliveryContext context = resolveDeliveryContext(request);
        String fileName =
                StrUtil.blankToDefault(
                        attachment.getOriginalName(),
                        new File(attachment.getLocalPath()).getName());
        byte[] data = cn.hutool.core.io.FileUtil.readBytes(new File(attachment.getLocalPath()));
        String spaceId = resolveSpaceId(context);
        String uploadKey = getUploadInfo(context.unionId, fileName, data.length, spaceId);
        uploadFileBytes(uploadKey, data, context.unionId, spaceId, fileName);
        String dentryId =
                commitUploadedFile(uploadKey, context.unionId, spaceId, fileName, data.length);
        sendConversationFile(context, spaceId, dentryId);
        log.info("[DINGTALK:{}] native attachment sent {}", request.getChatId(), fileName);
    }

    protected boolean isAiCardRequest(DeliveryRequest request) {
        Map<String, Object> extras = request.getChannelExtras();
        if (extras == null || extras.isEmpty()) {
            return false;
        }
        if (!config.isAiCardStreamingEnabled() && isAiCardUpdateRequest(extras)) {
            return false;
        }
        String mode = stringValue(extras.get("mode"));
        return "ai_card".equalsIgnoreCase(mode)
                || "dingtalk_ai_card".equalsIgnoreCase(mode)
                || StrUtil.isNotBlank(stringValue(extras.get("cardTemplateId")));
    }

    protected void sendAiCard(DeliveryRequest request) throws Exception {
        Map<String, Object> extras =
                request.getChannelExtras() == null
                        ? Collections.<String, Object>emptyMap()
                        : request.getChannelExtras();
        if (isAiCardUpdateRequest(extras)) {
            updateAiCard(extras);
            return;
        }
        String cardTemplateId = stringValue(extras.get("cardTemplateId"));
        String cardData = jsonString(extras.get("cardData"));
        if (isBlank(cardTemplateId) || isBlank(cardData)) {
            throw new IllegalStateException(
                    "DingTalk AI card send requires channelExtras.cardTemplateId and channelExtras.cardData");
        }

        SendRobotInteractiveCardHeaders headers = new SendRobotInteractiveCardHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        SendRobotInteractiveCardRequest sendRequest = new SendRobotInteractiveCardRequest();
        sendRequest.setRobotCode(config.getRobotCode());
        sendRequest.setCardTemplateId(cardTemplateId);
        sendRequest.setCardData(cardData);
        sendRequest.setCardBizId(
                StrUtil.blankToDefault(
                        stringValue(extras.get("cardBizId")),
                        "jimuqu-card-" + System.currentTimeMillis()));

        SendRobotInteractiveCardRequest.SendRobotInteractiveCardRequestSendOptions sendOptions =
                new SendRobotInteractiveCardRequest.SendRobotInteractiveCardRequestSendOptions();
        if (extras.containsKey("atAll")) {
            sendOptions.setAtAll(
                    Boolean.valueOf(Boolean.parseBoolean(String.valueOf(extras.get("atAll")))));
        }
        if (extras.containsKey("cardPropertyJson")) {
            sendOptions.setCardPropertyJson(stringValue(extras.get("cardPropertyJson")));
        }
        if (extras.containsKey("atUserListJson")) {
            sendOptions.setAtUserListJson(stringValue(extras.get("atUserListJson")));
        }
        if (extras.containsKey("receiverListJson")) {
            sendOptions.setReceiverListJson(stringValue(extras.get("receiverListJson")));
        }
        if (hasAnySendOptions(sendOptions)) {
            sendRequest.setSendOptions(sendOptions);
        }

        if (isGroupConversation(request)) {
            sendRequest.setOpenConversationId(request.getChatId());
        } else {
            String receiver = request.getUserId();
            if (isBlank(receiver)) {
                receiver =
                        channelStateRepository.get(
                                PlatformType.DINGTALK, request.getChatId(), STATE_LAST_USER_ID);
            }
            if (isBlank(receiver)) {
                throw new IllegalStateException(
                        "DingTalk AI card send requires userId from inbound context for private chat");
            }
            sendRequest.setSingleChatReceiver(receiver);
        }

        if (extras.containsKey("callbackUrl")) {
            sendRequest.setCallbackUrl(stringValue(extras.get("callbackUrl")));
        }

        SendRobotInteractiveCardResponse response =
                imClient.sendRobotInteractiveCardWithOptions(
                        sendRequest, headers, new com.aliyun.teautil.models.RuntimeOptions());
        if (response == null
                || response.getBody() == null
                || isBlank(response.getBody().getProcessQueryKey())) {
            throw new IllegalStateException("DingTalk AI card send failed");
        }
        if (StrUtil.isNotBlank(request.getThreadId())) {
            cardInstanceBindings.put(
                    response.getBody().getProcessQueryKey(), request.getThreadId().trim());
        }
        log.info(
                "[DINGTALK:{}] ai card sent processKey={}",
                request.getChatId(),
                response.getBody().getProcessQueryKey());
    }

    private boolean isAiCardUpdateRequest(Map<String, Object> extras) {
        if (extras == null || extras.isEmpty()) {
            return false;
        }
        String mode = stringValue(extras.get("mode"));
        if ("ai_card_update".equalsIgnoreCase(mode)
                || "dingtalk_ai_card_update".equalsIgnoreCase(mode)) {
            return true;
        }
        return Boolean.parseBoolean(String.valueOf(extras.get("updateExisting")));
    }

    private void updateAiCard(Map<String, Object> extras) throws Exception {
        String cardBizId = stringValue(extras.get("cardBizId"));
        String cardData = jsonString(extras.get("cardData"));
        if (isBlank(cardBizId) || isBlank(cardData)) {
            throw new IllegalStateException(
                    "DingTalk AI card update requires channelExtras.cardBizId and channelExtras.cardData");
        }

        UpdateRobotInteractiveCardHeaders headers = new UpdateRobotInteractiveCardHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        UpdateRobotInteractiveCardRequest request = new UpdateRobotInteractiveCardRequest();
        request.setCardBizId(cardBizId);
        request.setCardData(cardData);
        UpdateRobotInteractiveCardRequest.UpdateRobotInteractiveCardRequestUpdateOptions options =
                new UpdateRobotInteractiveCardRequest
                        .UpdateRobotInteractiveCardRequestUpdateOptions();
        options.setUpdateCardDataByKey(Boolean.TRUE);
        request.setUpdateOptions(options);

        UpdateRobotInteractiveCardResponse response =
                imClient.updateRobotInteractiveCardWithOptions(
                        request, headers, new com.aliyun.teautil.models.RuntimeOptions());
        if (response == null || response.getBody() == null) {
            throw new IllegalStateException("DingTalk AI card update failed");
        }
        log.info("[DINGTALK] ai card updated cardBizId={}", cardBizId);
    }

    private String uploadMedia(MessageAttachment attachment) {
        File file = new File(attachment.getLocalPath());
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "DingTalk attachment file not found: " + attachment.getLocalPath());
        }
        String kind =
                AttachmentCacheService.normalizeKind(
                        attachment.getKind(),
                        attachment.getOriginalName(),
                        attachment.getMimeType());
        String type = "image".equals(kind) ? "image" : ("voice".equals(kind) ? "voice" : "file");
        String response =
                HttpRequest.post(
                                MEDIA_UPLOAD_URL + "?access_token=" + accessToken + "&type=" + type)
                        .form("media", file)
                        .timeout(30000)
                        .execute()
                        .body();
        ONode node = ONode.ofJson(response);
        int errCode = node.get("errcode").getInt(0);
        if (errCode != 0) {
            throw new IllegalStateException("DingTalk media upload failed: " + response);
        }
        String mediaId = node.get("media_id").getString();
        if (isBlank(mediaId)) {
            throw new IllegalStateException("DingTalk media upload missing media_id");
        }
        return mediaId;
    }

    private ONode buildWebhookMediaPayload(
            String kind, String mediaId, MessageAttachment attachment) {
        if ("image".equals(kind)) {
            return new ONode()
                    .set("msgtype", "image")
                    .getOrNew("image")
                    .set("media_id", mediaId)
                    .parent()
                    .asObject();
        }
        if ("voice".equals(kind)
                && (StrUtil.endWithIgnoreCase(attachment.getOriginalName(), ".ogg")
                        || StrUtil.endWithIgnoreCase(attachment.getOriginalName(), ".amr"))) {
            return new ONode()
                    .set("msgtype", "voice")
                    .getOrNew("voice")
                    .set("media_id", mediaId)
                    .set("duration", "1")
                    .parent()
                    .asObject();
        }
        return new ONode()
                .set("msgtype", "file")
                .getOrNew("file")
                .set("media_id", mediaId)
                .parent()
                .asObject();
    }

    private void rememberSessionWebhook(String chatId, String sessionWebhook, Long expiredTime) {
        if (isBlank(chatId) || isBlank(sessionWebhook)) {
            return;
        }
        long expiresAt = expiredTime == null ? 0L : expiredTime.longValue();
        sessionWebhooks.put(chatId, new SessionWebhookState(sessionWebhook, expiresAt));
        try {
            channelStateRepository.put(
                    PlatformType.DINGTALK, chatId, STATE_SESSION_WEBHOOK, sessionWebhook);
            channelStateRepository.put(
                    PlatformType.DINGTALK,
                    chatId,
                    STATE_SESSION_WEBHOOK_EXPIRES_AT,
                    String.valueOf(expiresAt));
        } catch (Exception ignored) {
        }
    }

    private boolean allowInbound(
            ChatbotMessage message, String conversationId, String chatType, String userId) {
        if ("group".equals(chatType)) {
            if (!Boolean.TRUE.equals(message.getInAtList())) {
                return false;
            }
            String groupPolicy =
                    StrUtil.blankToDefault(
                                    config.getGroupPolicy(),
                                    GatewayBehaviorConstants.GROUP_POLICY_OPEN)
                            .toLowerCase();
            if (GatewayBehaviorConstants.GROUP_POLICY_DISABLED.equals(groupPolicy)) {
                return false;
            }
            if (GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST.equals(groupPolicy)
                    && !contains(config.getGroupAllowedUsers(), conversationId)) {
                return false;
            }
            return true;
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

    private GatewayMessage toCardCallbackMessage(Map<String, Object> payload) {
        ONode node = ONode.ofJson(ONode.serialize(payload));
        String processKey =
                firstNonBlank(
                        node.get("processQueryKey").getString(),
                        node.get("process_query_key").getString(),
                        node.get("outTrackId").getString(),
                        node.get("out_track_id").getString(),
                        node.get("cardBizId").getString(),
                        node.get("card_biz_id").getString());
        String chatId =
                firstNonBlank(
                        node.get("openConversationId").getString(),
                        node.get("open_conversation_id").getString(),
                        findNested(node, "conversation", "openConversationId"),
                        cardInstanceBindings.get(processKey));
        String userId =
                firstNonBlank(
                        node.get("userId").getString(),
                        node.get("staffId").getString(),
                        node.get("unionId").getString(),
                        findNested(node, "operator", "staffId"),
                        findNested(node, "operator", "userId"));
        if (isBlank(chatId)) {
            chatId = "dingtalk-card";
        }
        String text = "Card action: " + node.toJson();
        GatewayMessage message = new GatewayMessage(PlatformType.DINGTALK, chatId, userId, text);
        message.setChatType(
                conversationGroupFlags.containsKey(chatId)
                                && Boolean.TRUE.equals(conversationGroupFlags.get(chatId))
                        ? GatewayBehaviorConstants.CHAT_TYPE_GROUP
                        : GatewayBehaviorConstants.CHAT_TYPE_DM);
        message.setChatName(chatId);
        message.setUserName(StrUtil.blankToDefault(userId, "dingtalk-card"));
        message.setThreadId(
                StrUtil.blankToDefault(processKey, "card-callback-" + System.currentTimeMillis()));
        return message;
    }

    private String findNested(ONode node, String parentKey, String childKey) {
        ONode parent = node.get(parentKey);
        if (parent == null || parent.isNull()) {
            return null;
        }
        return parent.get(childKey).getString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean hasAnySendOptions(
            SendRobotInteractiveCardRequest.SendRobotInteractiveCardRequestSendOptions options) {
        return options != null
                && (options.getAtAll() != null
                        || notBlank(options.getAtUserListJson())
                        || notBlank(options.getCardPropertyJson())
                        || notBlank(options.getReceiverListJson()));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String jsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return ((String) value).trim();
        }
        return ONode.serialize(value);
    }

    private DeliveryContext resolveDeliveryContext(DeliveryRequest request) throws Exception {
        DeliveryContext context = new DeliveryContext();
        context.chatId = request.getChatId();
        context.group = isGroupConversation(request);
        context.unionId =
                channelStateRepository.get(
                        PlatformType.DINGTALK, request.getChatId(), STATE_LAST_UNION_ID);
        if (isBlank(context.unionId)) {
            throw new IllegalStateException(
                    "DingTalk attachment send requires a recent inbound conversation context to resolve unionId");
        }
        return context;
    }

    private String resolveSpaceId(DeliveryContext context) throws Exception {
        String cached =
                channelStateRepository.get(
                        PlatformType.DINGTALK, context.chatId, STATE_LAST_SPACE_ID);
        if (notBlank(cached)) {
            return cached;
        }
        GetSpaceHeaders headers = new GetSpaceHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        GetSpaceRequest request =
                new GetSpaceRequest()
                        .setOpenConversationId(context.chatId)
                        .setUnionId(context.unionId);
        GetSpaceResponse response =
                convFileClient.getSpaceWithOptions(
                        request, headers, new com.aliyun.teautil.models.RuntimeOptions());
        if (response == null
                || response.getBody() == null
                || response.getBody().getSpace() == null
                || isBlank(response.getBody().getSpace().getSpaceId())) {
            throw new IllegalStateException("DingTalk conversation space lookup failed");
        }
        String spaceId = response.getBody().getSpace().getSpaceId();
        channelStateRepository.put(
                PlatformType.DINGTALK, context.chatId, STATE_LAST_SPACE_ID, spaceId);
        return spaceId;
    }

    private String getUploadInfo(String unionId, String fileName, int size, String spaceId)
            throws Exception {
        GetFileUploadInfoHeaders headers = new GetFileUploadInfoHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        GetFileUploadInfoRequest.GetFileUploadInfoRequestOption option =
                new GetFileUploadInfoRequest.GetFileUploadInfoRequestOption()
                        .setStorageDriver("DINGTALK")
                        .setPreCheckParam(
                                new GetFileUploadInfoRequest
                                                .GetFileUploadInfoRequestOptionPreCheckParam()
                                        .setName(fileName)
                                        .setSize(Long.valueOf(size)));
        GetFileUploadInfoRequest request =
                new GetFileUploadInfoRequest()
                        .setProtocol("HEADER_SIGNATURE")
                        .setUnionId(unionId)
                        .setOption(option);
        GetFileUploadInfoResponse response =
                storageClient.getFileUploadInfoWithOptions(
                        spaceId, request, headers, new com.aliyun.teautil.models.RuntimeOptions());
        if (response == null
                || response.getBody() == null
                || isBlank(response.getBody().getUploadKey())) {
            throw new IllegalStateException("DingTalk upload init failed");
        }
        DeliveryUploadState.current.set(response.getBody());
        return response.getBody().getUploadKey();
    }

    private void uploadFileBytes(
            String uploadKey, byte[] data, String unionId, String spaceId, String fileName) {
        com.aliyun.dingtalkstorage_2_0.models.GetFileUploadInfoResponseBody body =
                DeliveryUploadState.current.get();
        if (body == null
                || body.getHeaderSignatureInfo() == null
                || body.getHeaderSignatureInfo().getResourceUrls() == null
                || body.getHeaderSignatureInfo().getResourceUrls().isEmpty()) {
            throw new IllegalStateException("DingTalk upload info missing signed resource url");
        }
        String uploadUrl = body.getHeaderSignatureInfo().getResourceUrls().get(0);
        HttpRequest request = HttpRequest.put(uploadUrl).timeout(120000).body(data);
        Map<String, String> headers = body.getHeaderSignatureInfo().getHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.header(entry.getKey(), entry.getValue());
            }
        }
        HttpResponse response = request.execute();
        try {
            if (response.getStatus() >= 400) {
                throw new IllegalStateException(
                        "DingTalk upload bytes failed: HTTP " + response.getStatus());
            }
        } finally {
            response.close();
        }
    }

    private String commitUploadedFile(
            String uploadKey, String unionId, String spaceId, String fileName, int size)
            throws Exception {
        CommitFileHeaders headers = new CommitFileHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        CommitFileRequest request =
                new CommitFileRequest()
                        .setName(fileName)
                        .setUploadKey(uploadKey)
                        .setUnionId(unionId)
                        .setOption(
                                new CommitFileRequest.CommitFileRequestOption()
                                        .setSize(Long.valueOf(size))
                                        .setConflictStrategy("AUTO_RENAME"));
        CommitFileResponse response =
                storageClient.commitFileWithOptions(
                        spaceId, request, headers, new com.aliyun.teautil.models.RuntimeOptions());
        if (response == null
                || response.getBody() == null
                || response.getBody().getDentry() == null
                || isBlank(response.getBody().getDentry().getId())) {
            throw new IllegalStateException("DingTalk commit file failed");
        }
        return response.getBody().getDentry().getId();
    }

    private void sendConversationFile(DeliveryContext context, String spaceId, String dentryId)
            throws Exception {
        SendHeaders headers = new SendHeaders();
        headers.setXAcsDingtalkAccessToken(accessToken);
        SendRequest request =
                new SendRequest()
                        .setOpenConversationId(context.chatId)
                        .setSpaceId(spaceId)
                        .setDentryId(dentryId)
                        .setUnionId(context.unionId);
        SendResponse response =
                convFileClient.sendWithOptions(
                        request, headers, new com.aliyun.teautil.models.RuntimeOptions());
        if (response == null
                || response.getBody() == null
                || response.getBody().getFile() == null
                || isBlank(response.getBody().getFile().getId())) {
            throw new IllegalStateException("DingTalk conversation file send failed");
        }
    }

    private boolean isBlank(String value) {
        return StrUtil.isBlank(value);
    }

    private boolean notBlank(String value) {
        return !isBlank(value);
    }

    private String resolveMarkdownTitle(String content) {
        if (isBlank(content)) {
            return "solon-claw";
        }
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalized = line.replaceFirst("^[#>*`\\-\\s]+", "").trim();
            if (!isBlank(normalized)) {
                return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
            }
        }
        return "solon-claw";
    }

    private static class SessionWebhookState {
        private final String url;
        private final long expiresAt;

        private SessionWebhookState(String url, long expiresAt) {
            this.url = url;
            this.expiresAt = expiresAt;
        }

        private boolean isValid() {
            return expiresAt <= 0 || expiresAt > System.currentTimeMillis();
        }
    }

    private static class DeliveryContext {
        private String chatId;
        private String unionId;
        private boolean group;
    }

    private static class DeliveryUploadState {
        private static final ThreadLocal<
                        com.aliyun.dingtalkstorage_2_0.models.GetFileUploadInfoResponseBody>
                current =
                        new ThreadLocal<
                                com.aliyun.dingtalkstorage_2_0.models
                                        .GetFileUploadInfoResponseBody>();
    }
}
