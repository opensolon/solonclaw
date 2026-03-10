package com.jimuqu.solonclaw.agent.subagent;

import com.jimuqu.solonclaw.agent.AgentService;
import com.jimuqu.solonclaw.agent.event.AgentInternalEvent;
import com.jimuqu.solonclaw.agent.event.EventStore;
import com.jimuqu.solonclaw.agent.event.InternalEventListener;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 子 Agent 生成服务
 * <p>
 * 负责创建和管理子 Agent，支持任务分解和并行执行
 * 参考 OpenClaw 的 subagent-spawn 设计
 *
 * @author SolonClaw
 */
@Component
public class SubagentSpawnService {

    private static final Logger log = LoggerFactory.getLogger(SubagentSpawnService.class);

    /**
     * 默认超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    @Inject
    private SubagentManager subagentManager;

    @Inject
    private AgentService agentService;

    @Inject
    private EventStore eventStore;

    /**
     * 子 Agent 执行线程池
     */
    private final ExecutorService executorService;

    public SubagentSpawnService() {
        // 创建线程池用于执行子 Agent
        this.executorService = new ThreadPoolExecutor(
                5, // 核心线程数
                20, // 最大线程数
                60L, // 空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100), // 任务队列
                new ThreadFactory() {
                    private final ThreadGroup group = Thread.currentThread().getThreadGroup();
                    private int count = 1;

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(group, r, "Subagent-" + count++);
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行
        );
    }

    /**
     * 生成子 Agent（同步）
     *
     * @param params 生成参数
     * @return 生成结果
     */
    public SpawnResult spawn(SpawnParams params) {
        return spawn(params, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 生成子 Agent（同步，带超时）
     *
     * @param params 生成参数
     * @param timeoutSeconds 超时时间（秒）
     * @return 生成结果
     */
    public SpawnResult spawn(SpawnParams params, int timeoutSeconds) {
        log.info("开始生成子 Agent: taskLabel={}, parentSession={}",
                params.getTaskLabel(), params.getParentSessionKey());

        // 创建子会话键
        String childSessionKey = deriveChildSessionKey(params.getParentSessionKey());
        String runId = UUID.randomUUID().toString();
        String childSessionId = UUID.randomUUID().toString();

        // 创建运行记录
        SubagentManager.SubagentRun run = new SubagentManager.SubagentRun(
                runId,
                params.getParentSessionKey(),
                childSessionKey,
                childSessionId,
                params.getTaskLabel(),
                params.getTask()
        );

        // 设置运行时属性
        if (params.getReplyInstruction() != null) {
            run.setReplyInstruction(params.getReplyInstruction());
        }

        // 注册运行
        if (!subagentManager.registerRun(run)) {
            log.warn("子 Agent 注册失败: runId={}", runId);
            return new SpawnResult(false, "达到并发或深度限制", null, null, null);
        }

        try {
            // 异步执行子 Agent
            Future<String> future = executorService.submit(() -> {
                return executeSubagent(run, params);
            });

            // 等待执行完成
            String result = future.get(timeoutSeconds, TimeUnit.SECONDS);

            // 更新运行结果
            run.setResult(result);
            run.setStatsLine(buildStatsLine(result));

            // 标记完成
            subagentManager.completeRun(runId, SubagentManager.RunOutcome.OK);

            // 创建内部事件
            AgentInternalEvent event = createTaskCompletionEvent(
                    run, result, AgentInternalEvent.EventStatus.OK);
            eventStore.addEvent(event);

            log.info("子 Agent 执行完成: runId={}, session={}, resultLength={}, 事件已创建",
                    runId, childSessionKey, result != null ? result.length() : 0);

            return new SpawnResult(true, "执行成功", childSessionKey, runId, result);

        } catch (TimeoutException e) {
            log.warn("子 Agent 执行超时: runId={}", runId);
            subagentManager.completeRun(runId, SubagentManager.RunOutcome.TIMEOUT);

            // 创建超时事件
            AgentInternalEvent event = createTaskCompletionEvent(
                    run, "执行超时", AgentInternalEvent.EventStatus.TIMEOUT);
            eventStore.addEvent(event);

            return new SpawnResult(false, "执行超时", childSessionKey, runId, null);

        } catch (Exception e) {
            log.error("子 Agent 执行异常: runId={}", runId, e);
            subagentManager.completeRun(runId, SubagentManager.RunOutcome.ERROR);

            // 创建错误事件
            AgentInternalEvent event = createTaskCompletionEvent(
                    run, "执行异常: " + e.getMessage(), AgentInternalEvent.EventStatus.ERROR);
            eventStore.addEvent(event);

            return new SpawnResult(false, "执行异常: " + e.getMessage(), childSessionKey, runId, null);
        }
    }

    /**
     * 生成子 Agent（异步）
     *
     * @param params 生成参数
     * @param callback 回调函数
     */
    public void spawnAsync(SpawnParams params, SpawnCallback callback) {
        executorService.submit(() -> {
            SpawnResult result = spawn(params);
            if (callback != null) {
                callback.onComplete(result);
            }
        });
    }

    /**
     * 并行生成多个子 Agent
     *
     * @param paramsList 参数列表
     * @return 结果列表
     */
    public List<SpawnResult> spawnParallel(List<SpawnParams> paramsList) {
        log.info("并行生成 {} 个子 Agent", paramsList.size());

        List<SpawnResult> results = new ArrayList<>();
        List<Future<SpawnResult>> futures = new ArrayList<>();

        for (SpawnParams params : paramsList) {
            Future<SpawnResult> future = executorService.submit(() -> spawn(params));
            futures.add(future);
        }

        for (Future<SpawnResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.error("并行执行异常", e);
                results.add(new SpawnResult(false, "并行执行异常: " + e.getMessage(), null, null, null));
            }
        }

        log.info("并行执行完成: 成功={}, 总数={}",
                results.stream().mapToInt(r -> r.success() ? 1 : 0).sum(),
                results.size());

        return results;
    }

    /**
     * 执行子 Agent
     */
    private String executeSubagent(SubagentManager.SubagentRun run, SpawnParams params) {
        log.info("执行子 Agent: runId={}, task={}", run.getRunId(), run.getTaskLabel());

        try {
            // 使用 AgentService 执行任务
            String response = agentService.chat(run.getTask(), run.getChildSessionKey());

            log.debug("子 Agent 响应: runId={}, length={}",
                    run.getRunId(), response != null ? response.length() : 0);

            return response;

        } catch (Exception e) {
            log.error("子 Agent 执行失败: runId={}", run.getRunId(), e);
            throw new RuntimeException("子 Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 派生子会话键
     */
    private String deriveChildSessionKey(String parentSessionKey) {
        // 简单实现：在父会话键后添加时间戳和随机数
        return parentSessionKey + "-child-" + System.currentTimeMillis() + "-" +
                (int)(Math.random() * 1000);
    }

    /**
     * 构建统计信息行
     */
    private String buildStatsLine(String result) {
        if (result == null) {
            return "No output";
        }
        int length = result.length();
        int lines = result.split("\n").length;
        return String.format("Output: %d chars, %d lines", length, lines);
    }

    /**
     * 创建任务完成事件
     */
    private AgentInternalEvent createTaskCompletionEvent(
            SubagentManager.SubagentRun run,
            String result,
            AgentInternalEvent.EventStatus status) {

        String statsLine = run.getStatsLine();
        String replyInstruction = run.getReplyInstruction();

        if (replyInstruction == null || replyInstruction.isEmpty()) {
            replyInstruction = "请根据子任务结果继续工作";
        }

        return AgentInternalEvent.taskCompletion(
                AgentInternalEvent.EventSource.SUBAGENT,
                run.getChildSessionKey(),
                run.getChildSessionId(),
                run.getTaskLabel(),
                status,
                result,
                statsLine,
                replyInstruction
        );
    }

    /**
     * 添加事件监听器
     */
    public void addEventListener(InternalEventListener listener) {
        subagentManager.addEventListener(listener);
    }

    /**
     * 移除事件监听器
     */
    public void removeEventListener(InternalEventListener listener) {
        subagentManager.removeEventListener(listener);
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        log.info("关闭子 Agent 生成服务...");
        subagentManager.shutdown();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("子 Agent 生成服务已关闭");
    }

    /**
     * 子 Agent 生成模式
     */
    public enum SpawnMode {
        /** 一次性运行模式 */
        RUN,
        /** 持久会话模式（支持多轮对话） */
        SESSION
    }

    /**
     * 沙箱模式
     */
    public enum SandboxMode {
        /** 继承父 Agent 的沙箱设置 */
        INHERIT,
        /** 要求子 Agent 必须在沙箱中运行 */
        REQUIRE
    }

    /**
     * 清理策略
     */
    public enum CleanupStrategy {
        /** 保留子会话 */
        KEEP,
        /** 删除子会话 */
        DELETE
    }

    /**
     * 生成参数
     * <p>
     * 参考 OpenClaw 的 SpawnSubagentParams 设计
     */
    public static class SpawnParams {
        private final String parentSessionKey;
        private final String taskLabel;
        private final String task;
        private String replyInstruction;
        private String modelId;
        private String thinkingLevel;
        private Integer timeoutSeconds;
        private boolean threadRequested;
        private SpawnMode spawnMode;
        private CleanupStrategy cleanup;
        private SandboxMode sandboxMode;
        private boolean expectsCompletionMessage;

        public SpawnParams(
                String parentSessionKey,
                String taskLabel,
                String task) {
            this.parentSessionKey = parentSessionKey;
            this.taskLabel = taskLabel;
            this.task = task;
            this.spawnMode = SpawnMode.RUN;
            this.cleanup = CleanupStrategy.KEEP;
            this.sandboxMode = SandboxMode.INHERIT;
            this.expectsCompletionMessage = true;
            this.threadRequested = false;
        }

        public SpawnParams setReplyInstruction(String replyInstruction) {
            this.replyInstruction = replyInstruction;
            return this;
        }

        public SpawnParams setModelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        /**
         * 设置思考级别
         * 参考 OpenClaw 的 thinking 参数
         * 可选值: "off", "minimal", "low", "medium", "high", "xhigh", "adaptive"
         */
        public SpawnParams setThinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        /**
         * 设置超时时间（秒）
         */
        public SpawnParams setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        /**
         * 设置是否请求线程绑定（支持多轮对话）
         */
        public SpawnParams setThreadRequested(boolean threadRequested) {
            this.threadRequested = threadRequested;
            if (threadRequested && this.spawnMode == SpawnMode.RUN) {
                // 线程绑定应默认使用 SESSION 模式
                this.spawnMode = SpawnMode.SESSION;
            }
            return this;
        }

        /**
         * 设置生成模式
         */
        public SpawnParams setSpawnMode(SpawnMode spawnMode) {
            this.spawnMode = spawnMode;
            return this;
        }

        /**
         * 设置清理策略
         */
        public SpawnParams setCleanup(CleanupStrategy cleanup) {
            this.cleanup = cleanup;
            return this;
        }

        /**
         * 设置沙箱模式
         */
        public SpawnParams setSandboxMode(SandboxMode sandboxMode) {
            this.sandboxMode = sandboxMode;
            return this;
        }

        /**
         * 设置是否期望完成消息
         */
        public SpawnParams setExpectsCompletionMessage(boolean expectsCompletionMessage) {
            this.expectsCompletionMessage = expectsCompletionMessage;
            return this;
        }

        // Getters
        public String getParentSessionKey() { return parentSessionKey; }
        public String getTaskLabel() { return taskLabel; }
        public String getTask() { return task; }
        public String getReplyInstruction() { return replyInstruction; }
        public String getModelId() { return modelId; }
        public String getThinkingLevel() { return thinkingLevel; }
        public Integer getTimeoutSeconds() { return timeoutSeconds; }
        public boolean isThreadRequested() { return threadRequested; }
        public SpawnMode getSpawnMode() { return spawnMode; }
        public CleanupStrategy getCleanup() { return cleanup; }
        public SandboxMode getSandboxMode() { return sandboxMode; }
        public boolean isExpectsCompletionMessage() { return expectsCompletionMessage; }
    }

    /**
     * 生成结果
     */
    public record SpawnResult(
            boolean success,
            String message,
            String childSessionKey,
            String runId,
            String result
    ) {}

    /**
     * 生成回调
     */
    @FunctionalInterface
    public interface SpawnCallback {
        void onComplete(SpawnResult result);
    }
}
