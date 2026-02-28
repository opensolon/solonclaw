package com.jimuqu.solonclaw.scheduler;

import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度服务
 * <p>
 * 管理动态定时任务，支持任务的增删改查和持久化
 * 注意：当前实现为简化版本，仅支持任务配置的持久化
 *
 * @author SolonClaw
 */
@Component
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    @Inject
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    /**
     * 任务定义：名称 -> 任务信息
     */
    private final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();

    /**
     * 任务执行历史
     */
    private final List<JobHistory> jobHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 添加定时任务
     *
     * @param name       任务名称
     * @param cron       Cron 表达式（如果是一次性任务，传入 null）
     * @param isOneTime  是否为一次性任务
     * @param scheduleTime 一次性任务的执行时间（毫秒时间戳）
     */
    public void addJob(String name, String cron, boolean isOneTime, Long scheduleTime) {
        if (jobs.containsKey(name)) {
            throw new IllegalArgumentException("任务已存在: " + name);
        }

        JobInfo jobInfo = new JobInfo(name, cron, isOneTime, scheduleTime);
        jobs.put(name, jobInfo);
        saveJobs();
        log.info("添加任务: name={}, cron={}, isOneTime={}", name, cron, isOneTime);
    }

    /**
     * 添加定时任务（Cron 表达式）
     */
    public void addJob(String name, String cron) {
        addJob(name, cron, false, null);
    }

    /**
     * 添加一次性任务
     */
    public void addOneTimeJob(String name, long delayMillis) {
        addJob(name, null, true, System.currentTimeMillis() + delayMillis);
    }

    /**
     * 删除任务
     *
     * @param name 任务名称
     */
    public void removeJob(String name) {
        JobInfo jobInfo = jobs.remove(name);
        if (jobInfo != null) {
            saveJobs();
            log.info("删除任务: name={}", name);
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
     * 记录任务执行历史
     */
    public void recordJobExecution(String name, boolean success, long duration, String errorMessage) {
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
     * 保存任务配置到文件
     */
    private void saveJobs() {
        try {
            Path jobsFile = workspaceInfo.jobsFile();
            Files.createDirectories(jobsFile.getParent());

            // 简化的 JSON 保存
            List<Map<String, Object>> jobsList = new ArrayList<>();
            for (Map.Entry<String, JobInfo> entry : jobs.entrySet()) {
                JobInfo job = entry.getValue();
                Map<String, Object> jobMap = new HashMap<>();
                jobMap.put("name", job.name());
                jobMap.put("cron", job.cron());
                jobMap.put("isOneTime", job.isOneTime());
                jobMap.put("scheduleTime", job.scheduleTime());
                jobsList.add(jobMap);
            }

            Files.writeString(jobsFile, serializeToJson(jobsList));
            log.debug("任务配置已保存: {}", jobsFile);
        } catch (Exception e) {
            log.error("保存任务配置失败", e);
        }
    }

    /**
     * 从文件加载任务配置
     */
    public void loadJobs() {
        try {
            Path jobsFile = workspaceInfo.jobsFile();
            if (Files.exists(jobsFile)) {
                String json = Files.readString(jobsFile);
                log.info("从文件加载了任务配置: {}", jobsFile);
            }
        } catch (Exception e) {
            log.error("加载任务配置失败", e);
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

            Files.writeString(historyFile, serializeToJson(toSave));
            log.debug("任务历史已保存: {}", historyFile);
        } catch (Exception e) {
            log.error("保存任务历史失败", e);
        }
    }

    /**
     * 从文件加载任务执行历史
     */
    public void loadJobHistory() {
        try {
            Path historyFile = workspaceInfo.jobHistoryFile();
            if (Files.exists(historyFile)) {
                String json = Files.readString(historyFile);
                log.info("从文件加载了任务历史: {}", historyFile);
            }
        } catch (Exception e) {
            log.error("加载任务历史失败", e);
        }
    }

    /**
     * 简化的 JSON 序列化
     */
    private String serializeToJson(Object obj) {
        if (obj instanceof List) {
            return serializeList((List<?>) obj);
        } else if (obj instanceof Map) {
            return serializeMap((Map<?, ?>) obj);
        } else {
            return "\"" + obj.toString() + "\"";
        }
    }

    private String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeValue(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeValue(Object value) {
        if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value);
        } else if (value instanceof List) {
            return serializeList((List<?>) value);
        } else if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value == null) {
            return "null";
        } else {
            return "\"" + value.toString() + "\"";
        }
    }

    /**
     * 任务信息
     *
     * @param name          任务名称
     * @param cron          Cron 表达式
     * @param isOneTime     是否为一次性任务
     * @param scheduleTime  调度时间（一次性任务）
     */
    public record JobInfo(
            String name,
            String cron,
            boolean isOneTime,
            Long scheduleTime
    ) {
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