package com.jimuqu.solon.claw.gateway.service;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.AgentRunCancelledException;
import com.jimuqu.solon.claw.core.service.CommandService;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.core.service.SkillLearningService;
import com.jimuqu.solon.claw.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.solon.claw.support.constants.GatewayCommandConstants;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 网关主入口服务，负责把消息分流到授权、命令和对话主链。 */
@RequiredArgsConstructor
public class DefaultGatewayService {
    /** 网关日志器。 */
    private static final Logger log = LoggerFactory.getLogger(DefaultGatewayService.class);

    /** 渠道消息去重窗口，单位毫秒。 */
    private static final long DUPLICATE_WINDOW_MILLIS = 10L * 60L * 1000L;

    /** 命令服务。 */
    private final CommandService commandService;

    /** 对话编排器。 */
    private final ConversationOrchestrator conversationOrchestrator;

    /** 渠道投递服务。 */
    private final DeliveryService deliveryService;

    /** 会话仓储。 */
    private final SessionRepository sessionRepository;

    /** 授权服务。 */
    private final GatewayAuthorizationService gatewayAuthorizationService;

    /** 任务后自动学习服务。 */
    private final SkillLearningService skillLearningService;

    /** 进程内最近已处理的消息键，用于抑制渠道重复投递。 */
    private final ConcurrentMap<String, Long> recentMessageKeys =
            new ConcurrentHashMap<String, Long>();

    /**
     * 处理单条统一网关消息。
     *
     * @param message 渠道统一消息
     * @return 网关处理结果
     */
    public GatewayReply handle(GatewayMessage message) throws Exception {
        if (message == null) {
            return GatewayReply.error("消息体不能为空。");
        }

        pruneDuplicateKeys();
        String messageKey = messageKey(message);
        if (isDuplicate(messageKey)) {
            log.info("Ignore duplicate gateway message: {}", messageKey);
            return null;
        }

        boolean authorized = false;
        try {
            GatewayReply preAuth = gatewayAuthorizationService.preAuthorize(message);
            if (preAuth != null) {
                safeDeliver(message, preAuth);
                return preAuth;
            }

            String text = message.getText() == null ? "" : message.getText().trim();
            authorized = gatewayAuthorizationService.isAuthorized(message);
            if (!authorized) {
                return null;
            }

            GatewayReply reply;
            if (text.startsWith(GatewayCommandConstants.COMMAND_PREFIX)) {
                reply = commandService.handle(message, text);
                if (reply != null) {
                    reply.setCommandHandled(true);
                }
            } else {
                reply = conversationOrchestrator.handleIncoming(message);
            }

            if (reply != null) {
                safeDeliver(message, reply);
                safeScheduleLearning(message, reply);
            }
            return reply;
        } catch (Exception e) {
            if (messageKey != null) {
                recentMessageKeys.remove(messageKey);
            }
            Throwable cause = rootCause(e);
            if (cause instanceof AgentRunCancelledException) {
                GatewayReply cancelledReply = GatewayReply.ok(cause.getMessage());
                if (authorized) {
                    safeDeliver(message, cancelledReply);
                }
                return cancelledReply;
            }
            log.warn(
                    "Gateway handle failed: platform={}, chatId={}, userId={}, text={}",
                    message.getPlatform(),
                    message.getChatId(),
                    message.getUserId(),
                    message.getText(),
                    e);
            GatewayReply errorReply = GatewayReply.error("处理消息失败：" + safeMessage(e));
            if (authorized) {
                safeDeliver(message, errorReply);
            }
            return errorReply;
        }
    }

    /** 安全投递当前回复，不让渠道发送失败打断主链。 */
    private void safeDeliver(GatewayMessage message, GatewayReply reply) {
        if (reply == null
                || StrUtil.isBlank(reply.getContent())
                        && (reply.getChannelExtras() == null
                                || reply.getChannelExtras().isEmpty())) {
            return;
        }
        try {
            DeliveryRequest request = new DeliveryRequest();
            request.setPlatform(message.getPlatform());
            request.setChatId(message.getChatId());
            request.setUserId(message.getUserId());
            request.setChatType(message.getChatType());
            request.setThreadId(message.getThreadId());
            request.setText(reply.getContent());
            if (reply.getChannelExtras() != null) {
                request.getChannelExtras().putAll(reply.getChannelExtras());
            }
            deliveryService.deliver(request);
        } catch (Exception e) {
            log.warn(
                    "Gateway delivery failed: platform={}, chatId={}, userId={}",
                    message.getPlatform(),
                    message.getChatId(),
                    message.getUserId(),
                    e);
        }
    }

    /** 安全触发后台学习，不让后台线程调度问题影响当前回复。 */
    private void safeScheduleLearning(GatewayMessage message, GatewayReply reply) {
        if (reply == null
                || reply.isCommandHandled()
                || reply.isError()
                || reply.getSessionId() == null) {
            return;
        }
        try {
            SessionRecord session = sessionRepository.findById(reply.getSessionId());
            if (session != null) {
                skillLearningService.schedulePostReplyLearning(session, message, reply);
            }
        } catch (Exception e) {
            log.warn("Post-reply learning schedule failed: sessionId={}", reply.getSessionId(), e);
        }
    }

    /** 生成用于重复消息抑制的键。 */
    private String messageKey(GatewayMessage message) {
        if (StrUtil.isBlank(message.getThreadId())) {
            return null;
        }
        return String.valueOf(message.getPlatform()) + ":" + message.getThreadId().trim();
    }

    /** 记录并判断是否为重复消息。 */
    private boolean isDuplicate(String messageKey) {
        if (messageKey == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long previous = recentMessageKeys.putIfAbsent(messageKey, now);
        if (previous == null) {
            return false;
        }
        if (now - previous < DUPLICATE_WINDOW_MILLIS) {
            return true;
        }
        recentMessageKeys.put(messageKey, now);
        return false;
    }

    /** 清理过期的重复消息键，避免进程内表无限增长。 */
    private void pruneDuplicateKeys() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, Long> entry : recentMessageKeys.entrySet()) {
            if (now - entry.getValue() >= DUPLICATE_WINDOW_MILLIS) {
                recentMessageKeys.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 提炼用户可见错误信息。 */
    private String safeMessage(Exception e) {
        Throwable cause = rootCause(e);
        if (cause instanceof InterruptedException) {
            return "当前操作被中断，请重试一次。";
        }
        String message = cause == null ? null : cause.getMessage();
        if (StrUtil.isBlank(message)) {
            message = e.getMessage();
        }
        return StrUtil.isBlank(message) ? e.getClass().getSimpleName() : message.trim();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
