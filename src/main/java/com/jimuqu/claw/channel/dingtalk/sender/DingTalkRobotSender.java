package com.jimuqu.claw.channel.dingtalk.sender;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.dingtalkrobot_1_0.Client;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOHeaders;
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTORequest;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders;
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest;
import com.aliyun.teautil.models.RuntimeOptions;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.channel.dingtalk.service.DingTalkAccessTokenService;
import com.jimuqu.claw.config.props.DingTalkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.regex.Pattern;

/**
 * 基于钉钉机器人 OpenAPI 发送群聊和私聊消息。
 */
public class DingTalkRobotSender {
    private static final int MAX_MARKDOWN_TEXT_LENGTH = 5000;
    private static final Pattern MARKDOWN_PREFIX = Pattern.compile("^[#>*`\\-\\s]+");
    private static final String TRUNCATED_SUFFIX = "\n\n[消息过长，已截断]";
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(DingTalkRobotSender.class);
    /** access_token 服务。 */
    private final DingTalkAccessTokenService accessTokenService;
    /** 钉钉配置。 */
    private final DingTalkProperties properties;
    /** 机器人 OpenAPI 客户端。 */
    private final Client robotClient;

    /**
     * 创建钉钉机器人发送服务。
     *
     * @param accessTokenService access_token 服务
     * @param properties 钉钉配置
     * @throws Exception 创建底层客户端时的异常
     */
    public DingTalkRobotSender(
            DingTalkAccessTokenService accessTokenService,
            DingTalkProperties properties
    ) throws Exception {
        this(accessTokenService, properties, createRobotClient());
    }

    /**
     * 使用显式客户端创建发送服务。
     *
     * @param accessTokenService access_token 服务
     * @param properties 钉钉配置
     * @param robotClient 机器人客户端
     */
    public DingTalkRobotSender(
            DingTalkAccessTokenService accessTokenService,
            DingTalkProperties properties,
            Client robotClient
    ) {
        this.accessTokenService = accessTokenService;
        this.properties = properties;
        this.robotClient = robotClient;
    }

    /**
     * 向指定回复目标发送文本。
     *
     * @param replyTarget 回复目标
     * @param content 文本内容
     */
    public DeliveryResult sendText(ReplyTarget replyTarget, String content) {
        DeliveryResult result = new DeliveryResult();
        result.setChannelType(com.jimuqu.claw.agent.model.enums.ChannelType.DINGTALK);
        result.setOriginalLength(content == null ? 0 : content.length());
        String normalizedContent = normalizeMarkdownContent(content);
        if (replyTarget == null || StrUtil.isBlank(normalizedContent)) {
            result.setDelivered(false);
            result.setFinalLength(normalizedContent == null ? 0 : normalizedContent.length());
            result.setMessage("empty content or missing replyTarget");
            return result;
        }

        if (!accessTokenService.isReady()) {
            log.warn("Skip DingTalk send because access token is not ready.");
            result.setDelivered(false);
            result.setFinalLength(normalizedContent.length());
            result.setMessage("access token is not ready");
            return result;
        }

        if (StrUtil.isBlank(properties.getRobotCode())) {
            log.warn("Skip DingTalk send because robotCode is missing.");
            result.setDelivered(false);
            result.setFinalLength(normalizedContent.length());
            result.setMessage("robotCode is missing");
            return result;
        }

        try {
            if (!StrUtil.equals(content, normalizedContent)) {
                log.warn(
                        "Trim DingTalk markdown message to fit length limit. originalLength={}, finalLength={}",
                        content == null ? 0 : content.length(),
                        normalizedContent.length()
                );
                result.setTruncated(true);
            }

            if (replyTarget.getConversationType() == ConversationType.GROUP) {
                sendGroup(replyTarget, normalizedContent);
            } else {
                sendPrivate(replyTarget, normalizedContent);
            }
            result.setDelivered(true);
            result.setFinalLength(normalizedContent.length());
            result.setMessage("sent");
        } catch (Exception exception) {
            log.warn("Failed to send DingTalk message: {}", exception.getMessage(), exception);
            result.setDelivered(false);
            result.setFinalLength(normalizedContent.length());
            result.setMessage(exception.getMessage());
        }
        return result;
    }

