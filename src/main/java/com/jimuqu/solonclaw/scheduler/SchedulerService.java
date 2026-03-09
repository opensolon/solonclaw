package com.jimuqu.solonclaw.scheduler;

import com.jimuqu.solonclaw.agent.AgentService;
import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.noear.solon.scheduling.scheduled.JobHolder;
import org.noear.solon.scheduling.scheduled.JobInterceptor;
import org.noear.solon.scheduling.scheduled.JobHandler;
import org.noear.solon.scheduling.scheduled.manager.IJobManager;
import org.noear.solon.scheduling.simple.JobManager;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * 调度服务
 * <p>
 * 管理动态定时任务，支持任务的增删改查和持久化
 * 集成 Solon 的 IJobManager 实现真正的动态调度
 *
 * @author SolonClaw
 */
@Component
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    @Inject
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    @Inject
    private AgentService agentService;

    /**
     * Solon 的任务管理器
     */
    private IJobManager jobManager;

    /**
     * 任务执行器（用于 fixedRate 任务）
     */
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "scheduler-fixedrate");
        t.setDaemon(true);
        return t;
    });

    /**
     * 运行中的 fixedRate 任务（用于取消）
     */
    private final Map<String, ScheduledFuture<?>> runningFixedRateTasks = new ConcurrentHashMap<>();

    /**
     * 任务定义：名称 -> 任务信息
     */
    private final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();

    /**
     * 任务执行历史
     */
    private final List<JobHistory> jobHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 初始化时加载任务
     */
    @Init
    public void init() {
        // 获取 Solon 的 JobManager 实例
        jobManager = JobManager.getInstance();
        log.info("SchedulerService 初始化，JobManager: {}", ObjUtil.isNotNull(jobManager) ? "已加载" : "未找到");

        // 加载持久化的任务
        loadJobs();
        loadJobHistory();

        // 恢复之前保存的任务
        restoreJobs();
    }

    /**
     * 添加 Cron 表达式任务（默认 Main Session 模式）
     *
     * @param name     任务名称
     * @param cron     Cron 表达式
     * @param command  要执行的命令（发送给 Agent）
     * @return 是否添加成功
     */
    public boolean addCronJob(String name, String cron, String command) {
        return addCronJob(name, cron, command, SessionType.MAIN, MessageMode.STANDARD, null, null);
    }

    /**
     * 添加 Cron 表达式任务（支持会话类型）
     *
     * @param name         任务名称
     * @param cron         Cron 表达式
     * @param command      要执行的命令（发送给 Agent）
     * @param sessionType  会话类型（MAIN/ISOLATED）
     * @param messageMode  消息模式（STANDARD/ANNOUNCE）
     * @param model        可选：覆盖默认模型
     * @param thinking     可选：思考级别
     * @return 是否添加成功
     */
    public boolean addCronJob(String name, String cron, String command, SessionType sessionType, MessageMode messageMode, String model, String thinking) {
        if (jobs.containsKey(name)) {
            log.warn("任务已存在: {}", name);
            return false;
        }

        try {
            // 创建任务信息
            JobInfo jobInfo = new JobInfo(name, cron, 0, false, 0, command, JobType.CRON, sessionType, messageMode, model, thinking);
            jobs.put(name, jobInfo);

            // 注册到 Solon 调度器
            registerJob(jobInfo);

            // 持久化
            saveJobs();
            log.info("添加 Cron 任务: name={}, cron={}, sessionType={}", name, cron, sessionType);
            return true;
        } catch (Exception e) {
            log.error("添加 Cron 任务失败: {}", name, e);
            jobs.remove(name);
            return false;
        }
    }

    /**
     * 添加固定频率任务
     *
     * @param name     任务名称
     * @param fixedRate 固定频率（毫秒）
     * @param command  要执行的命令
     * @return 是否添加成功
     */
    public boolean addFixedRateJob(String name, long fixedRate, String command) {
        if (jobs.containsKey(name)) {
            log.warn("任务已存在: {}", name);
            return false;
        }

        try {
            JobInfo jobInfo = new JobInfo(name, null, fixedRate, false, 0, command, JobType.FIXED_RATE);
            jobs.put(name, jobInfo);

            // 使用 ExecutorService 调度 fixedRate 任务
            startFixedRateTask(jobInfo);

            saveJobs();
            log.info("添加固定频率任务: name={}, fixedRate={}ms", name, fixedRate);
            return true;
        } catch (Exception e) {
            log.error("添加固定频率任务失败: {}", name, e);
            jobs.remove(name);
            return false;
        }
    }

    /**
     * 启动 fixedRate 任务
     */
    private void startFixedRateTask(JobInfo jobInfo) {
        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                executeJob(jobInfo);
            } catch (Exception e) {
                log.error("FixedRate 任务执行失败: {}", jobInfo.name(), e);
            }
        }, 0, jobInfo.fixedRate(), TimeUnit.MILLISECONDS);

        runningFixedRateTasks.put(jobInfo.name(), future);
        log.info("FixedRate 任务已启动: {}, interval={}ms", jobInfo.name(), jobInfo.fixedRate());
    }

    /**
     * 添加一次性任务
     *
     * @param name          任务名称
     * @param delayMillis   延迟时间（毫秒）
     * @param command       要执行的命令
     * @return 是否添加成功
     */
    public boolean addOneTimeJob(String name, long delayMillis, String command) {
        if (jobs.containsKey(name)) {
            log.warn("任务已存在: {}", name);
            return false;
        }

        try {
            long scheduleTime = System.currentTimeMillis() + delayMillis;
            JobInfo jobInfo = new JobInfo(name, null, delayMillis, true, scheduleTime, command, JobType.ONE_TIME);
            jobs.put(name, jobInfo);

            registerJob(jobInfo);
            saveJobs();
            log.info("添加一次性任务: name={}, delay={}ms", name, delayMillis);
            return true;
        } catch (Exception e) {
            log.error("添加一次性任务失败: {}", name, e);
            jobs.remove(name);
            return false;
        }
    }

    /**
     * 注册任务到 Solon 调度器
     */
    private void registerJob(JobInfo jobInfo) {
        if (ObjUtil.isNull(jobManager)) {
            log.warn("JobManager 未初始化，任务将只保存到配置文件");
            return;
        }

        try {
            // 创建 Scheduled 注解实例
            final Scheduled scheduled = new Scheduled() {
                @Override
                public String name() {
                    return jobInfo.name();
                }

                @Override
                public String cron() {
                    return StrUtil.blankToDefault(jobInfo.cron(), "");
                }

                @Override
                public String zone() {
                    return "";
                }

                @Override
                public long initialDelay() {
                    return 0;
                }

                @Override
                public long fixedRate() {
                    // Solon 可能使用 fixedDelay，对于 FIXED_RATE 任务需要同时设置
                    return 0;
                }

                @Override
                public long fixedDelay() {
                    // 对于 FIXED_RATE 和 CRON 任务，使用 fixedDelay 来调度
                    if (jobInfo.jobType() == JobType.FIXED_RATE) {
                        long delay = jobInfo.fixedRate();
                        log.info("fixedDelay() for FIXED_RATE: {} = {}", jobInfo.name(), delay);
                        return delay;
                    } else if (jobInfo.jobType() == JobType.ONE_TIME) {
                        return jobInfo.fixedRate();
                    }
                    return 0;
                }

                @Override
                public boolean enable() {
                    return true;
                }

                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return Scheduled.class;
                }
            };

            // 创建任务处理器
            JobHandler handler = (ctx) -> {
                log.info("任务处理器被调用: {}", jobInfo.name());
                executeJob(jobInfo);
            };

            jobManager.jobAdd(jobInfo.name(), scheduled, handler);

            log.info("任务已注册到调度器: {}, type={}, cron={}, fixedRate={}",
                jobInfo.name(), jobInfo.jobType(), jobInfo.cron(), jobInfo.fixedRate());
        } catch (Exception e) {
            log.error("注册任务到调度器失败: {}", jobInfo.name(), e);
            throw new RuntimeException("注册任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行任务
     */
    private void executeJob(JobInfo jobInfo) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            log.info("执行任务: {}, sessionType={}", jobInfo.name(), jobInfo.sessionType());

            // 根据会话类型执行任务
            String response;
            if (jobInfo.sessionType() == SessionType.ISOLATED) {
                // Isolated Session 模式 - 创建干净会话
                response = executeJobIsolated(jobInfo);
            } else {
                // Main Session 模式 - 注入主会话
                response = executeJobMain(jobInfo);
            }

            success = true;
            log.info("任务执行成功: {}, 响应长度: {}", jobInfo.name(), response.length());

            // 如果是一次性任务，执行后删除
            if (jobInfo.isOneTime()) {
                removeJob(jobInfo.name());
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("任务执行失败: {}", jobInfo.name(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            recordJobExecution(jobInfo.name(), success, duration, errorMessage);
        }
    }

    /**
     * 执行 Isolated Session 任务（干净的历史记录）
     */
    private String executeJobIsolated(JobInfo jobInfo) {
        String sessionId = "cron:" + jobInfo.name();

        // 清空该会话的历史记录
        agentService.clearHistory(sessionId);
        log.debug("Isolated Session 已清空历史: {}", sessionId);

        // 执行任务
        String response = agentService.chat(jobInfo.command(), sessionId);

        // 执行完成后可以选择保留或清理
        // 这里保留以便后续调试

        return response;
    }

    /**
     * 执行 Main Session 任务（注入主会话）
     */
    private String executeJobMain(JobInfo jobInfo) {
        String sessionId = "main";

        // 注入到主会话，作为系统事件
        String eventMessage = "System Event: " + jobInfo.name() + " - " + jobInfo.command();
        log.debug("注入系统事件到主会话: {}", eventMessage);

        // 调用 Agent 执行命令
        return agentService.chat(jobInfo.command(), sessionId);
    }

    /**
     * 删除任务
     *
     * @param name 任务名称
     * @return 是否删除成功
     */
    public boolean removeJob(String name) {
        JobInfo jobInfo = jobs.remove(name);
        if (ObjUtil.isNull(jobInfo)) {
            log.warn("任务不存在: {}", name);
            return false;
        }

        // 取消 fixedRate 任务
        ScheduledFuture<?> future = runningFixedRateTasks.remove(name);
        if (ObjUtil.isNotNull(future)) {
            future.cancel(false);
            log.info("已取消 FixedRate 任务: {}", name);
        }

        // 从 Solon 调度器中移除
        if (ObjUtil.isNotNull(jobManager) && jobManager.jobExists(name)) {
            jobManager.jobRemove(name);
        }

        saveJobs();
        log.info("删除任务: {}", name);
        return true;
    }

    /**
     * 暂停任务
     *
     * @param name 任务名称
     * @return 是否暂停成功
     */
    public boolean pauseJob(String name) {
        if (!jobs.containsKey(name)) {
            log.warn("任务不存在: {}", name);
            return false;
        }

        try {
            if (ObjUtil.isNotNull(jobManager) && jobManager.jobExists(name)) {
                jobManager.jobStop(name);
                log.info("暂停任务: {}", name);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("暂停任务失败: {}", name, e);
            return false;
        }
    }

    /**
     * 恢复任务
     *
     * @param name 任务名称
     * @return 是否恢复成功
     */
    public boolean resumeJob(String name) {
        if (!jobs.containsKey(name)) {
            log.warn("任务不存在: {}", name);
            return false;
        }

        try {
            if (ObjUtil.isNotNull(jobManager) && jobManager.jobExists(name)) {
                jobManager.jobStart(name, null);
                log.info("恢复任务: {}", name);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("恢复任务失败: {}", name, e);
            return false;
        }
    }

    /**
     * 获取所有任务
     *
     * @return 任务列表
     */
    public List<JobInfo> getJobs() {
        return new ArrayList<>(jobs.values());
    }

    /**
     * 获取任务信息
     *
     * @param name 任务名称
     * @return 任务信息
     */
    public JobInfo getJob(String name) {
        return jobs.get(name);
    }

    /**
     * 检查任务是否存在
     *
     * @param name 任务名称
     * @return 是否存在
     */
    public boolean hasJob(String name) {
        return jobs.containsKey(name);
    }

    /**
     * 获取任务执行历史
     *
     * @param limit 数量限制
     * @return 执行历史列表
     */
    public List<JobHistory> getJobHistory(int limit) {
        int size = jobHistory.size();
        if (limit <= 0 || limit > size) {
            return new ArrayList<>(jobHistory);
        }
        return new ArrayList<>(jobHistory.subList(size - limit, size));
    }

    /**
     * 获取指定任务的执行历史
     *
     * @param name 任务名称
     * @param limit 数量限制
     * @return 执行历史列表
     */
    public List<JobHistory> getJobHistoryByName(String name, int limit) {
        List<JobHistory> result = new ArrayList<>();
        for (int i = jobHistory.size() - 1; i >= 0 && result.size() < limit; i--) {
            JobHistory history = jobHistory.get(i);
            if (history.name().equals(name)) {
                result.add(history);
            }
        }
        return result;
    }

    /**
     * 记录任务执行历史
     */
    private void recordJobExecution(String name, boolean success, long duration, String errorMessage) {
        JobHistory history = new JobHistory(
            name,
            System.currentTimeMillis(),
            duration,
            success,
            errorMessage
        );
        jobHistory.add(history);
        saveJobHistory();
    }

    /**
     * 清空任务历史
     */
    public void clearJobHistory() {
        jobHistory.clear();
        saveJobHistory();
        log.info("任务历史已清空");
    }

    /**
     * 恢复之前保存的任务
     */
    private void restoreJobs() {
        for (JobInfo jobInfo : jobs.values()) {
            try {
                if (jobInfo.jobType() == JobType.FIXED_RATE) {
                    // 恢复 fixedRate 任务
                    if (!runningFixedRateTasks.containsKey(jobInfo.name())) {
                        startFixedRateTask(jobInfo);
                        log.info("恢复 FixedRate 任务: {}", jobInfo.name());
                    }
                } else if (ObjUtil.isNotNull(jobManager) && !jobManager.jobExists(jobInfo.name())) {
                    // 恢复 cron/one-time 任务
                    registerJob(jobInfo);
                    log.debug("恢复任务: {}", jobInfo.name());
                }
            } catch (Exception e) {
                log.error("恢复任务失败: {}", jobInfo.name(), e);
            }
        }
    }

    /**
     * 保存任务配置到文件
     */
    private void saveJobs() {
        try {
            Path jobsFile = workspaceInfo.jobsFile();
            Files.createDirectories(jobsFile.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            int i = 0;
            for (JobInfo job : jobs.values()) {
                if (i > 0) sb.append(",\n");
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(escapeJson(job.name())).append("\",\n");
                sb.append("    \"cron\": ").append(StrUtil.isNotEmpty(job.cron()) ? "\"" + escapeJson(job.cron()) + "\"" : "null").append(",\n");
                sb.append("    \"fixedRate\": ").append(job.fixedRate()).append(",\n");
                sb.append("    \"isOneTime\": ").append(job.isOneTime()).append(",\n");
                sb.append("    \"scheduleTime\": ").append(job.scheduleTime()).append(",\n");
                sb.append("    \"command\": \"").append(escapeJson(job.command())).append("\",\n");
                sb.append("    \"jobType\": \"").append(job.jobType().name()).append("\"\n");
                sb.append("  }");
                i++;
            }
            sb.append("\n]");

            Files.writeString(jobsFile, sb.toString());
            log.debug("任务配置已保存: {}", jobsFile);
        } catch (Exception e) {
            log.error("保存任务配置失败", e);
        }
    }

    /**
     * 从文件加载任务配置
     */
    private void loadJobs() {
        try {
            Path jobsFile = workspaceInfo.jobsFile();
            if (!Files.exists(jobsFile)) {
                return;
            }

            String content = Files.readString(jobsFile);
            // 简化的 JSON 解析
            parseJobsJson(content);
            log.info("从文件加载了 {} 个任务: {}", jobs.size(), jobsFile);
        } catch (Exception e) {
            log.error("加载任务配置失败", e);
        }
    }

    /**
     * 简化的 JSON 解析
     */
    private void parseJobsJson(String json) {
        // 移除首尾空白和方括号
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        // 分割对象
        int braceCount = 0;
        StringBuilder currentObj = new StringBuilder();
        for (char c : json.toCharArray()) {
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            currentObj.append(c);
            if (braceCount == 0 && currentObj.length() > 0) {
                String obj = currentObj.toString().trim();
                if (obj.startsWith("{") && obj.endsWith("}")) {
                    JobInfo jobInfo = parseJobObject(obj);
                    if (jobInfo != null) {
                        jobs.put(jobInfo.name(), jobInfo);
                    }
                }
                currentObj = new StringBuilder();
            }
        }
    }

    /**
     * 解析单个任务对象
     */
    private JobInfo parseJobObject(String obj) {
        try {
            String name = extractStringValue(obj, "name");
            String cron = extractStringValue(obj, "cron");
            long fixedRate = extractLongValue(obj, "fixedRate");
            boolean isOneTime = extractBooleanValue(obj, "isOneTime");
            long scheduleTime = extractLongValue(obj, "scheduleTime");
            String command = extractStringValue(obj, "command");
            String jobTypeStr = extractStringValue(obj, "jobType");

            JobType jobType = JobType.CRON;
            if (jobTypeStr != null) {
                try {
                    jobType = JobType.valueOf(jobTypeStr);
                } catch (IllegalArgumentException ignored) {}
            }

            return new JobInfo(name, cron, fixedRate, isOneTime, scheduleTime, command, jobType);
        } catch (Exception e) {
            log.warn("解析任务对象失败: {}", obj, e);
            return null;
        }
    }

    /**
     * 保存任务执行历史到文件
     */
    private void saveJobHistory() {
        try {
            Path historyFile = workspaceInfo.jobHistoryFile();
            Files.createDirectories(historyFile.getParent());

            // 只保留最近 1000 条历史记录
            List<JobHistory> toSave = new ArrayList<>(jobHistory);
            if (toSave.size() > 1000) {
                toSave = toSave.subList(toSave.size() - 1000, toSave.size());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < toSave.size(); i++) {
                if (i > 0) sb.append(",\n");
                JobHistory h = toSave.get(i);
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(escapeJson(h.name())).append("\",\n");
                sb.append("    \"executionTime\": ").append(h.executionTime()).append(",\n");
                sb.append("    \"duration\": ").append(h.duration()).append(",\n");
                sb.append("    \"success\": ").append(h.success()).append(",\n");
                sb.append("    \"errorMessage\": ").append(StrUtil.isNotEmpty(h.errorMessage()) ? "\"" + escapeJson(h.errorMessage()) + "\"" : "null").append("\n");
                sb.append("  }");
            }
            sb.append("\n]");

            Files.writeString(historyFile, sb.toString());
            log.debug("任务历史已保存: {}", historyFile);
        } catch (Exception e) {
            log.error("保存任务历史失败", e);
        }
    }

    /**
     * 从文件加载任务执行历史
     */
    private void loadJobHistory() {
        try {
            Path historyFile = workspaceInfo.jobHistoryFile();
            if (!Files.exists(historyFile)) {
                return;
            }

            String content = Files.readString(historyFile);
            parseHistoryJson(content);
            log.info("从文件加载了 {} 条任务历史: {}", jobHistory.size(), historyFile);
        } catch (Exception e) {
            log.error("加载任务历史失败", e);
        }
    }

    /**
     * 解析历史 JSON
     */
    private void parseHistoryJson(String json) {
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        int braceCount = 0;
        StringBuilder currentObj = new StringBuilder();
        for (char c : json.toCharArray()) {
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            currentObj.append(c);
            if (braceCount == 0 && currentObj.length() > 0) {
                String obj = currentObj.toString().trim();
                if (obj.startsWith("{") && obj.endsWith("}")) {
                    JobHistory history = parseHistoryObject(obj);
                    if (history != null) {
                        jobHistory.add(history);
                    }
                }
                currentObj = new StringBuilder();
            }
        }
    }

    /**
     * 解析单个历史对象
     */
    private JobHistory parseHistoryObject(String obj) {
        try {
            String name = extractStringValue(obj, "name");
            long executionTime = extractLongValue(obj, "executionTime");
            long duration = extractLongValue(obj, "duration");
            boolean success = extractBooleanValue(obj, "success");
            String errorMessage = extractStringValue(obj, "errorMessage");

            return new JobHistory(name, executionTime, duration, success, errorMessage);
        } catch (Exception e) {
            log.warn("解析历史对象失败: {}", obj, e);
            return null;
        }
    }

    // JSON 辅助方法
    private String escapeJson(String s) {
        if (StrUtil.isBlank(s)) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\') end++;
            end++;
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }

    private long extractLongValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return 0;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean extractBooleanValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return false;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        return json.substring(start, start + 4).equals("true");
    }

    /**
     * 任务类型
     */
    public enum JobType {
        CRON,       // Cron 表达式任务
        FIXED_RATE, // 固定频率任务
        ONE_TIME    // 一次性任务
    }

    /**
     * 会话类型
     */
    public enum SessionType {
        MAIN,       // 主会话 - 注入主会话上下文
        ISOLATED    // 独立会话 - 干净的历史记录
    }

    /**
     * 消息模式
     */
    public enum MessageMode {
        STANDARD,   // 标准模式
        ANNOUNCE    // 公告模式 - 直接发送摘要
    }

    /**
     * 任务信息
     *
     * @param name          任务名称
     * @param cron          Cron 表达式
     * @param fixedRate     固定频率（毫秒）
     * @param isOneTime     是否为一次性任务
     * @param scheduleTime  调度时间（一次性任务）
     * @param command       要执行的命令
     * @param jobType       任务类型
     * @param sessionType   会话类型（MAIN/ISOLATED）
     * @param messageMode   消息模式（STANDARD/ANNOUNCE）
     * @param model         可选：覆盖默认模型
     * @param thinking      可选：思考级别
     */
    public record JobInfo(
            String name,
            String cron,
            long fixedRate,
            boolean isOneTime,
            long scheduleTime,
            String command,
            JobType jobType,
            SessionType sessionType,
            MessageMode messageMode,
            String model,
            String thinking
    ) {
        /**
         * 便捷构造函数（向后兼容）
         */
        public JobInfo(String name, String cron, long fixedRate, boolean isOneTime, long scheduleTime, String command, JobType jobType) {
            this(name, cron, fixedRate, isOneTime, scheduleTime, command, jobType, SessionType.MAIN, MessageMode.STANDARD, null, null);
        }

        /**
         * 更简洁的便捷构造函数（仅 name, cron, command）
         */
        public JobInfo(String name, String cron, String command) {
            this(name, cron, 0, false, 0, command, JobType.CRON, SessionType.MAIN, MessageMode.STANDARD, null, null);
        }

        /**
         * 获取会话类型（默认 MAIN）
         */
        public SessionType sessionType() {
            return sessionType != null ? sessionType : SessionType.MAIN;
        }

        /**
         * 获取消息模式（默认 STANDARD）
         */
        public MessageMode messageMode() {
            return messageMode != null ? messageMode : MessageMode.STANDARD;
        }
    }

    /**
     * 任务执行历史
     *
     * @param name           任务名称
     * @param executionTime  执行时间
     * @param duration       执行时长（毫秒）
     * @param success        是否成功
     * @param errorMessage   错误消息（如果有）
     */
    public record JobHistory(
            String name,
            long executionTime,
            long duration,
            boolean success,
            String errorMessage
    ) {
    }
}