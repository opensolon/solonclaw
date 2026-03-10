package com.jimuqu.solonclaw.agent.subagent;

import com.jimuqu.solonclaw.agent.event.AgentInternalEvent;
import com.jimuqu.solonclaw.agent.event.InternalEventListener;
import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子 Agent 管理器
 * <p>
 * 管理子 Agent 的生命周期、事件通知和资源清理
 * 参考 OpenClaw 的 subagent-registry 设计
 *
 * @author SolonClaw
 */
@Component
public class SubagentManager {

    private static final Logger log = LoggerFactory.getLogger(SubagentManager.class);

    /**
     * 默认最大并发子 Agent 数
     */
    private static final int DEFAULT_MAX_CHILDREN_PER_AGENT = 5;

    /**
     * 默认最大生成深度
     */
    private static final int DEFAULT_MAX_SPAWN_DEPTH = 10;

    /**
     * 活跃的子 Agent 运行记录
     * Key: runId, Value: SubagentRun
     */
    private final Map<String, SubagentRun> activeRuns = new ConcurrentHashMap<>();

    /**
     * 每个会话的活跃子 Agent 计数
     * Key: parentSessionKey, Value: count
     */
    private final Map<String, AtomicInteger> sessionChildCounts = new ConcurrentHashMap<>();

    /**
     * 会话生成深度
     * Key: sessionKey, Value: depth
     */
    private final Map<String, Integer> sessionDepths = new ConcurrentHashMap<>();

    /**
     * 内部事件队列
     */
    private final BlockingQueue<AgentInternalEvent> eventQueue = new LinkedBlockingQueue<>();

    /**
     * 事件处理线程
     */
    private volatile Thread eventProcessingThread;

    /**
     * 运行标志
     */
    private volatile boolean running = false;

    /**
     * 最大并发子 Agent 数
     */
    private final int maxChildrenPerAgent;

    /**
     * 最大生成深度
     */
    private final int maxSpawnDepth;

    /**
     * 事件监听器列表
     */
    private final List<InternalEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public SubagentManager() {
        this(DEFAULT_MAX_CHILDREN_PER_AGENT, DEFAULT_MAX_SPAWN_DEPTH);
    }

    public SubagentManager(int maxChildrenPerAgent, int maxSpawnDepth) {
        this.maxChildrenPerAgent = maxChildrenPerAgent;
        this.maxSpawnDepth = maxSpawnDepth;
        startEventProcessing();
    }

