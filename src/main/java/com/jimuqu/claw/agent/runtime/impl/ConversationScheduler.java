package com.jimuqu.claw.agent.runtime.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 负责按会话维度控制运行任务并发数。
 * <p>
 * 支持双队列模式：用户消息走独立队列（默认 maxConcurrent=1 串行），
 * 系统事件和子任务走并行队列（默认 maxConcurrent=4）。
 */
public class ConversationScheduler {

    /**
     * 调度提交的运行类型。
     */
    public enum SchedulerRunType {
        /** 用户消息，走串行队列。 */
        USER_MESSAGE,
        /** 系统事件（含 continuation、heartbeat、job），走并行队列。 */
        SYSTEM_EVENT,
        /** 子任务，走并行队列（子任务使用独立 sessionKey，天然隔离）。 */
        CHILD_TASK
    }

    /** 系统事件/子任务的单会话最大并发数。 */
    private final int maxConcurrentPerConversation;
    /** 用户消息的单会话最大并发数。 */
    private final int maxConcurrentUserMessage;
    /** 实际执行任务的线程池。 */
    private final ExecutorService executor = Executors.newCachedThreadPool();
    /** 系统事件队列（按 sessionKey 隔离）。 */
    private final Map<String, SessionQueue> systemEventQueues = new ConcurrentHashMap<String, SessionQueue>();
    /** 用户消息队列（按 sessionKey 隔离）。 */
    private final Map<String, SessionQueue> userMessageQueues = new ConcurrentHashMap<String, SessionQueue>();

    /**
     * 创建会话调度器。
     *
     * @param maxConcurrentPerConversation 系统事件单会话最大并发数
     * @param maxConcurrentUserMessage     用户消息单会话最大并发数
     */
    public ConversationScheduler(int maxConcurrentPerConversation, int maxConcurrentUserMessage) {
        this.maxConcurrentPerConversation = Math.max(1, maxConcurrentPerConversation);
        this.maxConcurrentUserMessage = Math.max(1, maxConcurrentUserMessage);
    }

    /**
     * 创建会话调度器（兼容旧构造器，用户消息默认串行）。
     *
     * @param maxConcurrentPerConversation 单会话最大并发数
     */
    public ConversationScheduler(int maxConcurrentPerConversation) {
        this(maxConcurrentPerConversation, 1);
    }

    /**
     * 查看某个会话在系统事件队列中的执行状态。
     *
     * @param sessionKey 会话键
     * @return 会话状态快照
     */
    public SessionState inspect(String sessionKey) {
        SessionQueue queue = systemEventQueues.computeIfAbsent(sessionKey, key -> new SessionQueue());
        synchronized (queue) {
            return new SessionState(queue.active, queue.waiting.size());
        }
    }

    /**
     * 查看某个会话在用户消息队列中的执行状态。
     *
     * @param sessionKey 会话键
     * @return 会话状态快照
     */
    public SessionState inspectUserMessage(String sessionKey) {
        SessionQueue queue = userMessageQueues.computeIfAbsent(sessionKey, key -> new SessionQueue());
        synchronized (queue) {
            return new SessionState(queue.active, queue.waiting.size());
        }
    }

    /**
     * 提交一个会话任务到调度器（默认走系统事件队列，兼容旧调用）。
     *
     * @param sessionKey 会话键
     * @param runnable   任务逻辑
     */
    public void submit(String sessionKey, Runnable runnable) {
        submit(sessionKey, SchedulerRunType.SYSTEM_EVENT, runnable);
    }

    /**
     * 按运行类型提交任务到对应队列。
     *
     * @param sessionKey 会话键
     * @param runType    运行类型
     * @param runnable   任务逻辑
     */
    public void submit(String sessionKey, SchedulerRunType runType, Runnable runnable) {
        if (runType == SchedulerRunType.USER_MESSAGE) {
            submitToQueue(sessionKey, userMessageQueues, maxConcurrentUserMessage, runnable);
        } else {
            submitToQueue(sessionKey, systemEventQueues, maxConcurrentPerConversation, runnable);
        }
    }

    /**
     * 停止调度器线程池。
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void submitToQueue(String sessionKey, Map<String, SessionQueue> queues, int maxConcurrent, Runnable runnable) {
        SessionQueue queue = queues.computeIfAbsent(sessionKey, key -> new SessionQueue());
        synchronized (queue) {
            if (queue.active < maxConcurrent) {
                queue.active++;
                dispatch(sessionKey, queues, queue, runnable);
            } else {
                queue.waiting.addLast(runnable);
            }
        }
    }

    private void dispatch(String sessionKey, Map<String, SessionQueue> queues, SessionQueue queue, Runnable runnable) {
        executor.submit(() -> {
            try {
                runnable.run();
            } finally {
                onTaskFinished(sessionKey, queues, queue);
            }
        });
    }

    private void onTaskFinished(String sessionKey, Map<String, SessionQueue> queues, SessionQueue queue) {
        Runnable next = null;
        synchronized (queue) {
            queue.active--;
            if (!queue.waiting.isEmpty()) {
                queue.active++;
                next = queue.waiting.removeFirst();
            } else if (queue.active == 0) {
                queues.remove(sessionKey, queue);
            }
        }

        if (next != null) {
            dispatch(sessionKey, queues, queue, next);
        }
    }

    /**
     * 保存单个会话的活跃数和等待队列。
     */
    private static class SessionQueue {
        /** 当前执行中的任务数。 */
        private int active;
        /** 当前等待中的任务队列。 */
        private final Deque<Runnable> waiting = new ArrayDeque<Runnable>();
    }

    /**
     * 表示会话在某个时刻的调度快照。
     */
    public static final class SessionState {
        private final int activeCount;
        private final int queuedCount;

        public SessionState(int activeCount, int queuedCount) {
            this.activeCount = activeCount;
            this.queuedCount = queuedCount;
        }

        public int activeCount() {
            return activeCount;
        }

        public int queuedCount() {
            return queuedCount;
        }

        /**
         * 判断当前会话是否繁忙。
         *
         * @return 若存在活跃或排队任务则返回 true
         */
        public boolean isBusy() {
            return activeCount > 0 || queuedCount > 0;
        }
    }
}