    /**
     * 向群聊发送消息。
     *
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @throws Exception 发送异常
     */
    private void sendGroup(ReplyTarget replyTarget, String content) throws Exception {
        if (StrUtil.isBlank(replyTarget.getConversationId())) {
            log.warn("Skip DingTalk group send because conversationId is missing.");
            return;
        }

        OrgGroupSendHeaders headers = new OrgGroupSendHeaders();
        headers.setXAcsDingtalkAccessToken(accessTokenService.getAccessToken());

        OrgGroupSendRequest request = new OrgGroupSendRequest();
        request.setMsgKey(resolveMsgKey(content));
        request.setRobotCode(properties.getRobotCode());
        request.setOpenConversationId(replyTarget.getConversationId());
        request.setMsgParam(messageParam(content));

        robotClient.orgGroupSendWithOptions(request, headers, new RuntimeOptions());
    }

    /**
     * 向私聊发送消息。
     *
     * @param replyTarget 回复目标
     * @param content 文本内容
     * @throws Exception 发送异常
     */
    private void sendPrivate(ReplyTarget replyTarget, String content) throws Exception {
        if (StrUtil.isBlank(replyTarget.getUserId())) {
            log.warn("Skip DingTalk private send because userId is missing.");
            return;
        }

        BatchSendOTOHeaders headers = new BatchSendOTOHeaders();
        headers.setXAcsDingtalkAccessToken(accessTokenService.getAccessToken());

        BatchSendOTORequest request = new BatchSendOTORequest();
        request.setMsgKey(resolveMsgKey(content));
        request.setRobotCode(properties.getRobotCode());
        request.setUserIds(Collections.singletonList(replyTarget.getUserId()));
        request.setMsgParam(messageParam(content));

        log.info("发送钉钉消息: {}", JSONUtil.toJsonPrettyStr(request));
        robotClient.batchSendOTOWithOptions(request, headers, new RuntimeOptions());
    }

    /**
     * 返回钉钉消息类型键。
     *
     * @param content 文本内容
     * @return 消息类型键
     */
    public String resolveMsgKey(String content) {
        return "sampleMarkdown";
    }

    /**
     * 将文本内容包装成钉钉消息参数 JSON。
     *
     * @param content 文本内容
     * @return JSON 字符串
     */
    private String messageParam(String content) {
        return markdownMessageParam(content);
    }

    /**
     * 组装 markdown 消息参数。
     *
     * @param content 回复内容
     * @return JSON 字符串
     */
    public String markdownMessageParam(String content) {
        String normalizedContent = normalizeMarkdownContent(content);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", resolveMarkdownTitle(normalizedContent));
        jsonObject.put("text", normalizedContent);
        return jsonObject.toJSONString();
    }

    /**
     * 将 markdown 文本裁剪到钉钉机器人安全范围内。
     *
     * @param content 原始文本
     * @return 适合发送的 markdown 文本
     */
    public String normalizeMarkdownContent(String content) {
        String normalized = StrUtil.blankToDefault(content, "");
        if (normalized.isEmpty()) {
            return normalized;
        }

        if (normalized.length() <= MAX_MARKDOWN_TEXT_LENGTH) {
            return normalized;
        }

        int maxBodyLength = Math.max(0, MAX_MARKDOWN_TEXT_LENGTH - TRUNCATED_SUFFIX.length());
        return normalized.substring(0, maxBodyLength) + TRUNCATED_SUFFIX;
    }

    /**
     * 从正文中提取 markdown 标题。
     *
     * @param content 回复内容
     * @return 标题文本
     */
    public String resolveMarkdownTitle(String content) {
        if (StrUtil.isBlank(content)) {
            return "SolonClaw";
        }

        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalized = MARKDOWN_PREFIX.matcher(line).replaceFirst("").trim();
            if (!normalized.isEmpty()) {
                return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
            }
        }

        return "SolonClaw";
    }
    /**
     * 创建钉钉机器人客户端。
     *
     * @return 机器人客户端
     * @throws Exception 创建异常
     */
    private static Client createRobotClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new Client(config);
    }
}



