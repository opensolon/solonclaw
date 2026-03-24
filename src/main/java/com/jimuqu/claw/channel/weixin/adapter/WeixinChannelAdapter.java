package com.jimuqu.claw.channel.weixin.adapter;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.impl.AgentRuntimeService;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;
import com.jimuqu.claw.channel.weixin.model.WeixinGetUpdatesResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinMessage;
import com.jimuqu.claw.channel.weixin.model.WeixinMessageItem;
import com.jimuqu.claw.channel.weixin.sender.WeixinRobotSender;
import com.jimuqu.claw.channel.weixin.service.WeixinAccountStoreService;
import com.jimuqu.claw.channel.weixin.service.WeixinApiGateway;
import com.jimuqu.claw.config.props.WeixinProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 负责通过长轮询接入微信 Bot，并映射到统一运行时。
 */
public class WeixinChannelAdapter implements ChannelAdapter {
    /** 用户消息类型。 */
    private static final int MESSAGE_TYPE_USER = 1;
    /** Bot 消息类型。 */
    private static final int MESSAGE_TYPE_BOT = 2;
    /** 文本消息项。 */
    private static final int ITEM_TYPE_TEXT = 1;
    /** 图片消息项。 */
    private static final int ITEM_TYPE_IMAGE = 2;
    /** 语音消息项。 */
    private static final int ITEM_TYPE_VOICE = 3;
    /** 文件消息项。 */
    private static final int ITEM_TYPE_FILE = 4;
    /** 视频消息项。 */
    private static final int ITEM_TYPE_VIDEO = 5;
    /** 会话过期错误码。 */
    private static final int SESSION_EXPIRED_ERRCODE = -14;
    /** 最多连续失败次数。 */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    /** 快速重试延迟。 */
    private static final long RETRY_DELAY_MS = 2000L;
    /** 熔断回退延迟。 */
    private static final long BACKOFF_DELAY_MS = 30000L;
    /** 会话过期暂停时间。 */
    private static final long SESSION_PAUSE_MS = TimeUnit.HOURS.toMillis(1);
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(WeixinChannelAdapter.class);

    /** 运行时服务。 */
    private final AgentRuntimeService agentRuntimeService;
    /** 微信发送器。 */
    private final WeixinRobotSender weixinRobotSender;
    /** 微信账号存储。 */
    private final WeixinAccountStoreService accountStoreService;
    /** 微信 API 网关。 */
    private final WeixinApiGateway apiGateway;
    /** 微信配置。 */
    private final WeixinProperties properties;
    /** 监控任务表。 */
    private final Map<String, Future<?>> monitorTasks = new ConcurrentHashMap<String, Future<?>>();
    /** 会话暂停截止时间。 */
    private final Map<String, Long> pauseUntilMap = new ConcurrentHashMap<String, Long>();

    /** 监控线程池。 */
    private ExecutorService monitorExecutor;
    /** 当前是否处于运行中。 */
    private volatile boolean running;

    /**
     * 创建微信渠道适配器。
     *
     * @param agentRuntimeService 运行时服务
     * @param weixinRobotSender 微信发送器
     * @param accountStoreService 微信账号存储
     * @param apiGateway 微信 API 网关
     * @param properties 微信配置
     */
    public WeixinChannelAdapter(
            AgentRuntimeService agentRuntimeService,
            WeixinRobotSender weixinRobotSender,
            WeixinAccountStoreService accountStoreService,
            WeixinApiGateway apiGateway,
            WeixinProperties properties
    ) {
        this.agentRuntimeService = agentRuntimeService;
        this.weixinRobotSender = weixinRobotSender;
        this.accountStoreService = accountStoreService;
        this.apiGateway = apiGateway;
        this.properties = properties;
    }

