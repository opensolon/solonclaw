package com.jimuqu.claw.agent.runtime.registry;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.run.AgentRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 活跃任务注册表。
 * <p>
 * 维护当前正在执行的子任务状态，支持进度更新与取消控制。
 */
public class ActiveTaskRegistry {
    private static final Logger log = LoggerFactory.getLogger(ActiveTaskRegistry.class);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ActiveTaskEntry>> byParentSession =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<ActiveTaskEntry>>();
    private final ConcurrentHashMap<String, ActiveTaskEntry> byRunId =
            new ConcurrentHashMap<String, ActiveTaskEntry>();
    private final File runsDir;

    public ActiveTaskRegistry(File runsDir) {
        this.runsDir = runsDir;
    }

    public void register(String parentSessionKey, AgentRun childRun) {
        if (childRun == null || StrUtil.isBlank(childRun.getRunId())) {
            return;
        }

        ActiveTaskEntry entry = new ActiveTaskEntry();
        entry.setRunId(childRun.getRunId());
        entry.setParentRunId(childRun.getParentRunId());
        entry.setParentSessionKey(parentSessionKey);
        entry.setChildSessionKey(childRun.getSessionKey());
        entry.setTaskTitle(childRun.getTaskTitle());
        entry.setTaskDescription(childRun.getTaskDescription());
        entry.setBatchKey(childRun.getBatchKey());
        entry.setStatus(childRun.getStatus());
        entry.setCreatedAt(childRun.getCreatedAt());

        byRunId.put(childRun.getRunId(), entry);
        byParentSession.computeIfAbsent(parentSessionKey, key -> new CopyOnWriteArrayList<ActiveTaskEntry>()).add(entry);
    }

    public void updateProgress(String runId, String phase, String detail) {
        ActiveTaskEntry entry = byRunId.get(runId);
        if (entry == null) {
            return;
        }
        entry.setLatestPhase(phase);
        entry.setLatestProgressDetail(detail);
        entry.setLatestProgressAt(System.currentTimeMillis());
    }

    public void updateStatus(String runId, RunStatus status) {
        ActiveTaskEntry entry = byRunId.get(runId);
        if (entry != null) {
            entry.setStatus(status);
        }
    }

    public void markCompleted(String runId, RunStatus finalStatus) {
        ActiveTaskEntry entry = byRunId.remove(runId);
        if (entry == null) {
            return;
        }
        entry.setStatus(finalStatus);
        entry.setExecutionThread(null);

        CopyOnWriteArrayList<ActiveTaskEntry> list = byParentSession.get(entry.getParentSessionKey());
        if (list != null) {
            list.removeIf(e -> StrUtil.equals(e.getRunId(), runId));
            if (list.isEmpty()) {
                byParentSession.remove(entry.getParentSessionKey(), list);
            }
        }
    }

    public void markCancelled(String runId) {
        markCompleted(runId, RunStatus.CANCELLED);
    }

    public List<ActiveTaskEntry> getActiveTasks(String parentSessionKey) {
        CopyOnWriteArrayList<ActiveTaskEntry> list = byParentSession.get(parentSessionKey);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<ActiveTaskEntry>(list);
    }

    public ActiveTaskEntry getEntry(String runId) {
        return byRunId.get(runId);
    }

    public void setExecutionThread(String runId, Thread thread) {
        ActiveTaskEntry entry = byRunId.get(runId);
        if (entry != null) {
            entry.setExecutionThread(thread);
        }
    }

    public void requestCancel(String runId) {
        ActiveTaskEntry entry = byRunId.get(runId);
        if (entry != null) {
            entry.setCancelRequested(true);
        }
    }

    public boolean isCancelRequested(String runId) {
        ActiveTaskEntry entry = byRunId.get(runId);
        return entry != null && entry.isCancelRequested();
    }

    public Thread getExecutionThread(String runId) {
        ActiveTaskEntry entry = byRunId.get(runId);
        return entry == null ? null : entry.getExecutionThread();
    }

    public void rebuildFromDisk() {
        if (runsDir == null || !runsDir.exists()) {
            return;
        }

        int count = 0;
        List<File> files = FileUtil.loopFiles(runsDir, pathname -> pathname.isFile() && pathname.getName().endsWith(".json"));
        for (File file : files) {
            try {
                AgentRun run = JSONUtil.toBean(FileUtil.readUtf8String(file), AgentRun.class);
                if (run == null || StrUtil.isBlank(run.getParentRunId()) || StrUtil.isBlank(run.getParentSessionKey())) {
                    continue;
                }
                if (run.getStatus() == RunStatus.QUEUED || run.getStatus() == RunStatus.RUNNING) {
                    register(run.getParentSessionKey(), run);
                    count++;
                }
            } catch (Exception ignored) {
            }
        }

        if (count > 0) {
            log.info("ActiveTaskRegistry rebuilt from disk: {} active tasks", count);
        }
    }
}
