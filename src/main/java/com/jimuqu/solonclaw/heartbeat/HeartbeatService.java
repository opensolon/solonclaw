package com.jimuqu.solonclaw.heartbeat;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solonclaw.agent.AgentService;
import com.jimuqu.solonclaw.memory.MemoryService;
import com.jimuqu.solonclaw.memory.file.MemoryFileManager;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heartbeat 核心服务
 * <p>
 * 实现定时触发心跳检查，读取 HEARTBEAT.md 并执行任务
 *
 * @author SolonClaw
 */
@Component
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private static final String HEARTBEAT_OK = "HEARTBEAT_OK";
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smh])");

    @Inject
    private HeartbeatProperties properties;

    @Inject
    private HeartbeatReadTool heartbeatReadTool;

    @Inject
    private AgentService agentService;

    @Inject
    private MemoryService memoryService;

    @Inject(required = false)
    private MemoryFileManager memoryFileManager;

    /**
     * 是否正在执行心跳（防止并发）
     */
    private volatile boolean running = false;

    /**
     * 定时触发心跳
     * <p>
     * 使用 cron 表达式实现定时，默认每 30 分钟执行一次
     */
    @Scheduled(cron = "${solonclaw.heartbeat.cron:0 0/30 * * * ?}")
    public void onHeartbeat() {
        if (!properties.isEnabled()) {
            log.debug("Heartbeat 已禁用，跳过");
            return;
        }

        // 检查是否在活跃时间范围内
        if (!isInActiveHours()) {
            log.debug("不在活跃时间范围内，跳过 Heartbeat");
            return;
        }

        triggerHeartbeat("scheduled");
    }

    /**
     * 触发心跳
     *
     * @param reason 触发原因
     */
    public void triggerHeartbeat(String reason) {
        if (running) {
            log.warn("Heartbeat 正在执行中，跳过本次触发: {}", reason);
            return;
        }

        synchronized (this) {
            if (running) {
                log.warn("Heartbeat 正在执行中，跳过本次触发: {}", reason);
                return;
            }
            running = true;
        }

        try {
            log.info("触发 Heartbeat: {}", reason);
            HeartbeatResult result = executeHeartbeat();
            log.info("Heartbeat 执行完成: {}", result);
        } catch (Exception e) {
            log.error("Heartbeat 执行异常", e);
        } finally {
            running = false;
        }
    }

    /**
     * 执行心跳任务
     */
    public HeartbeatResult executeHeartbeat() {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 读取 HEARTBEAT.md
            String content = heartbeatReadTool.readHeartbeatFile();

            // 2. 判断是否为空
            if (heartbeatReadTool.isContentEmpty(content)) {
                log.debug("HEARTBEAT.md 为空，跳过执行");
                return HeartbeatResult.skipped("empty-heartbeat-file");
            }

            // 3. 获取目标会话 ID
            String sessionId = resolveTargetSession();
            log.debug("使用目标会话: {}", sessionId);

            // 4. 构建 Heartbeat Prompt
            String prompt = buildHeartbeatPrompt(content);

            // 5. 执行 Agent（在主会话中）
            String response = agentService.chat(prompt, sessionId);

            long duration = System.currentTimeMillis() - startTime;

            // 6. 判断是否应该跳过发送
            if (properties.isSmartSkip() && shouldSkipSend(response)) {
                log.info("Heartbeat 智能跳过，响应: {}", response.substring(0, Math.min(50, response.length())));
                return HeartbeatResult.successNoNotify(response, duration);
            }

            // 7. 判断响应长度是否超过阈值
            boolean needsNotify = response.length() > properties.getAckMaxChars();
            log.info("Heartbeat 执行完成，响应长度: {}, 需要通知: {}", response.length(), needsNotify);

            // 8. 清理过期记忆（使用 MemoryFileManager）
            if (memoryFileManager != null) {
                try {
                    String cleanupResult = memoryFileManager.cleanupOldNotes();
                    log.debug("记忆清理: {}", cleanupResult);
                } catch (Exception e) {
                    log.warn("记忆清理失败", e);
                }
            }

            // 9. 执行长期记忆备份（使用 MemoryFileManager）
            if (memoryFileManager != null) {
                try {
                    String backupResult = memoryFileManager.backup();
                    log.debug("长期记忆备份: {}", backupResult);
                } catch (Exception e) {
                    log.warn("长期记忆备份失败", e);
                }
            }

            return HeartbeatResult.success(response, duration, needsNotify);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Heartbeat 执行失败", e);
            return HeartbeatResult.error(e.getMessage(), duration);
        }
    }

    /**
     * 构建 Heartbeat Prompt
     */
    private String buildHeartbeatPrompt(String content) {
        return """
                Read HEARTBEAT.md if it exists.
                Follow it strictly.
                Do not infer or repeat old tasks from prior chats.
                If nothing needs attention, reply HEARTBEAT_OK.

                ## HEARTBEAT.md 内容

                """ + content;
    }

    /**
     * 判断是否应该跳过发送
     */
    private boolean shouldSkipSend(String response) {
        if (StrUtil.isBlank(response)) {
            return true;
        }

        String trimmed = response.trim();

        // 检查是否只包含 HEARTBEAT_OK
        if (trimmed.equals(HEARTBEAT_OK)) {
            return true;
        }

        // 检查是否以 HEARTBEAT_OK 开头（忽略大小写）
        if (trimmed.toUpperCase().startsWith(HEARTBEAT_OK)) {
            return true;
        }

        // 检查响应中是否包含 HEARTBEAT_OK（用于更宽松的匹配）
        if (trimmed.toUpperCase().contains(HEARTBEAT_OK)) {
            // 如果只有 HEARTBEAT_OK 少数字符，才认为是跳过
            return trimmed.replace(HEARTBEAT_OK, "").trim().length() < 20;
        }

        return false;
    }

    /**
     * 解析目标会话 ID
     */
    private String resolveTargetSession() {
        String target = properties.getTarget();
        if (StrUtil.isBlank(target) || "last".equalsIgnoreCase(target)) {
            // 获取最近的会话
            return getLastSessionId();
        }
        return target;
    }

    /**
     * 获取最近会话 ID
     */
    private String getLastSessionId() {
        // 查找最近的会话
        var sessions = memoryService.listSessions();
        if (sessions != null && !sessions.isEmpty()) {
            // 返回最近的会话 ID（按更新时间排序）
            return sessions.get(0).id();
        }
        return "main";
    }

    /**
     * 检查是否在活跃时间范围内
     */
    private boolean isInActiveHours() {
        String startStr = properties.getActiveHoursStart();
        String endStr = properties.getActiveHoursEnd();

        // 如果没有配置活跃时间，则始终执行
        if (StrUtil.isBlank(startStr) || StrUtil.isBlank(endStr)) {
            return true;
        }

        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"));

            // 处理跨天情况
            if (start.isBefore(end)) {
                return now.isAfter(start) && now.isBefore(end);
            } else {
                // 跨天情况（如 22:00 - 06:00）
                return now.isAfter(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            log.warn("解析活跃时间失败: {}-{}", startStr, endStr, e);
            return true;
        }
    }

    /**
     * 手动触发一次心跳（供测试或外部调用）
     */
    public HeartbeatResult manualTrigger() {
        return executeHeartbeat();
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