    /**
     * 启动微信渠道。
     */
    public synchronized void start() {
        if (!properties.isEnabled()) {
            log.info("Weixin channel disabled.");
            return;
        }
        if (running) {
            return;
        }

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "weixin-channel-monitor");
            thread.setDaemon(true);
            return thread;
        };
        this.monitorExecutor = Executors.newCachedThreadPool(threadFactory);
        this.running = true;
        reloadAccounts();
        log.info("Weixin channel started.");
    }

    /**
     * 停止微信渠道。
     */
    public synchronized void stop() {
        running = false;
        for (Future<?> future : monitorTasks.values()) {
            future.cancel(true);
        }
        monitorTasks.clear();
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
            monitorExecutor = null;
        }
        pauseUntilMap.clear();
        log.info("Weixin channel stopped.");
    }

    /**
     * 重新扫描已保存账号，并为新增账号启动长轮询。
     */
    public synchronized void reloadAccounts() {
        if (!running || monitorExecutor == null) {
            return;
        }

        List<WeixinAccount> accounts = accountStoreService.listAccounts();
        Set<String> desiredAccountIds = new LinkedHashSet<String>();
        for (WeixinAccount account : accounts) {
            if (account != null && StrUtil.isNotBlank(account.getAccountId())) {
                desiredAccountIds.add(account.getAccountId());
                ensureMonitor(account.getAccountId());
            }
        }

        for (String existingAccountId : monitorTasks.keySet()) {
            if (!desiredAccountIds.contains(existingAccountId)) {
                stopMonitor(existingAccountId);
            }
        }
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.WEIXIN;
    }

    @Override
    public DeliveryResult send(OutboundEnvelope outboundEnvelope) {
        return weixinRobotSender.send(outboundEnvelope);
    }

    /**
     * 将微信消息转换为统一入站模型。
     *
     * @param account 当前账号
     * @param message 微信消息
     * @return 入站信封；不应处理时返回 null
     */
    public InboundEnvelope toInboundEnvelope(WeixinAccount account, WeixinMessage message) {
        if (account == null || message == null) {
            return null;
        }
        if (message.getMessage_type() != null && message.getMessage_type() == MESSAGE_TYPE_BOT) {
            return null;
        }

        String senderId = StrUtil.trim(message.getFrom_user_id());
        if (StrUtil.isBlank(senderId)) {
            return null;
        }
        if (!isAllowed(senderId)) {
            return null;
        }
        if (StrUtil.isBlank(message.getContext_token())) {
            log.warn("Ignore Weixin message because context_token is missing. accountId={}, senderId={}", account.getAccountId(), senderId);
            return null;
        }

        String content = extractContent(message);
        if (StrUtil.isBlank(content)) {
            return null;
        }

        InboundEnvelope inboundEnvelope = new InboundEnvelope();
        inboundEnvelope.setMessageId(resolveMessageId(account, message));
        inboundEnvelope.setChannelType(ChannelType.WEIXIN);
        inboundEnvelope.setChannelInstanceId(account.getAccountId());
        inboundEnvelope.setSenderId(senderId);
        inboundEnvelope.setConversationId(senderId);
        inboundEnvelope.setConversationType(ConversationType.PRIVATE);
        inboundEnvelope.setContent(content);
        inboundEnvelope.setReceivedAt(message.getCreate_time_ms() == null ? System.currentTimeMillis() : message.getCreate_time_ms());
        inboundEnvelope.setSessionKey("weixin:" + account.getAccountId() + ":private:" + senderId);
        inboundEnvelope.setReplyTarget(
                new ReplyTarget(
                        ChannelType.WEIXIN,
                        ConversationType.PRIVATE,
                        senderId,
                        senderId,
                        account.getAccountId(),
                        message.getContext_token()
                )
        );
        return inboundEnvelope;
    }

    private void ensureMonitor(String accountId) {
        Future<?> future = monitorTasks.get(accountId);
        if (future != null && !future.isDone()) {
            return;
        }
        Future<?> next = monitorExecutor.submit(() -> monitorAccount(accountId));
        monitorTasks.put(accountId, next);
    }

    private void stopMonitor(String accountId) {
        Future<?> future = monitorTasks.remove(accountId);
        if (future != null) {
            future.cancel(true);
        }
        pauseUntilMap.remove(accountId);
    }

    private void monitorAccount(String accountId) {
        int nextTimeoutMs = Math.max(5000, properties.getLongPollTimeoutMs());
        int consecutiveFailures = 0;
        String cursor = accountStoreService.loadCursor(accountId);
        while (running && !Thread.currentThread().isInterrupted()) {
            WeixinAccount account = accountStoreService.loadAccount(accountId);
            if (account == null || StrUtil.isBlank(account.getToken())) {
                sleepQuietly(5000L);
                continue;
            }

            long remainingPauseMs = remainingPauseMs(accountId);
            if (remainingPauseMs > 0L) {
                sleepQuietly(Math.min(remainingPauseMs, BACKOFF_DELAY_MS));
                continue;
            }

            try {
                WeixinGetUpdatesResponse response = apiGateway.getUpdates(
                        StrUtil.blankToDefault(account.getBaseUrl(), properties.getBaseUrl()),
                        account.getToken(),
                        cursor,
                        nextTimeoutMs
                );
                if (response != null && response.getLongpolling_timeout_ms() != null && response.getLongpolling_timeout_ms() > 0) {
                    nextTimeoutMs = response.getLongpolling_timeout_ms();
                }
                if (isSessionExpired(response)) {
                    pauseUntilMap.put(accountId, System.currentTimeMillis() + SESSION_PAUSE_MS);
                    consecutiveFailures = 0;
                    continue;
                }
                if (hasApiError(response)) {
                    consecutiveFailures = handleFailure(consecutiveFailures, accountId, response == null ? "unknown response" : response.getErrmsg());
                    continue;
                }

                consecutiveFailures = 0;
                if (response != null && StrUtil.isNotBlank(response.getGet_updates_buf())) {
                    cursor = response.getGet_updates_buf();
                    accountStoreService.saveCursor(accountId, cursor);
                }
                if (response == null || response.getMsgs() == null) {
                    continue;
                }

                for (WeixinMessage message : response.getMsgs()) {
                    try {
                        InboundEnvelope inboundEnvelope = toInboundEnvelope(account, message);
                        if (inboundEnvelope != null) {
                            agentRuntimeService.submitInbound(inboundEnvelope);
                        }
                    } catch (Throwable throwable) {
                        log.warn("Failed to consume Weixin message. accountId={}, err={}", accountId, throwable.getMessage(), throwable);
                    }
                }
            } catch (Throwable throwable) {
                consecutiveFailures = handleFailure(consecutiveFailures, accountId, throwable.getMessage());
            }
        }
    }

    private int handleFailure(int consecutiveFailures, String accountId, String message) {
        int nextFailures = consecutiveFailures + 1;
        log.warn("Weixin long-poll failed. accountId={}, failures={}, message={}", accountId, nextFailures, message);
        if (nextFailures >= MAX_CONSECUTIVE_FAILURES) {
            sleepQuietly(BACKOFF_DELAY_MS);
            return 0;
        }
        sleepQuietly(RETRY_DELAY_MS);
        return nextFailures;
    }

    private boolean isAllowed(String senderId) {
        return properties.getAllowFrom().isEmpty() || properties.getAllowFrom().contains(senderId);
    }

    private boolean hasApiError(WeixinGetUpdatesResponse response) {
        if (response == null) {
            return true;
        }
        Integer ret = response.getRet();
        Integer errcode = response.getErrcode();
        return (ret != null && ret != 0) || (errcode != null && errcode != 0);
    }

    private boolean isSessionExpired(WeixinGetUpdatesResponse response) {
        if (response == null) {
            return false;
        }
        Integer ret = response.getRet();
        Integer errcode = response.getErrcode();
        return (ret != null && ret == SESSION_EXPIRED_ERRCODE) || (errcode != null && errcode == SESSION_EXPIRED_ERRCODE);
    }

    private long remainingPauseMs(String accountId) {
        Long until = pauseUntilMap.get(accountId);
        if (until == null) {
            return 0L;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            pauseUntilMap.remove(accountId);
            return 0L;
        }
        return remaining;
    }

    private String resolveMessageId(WeixinAccount account, WeixinMessage message) {
        String messageId = message.getMessage_id() == null ? "msg-" + System.nanoTime() : String.valueOf(message.getMessage_id());
        return account.getAccountId() + ":" + messageId;
    }

    private String extractContent(WeixinMessage message) {
        List<WeixinMessageItem> items = message.getItem_list();
        if (items == null || items.isEmpty()) {
            return null;
        }

        for (WeixinMessageItem item : items) {
            if (item != null && item.getType() != null && item.getType() == ITEM_TYPE_TEXT && item.getText_item() != null) {
                String text = StrUtil.trim(item.getText_item().getText());
                if (StrUtil.isNotBlank(text)) {
                    return text;
                }
            }
        }
        for (WeixinMessageItem item : items) {
            if (item != null && item.getType() != null && item.getType() == ITEM_TYPE_VOICE && item.getVoice_item() != null) {
                String text = StrUtil.trim(item.getVoice_item().getText());
                if (StrUtil.isNotBlank(text)) {
                    return text;
                }
            }
        }
        for (WeixinMessageItem item : items) {
            if (item == null || item.getType() == null) {
                continue;
            }
            if (item.getType() == ITEM_TYPE_IMAGE) {
                return "收到图片消息";
            }
            if (item.getType() == ITEM_TYPE_VIDEO) {
                return "收到视频消息";
            }
            if (item.getType() == ITEM_TYPE_FILE) {
                String fileName = item.getFile_item() == null ? null : StrUtil.trim(item.getFile_item().getFile_name());
                return StrUtil.isBlank(fileName) ? "收到文件消息" : "收到文件：" + fileName;
            }
            if (item.getType() == ITEM_TYPE_VOICE) {
                return "收到语音消息";
            }
        }
        return null;
    }

    private void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
