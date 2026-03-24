package com.jimuqu.claw.channel.weixin.sender;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;
import com.jimuqu.claw.channel.weixin.service.WeixinAccountStoreService;
import com.jimuqu.claw.channel.weixin.service.WeixinApiGateway;
import com.jimuqu.claw.config.props.WeixinProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 负责向微信用户发送文本消息。
 */
public class WeixinRobotSender {
    /** 最大文本长度。 */
    private static final int MAX_TEXT_LENGTH = 4000;
    /** 截断提示。 */
    private static final String TRUNCATED_SUFFIX = "\n\n[消息过长，已截断]";
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(WeixinRobotSender.class);

    /** 微信网关。 */
    private final WeixinApiGateway apiGateway;
    /** 微信账号存储。 */
    private final WeixinAccountStoreService accountStoreService;
    /** 微信配置。 */
    private final WeixinProperties properties;

    /**
     * 创建微信发送器。
     *
     * @param apiGateway 微信网关
     * @param accountStoreService 账号存储
     * @param properties 微信配置
     */
    public WeixinRobotSender(
            WeixinApiGateway apiGateway,
            WeixinAccountStoreService accountStoreService,
            WeixinProperties properties
    ) {
        this.apiGateway = apiGateway;
        this.accountStoreService = accountStoreService;
        this.properties = properties;
    }

    /**
     * 发送一条微信文本消息。
     *
     * @param outboundEnvelope 出站消息
     * @return 发送结果
     */
    public DeliveryResult send(OutboundEnvelope outboundEnvelope) {
        DeliveryResult result = new DeliveryResult();
        result.setChannelType(ChannelType.WEIXIN);
        result.setOriginalLength(outboundEnvelope == null || outboundEnvelope.getContent() == null ? 0 : outboundEnvelope.getContent().length());

        if (outboundEnvelope == null || outboundEnvelope.getReplyTarget() == null) {
            result.setDelivered(false);
            result.setMessage("missing reply target");
            return result;
        }

        ReplyTarget replyTarget = outboundEnvelope.getReplyTarget();
        String text = normalizeContent(outboundEnvelope);
        result.setFinalLength(text.length());
        if (StrUtil.isBlank(text)) {
            result.setDelivered(false);
            result.setMessage("empty content");
            return result;
        }

        String accountId = replyTarget.getChannelInstanceId();
        if (StrUtil.isBlank(accountId)) {
            result.setDelivered(false);
            result.setMessage("channelInstanceId is missing");
            return result;
        }

        WeixinAccount account = accountStoreService.loadAccount(accountId);
        if (account == null || StrUtil.isBlank(account.getToken())) {
            result.setDelivered(false);
            result.setMessage("weixin account not configured");
            return result;
        }

        String toUserId = StrUtil.blankToDefault(StrUtil.trim(replyTarget.getUserId()), StrUtil.trim(replyTarget.getConversationId()));
        if (StrUtil.isBlank(toUserId)) {
            result.setDelivered(false);
            result.setMessage("userId is missing");
            return result;
        }

        if (StrUtil.isBlank(replyTarget.getContextToken())) {
            result.setDelivered(false);
            result.setMessage("contextToken is missing");
            return result;
        }

        try {
            apiGateway.sendTextMessage(
                    StrUtil.blankToDefault(account.getBaseUrl(), properties.getBaseUrl()),
                    account.getToken(),
                    toUserId,
                    replyTarget.getContextToken(),
                    text
            );
            result.setDelivered(true);
            result.setSegmentCount(1);
            if (text.endsWith(TRUNCATED_SUFFIX)) {
                result.setTruncated(true);
            }
            result.setMessage("sent");
        } catch (RuntimeException runtimeException) {
            log.warn("Failed to send Weixin message: {}", runtimeException.getMessage(), runtimeException);
            result.setDelivered(false);
            result.setMessage(runtimeException.getMessage());
        }
        return result;
    }

    private String normalizeContent(OutboundEnvelope outboundEnvelope) {
        String content = StrUtil.blankToDefault(outboundEnvelope.getContent(), "");
        if (outboundEnvelope.getMedia() != null && !outboundEnvelope.getMedia().isEmpty()) {
            StringBuilder builder = new StringBuilder(content);
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("当前微信渠道暂未启用媒体发送，以下附件已按文本说明忽略：\n");
            for (String media : outboundEnvelope.getMedia()) {
                builder.append("- ").append(media).append('\n');
            }
            content = builder.toString().trim();
        }
        if (content.length() <= MAX_TEXT_LENGTH) {
            return content;
        }
        int maxBodyLength = Math.max(0, MAX_TEXT_LENGTH - TRUNCATED_SUFFIX.length());
        return content.substring(0, maxBodyLength) + TRUNCATED_SUFFIX;
    }
}
