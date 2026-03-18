package com.jimuqu.claw.agent.job;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 在工作区 jobs.json 中存取定时任务定义。
 */
public class JobStoreService {
    private final File jobsFile;
    private final ReentrantLock lock = new ReentrantLock();

    public JobStoreService(AgentWorkspaceService workspaceService) {
        this.jobsFile = workspaceService.fileInWorkspace("jobs.json");
        ensureFileExists();
    }

    public File getJobsFile() {
        return jobsFile;
    }

    public List<JobDefinition> loadAll() {
        lock.lock();
        try {
            return loadAllInternal();
        } finally {
            lock.unlock();
        }
    }

    public JobDefinition get(String name) {
        return loadAll().stream()
                .filter(job -> StrUtil.equals(name, job.getName()))
                .findFirst()
                .orElse(null);
    }

    public void save(JobDefinition definition) {
        lock.lock();
        try {
            List<JobDefinition> jobs = loadAllInternal();
            jobs.removeIf(job -> StrUtil.equals(job.getName(), definition.getName()));
            jobs.add(definition);
            jobs.sort(Comparator.comparing(JobDefinition::getName, String.CASE_INSENSITIVE_ORDER));
            FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(jobs), jobsFile);
        } finally {
            lock.unlock();
        }
    }

    public void remove(String name) {
        lock.lock();
        try {
            List<JobDefinition> jobs = loadAllInternal();
            jobs.removeIf(job -> StrUtil.equals(job.getName(), name));
            FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(jobs), jobsFile);
        } finally {
            lock.unlock();
        }
    }

    private List<JobDefinition> loadAllInternal() {
        ensureFileExists();
        String json = FileUtil.readUtf8String(jobsFile).trim();
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }

        return new ArrayList<>(JSONUtil.toList(json, JobDefinition.class));
    }

    private void ensureFileExists() {
        if (!jobsFile.exists()) {
            File parent = jobsFile.getParentFile();
            if (parent != null) {
                FileUtil.mkdir(parent);
            }
            FileUtil.writeUtf8String("[]", jobsFile);
        }
    }
}