    /**
     * 添加事件监听器
     *
     * @param listener 监听器
     */
    public void addEventListener(InternalEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
            log.info("已添加内部事件监听器，当前监听器数：{}", eventListeners.size());
        }
    }

    /**
     * 移除事件监听器
     *
     * @param listener 监听器
     */
    public void removeEventListener(InternalEventListener listener) {
        if (listener != null) {
            eventListeners.remove(listener);
            log.info("已移除内部事件监听器，当前监听器数：{}", eventListeners.size());
        }
    }

    /**
     * 启动事件处理线程
     */
    private void startEventProcessing() {
        if (running) {
            return;
        }

        running = true;
        eventProcessingThread = new Thread(() -> {
            log.info("子 Agent 事件处理线程已启动");
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    AgentInternalEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
                    if (event != null) {
                        handleInternalEvent(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("处理内部事件异常", e);
                }
            }
            log.info("子 Agent 事件处理线程已停止");
        }, "SubagentEventProcessor");
        eventProcessingThread.start();
    }

    /**
     * 停止事件处理
     */
    public void shutdown() {
        running = false;
        if (eventProcessingThread != null) {
            eventProcessingThread.interrupt();
        }
    }

    /**
     * 注册子 Agent 运行
     *
     * @param run 运行信息
     * @return 是否注册成功
     */
    public boolean registerRun(SubagentRun run) {
        String parentSessionKey = run.getParentSessionKey();

        // 检查并发限制
        if (getActiveChildCount(parentSessionKey) >= maxChildrenPerAgent) {
            log.warn("达到最大子 Agent 数限制: session={}, max={}",
                    parentSessionKey, maxChildrenPerAgent);
            return false;
        }

        // 检查深度限制
        Integer currentDepth = sessionDepths.get(parentSessionKey);
        if (currentDepth != null && currentDepth >= maxSpawnDepth) {
            log.warn("达到最大生成深度限制: session={}, depth={}, max={}",
                    parentSessionKey, currentDepth, maxSpawnDepth);
            return false;
        }

        activeRuns.put(run.getRunId(), run);
        sessionChildCounts.computeIfAbsent(parentSessionKey, k -> new AtomicInteger(0)).incrementAndGet();

        log.info("注册子 Agent 运行: runId={}, session={}, task={}",
                run.getRunId(), parentSessionKey, run.getTaskLabel());

        return true;
    }

    /**
     * 完成子 Agent 运行
     *
     * @param runId 运行 ID
     * @param outcome 结果
     */
    public void completeRun(String runId, RunOutcome outcome) {
        SubagentRun run = activeRuns.remove(runId);
        if (run == null) {
            log.warn("未找到子 Agent 运行记录: runId={}", runId);
            return;
        }

        String parentSessionKey = run.getParentSessionKey();
        sessionChildCounts.computeIfPresent(parentSessionKey, (k, v) -> {
            int newValue = v.decrementAndGet();
            return newValue > 0 ? v : null;
        });

        // 生成内部事件
        AgentInternalEvent event = AgentInternalEvent.taskCompletion(
                AgentInternalEvent.EventSource.SUBAGENT,
                run.getChildSessionKey(),
                run.getChildSessionId(),
                run.getTaskLabel(),
                toEventStatus(outcome),
                run.getResult(),
                run.getStatsLine(),
                run.getReplyInstruction()
        );

        // 将事件放入队列
        eventQueue.offer(event);

        log.info("完成子 Agent 运行: runId={}, session={}, outcome={}",
                runId, parentSessionKey, outcome);
    }

    /**
     * 获取活跃子 Agent 数
     */
    public int getActiveChildCount(String parentSessionKey) {
        AtomicInteger count = sessionChildCounts.get(parentSessionKey);
        return count != null ? count.get() : 0;
    }

    /**
     * 获取会话深度
     */
    public int getSessionDepth(String sessionKey) {
        return sessionDepths.getOrDefault(sessionKey, 0);
    }

    /**
     * 设置会话深度
     */
    public void setSessionDepth(String sessionKey, int depth) {
        sessionDepths.put(sessionKey, depth);
    }

    /**
     * 获取活跃运行数
     */
    public int getActiveRunCount() {
        return activeRuns.size();
    }

    /**
     * 处理内部事件
     */
    private void handleInternalEvent(AgentInternalEvent event) {
        log.debug("处理内部事件: type={}, source={}, task={}",
                event.getType(), event.getSource(), event.getTaskLabel());

        // 通知所有注册的监听器
        for (InternalEventListener listener : eventListeners) {
            try {
                listener.onInternalEvent(event);
            } catch (Exception e) {
                log.error("事件监听器处理异常", e);
            }
        }
    }

    /**
     * 转换运行结果为事件状态
     */
    private AgentInternalEvent.EventStatus toEventStatus(RunOutcome outcome) {
        return switch (outcome) {
            case OK -> AgentInternalEvent.EventStatus.OK;
            case ERROR -> AgentInternalEvent.EventStatus.ERROR;
            case TIMEOUT -> AgentInternalEvent.EventStatus.TIMEOUT;
            default -> AgentInternalEvent.EventStatus.UNKNOWN;
        };
    }

    /**
     * 子 Agent 运行信息
     */
    public static class SubagentRun {
        private final String runId;
        private final String parentSessionKey;
        private final String childSessionKey;
        private final String childSessionId;
        private final String taskLabel;
        private final String task;
        private final long startTime;
        private String result;
        private String statsLine;
        private String replyInstruction;

        public SubagentRun(
                String runId,
                String parentSessionKey,
                String childSessionKey,
                String childSessionId,
                String taskLabel,
                String task) {
            this.runId = runId;
            this.parentSessionKey = parentSessionKey;
            this.childSessionKey = childSessionKey;
            this.childSessionId = childSessionId;
            this.taskLabel = taskLabel;
            this.task = task;
            this.startTime = System.currentTimeMillis();
        }

        // Getters and Setters
        public String getRunId() { return runId; }
        public String getParentSessionKey() { return parentSessionKey; }
        public String getChildSessionKey() { return childSessionKey; }
        public String getChildSessionId() { return childSessionId; }
        public String getTaskLabel() { return taskLabel; }
        public String getTask() { return task; }
        public long getStartTime() { return startTime; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getStatsLine() { return statsLine; }
        public void setStatsLine(String statsLine) { this.statsLine = statsLine; }
        public String getReplyInstruction() { return replyInstruction; }
        public void setReplyInstruction(String replyInstruction) { this.replyInstruction = replyInstruction; }
    }

    /**
     * 运行结果
     */
    public enum RunOutcome {
        OK,
        ERROR,
        TIMEOUT,
        KILLED
    }
}
