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
 */
public class ConversationScheduler {
    /** 单会话允许的最大并发数。 */
    private final int maxConcurrentPerConversation;
    /** 实际执行任务的线程池。 */
    private final ExecutorService executor = Executors.newCachedThreadPool();
    /** 会话键到队列状态的映射。 */
    private final Map<String, SessionQueue> sessionQueues = new ConcurrentHashMap<>();

    /**
     * 创建会话调度器。
     *
     * @param maxConcurrentPerConversation 单会话最大并发数
     */
    public ConversationScheduler(int maxConcurrentPerConversation) {
        this.maxConcurrentPerConversation = Math.max(1, maxConcurrentPerConversation);
    }

    /**
     * 查看某个会话当前的执行状态。
     *
     * @param sessionKey 会话键
     * @return 会话状态快照
     */
    public SessionState inspect(String sessionKey) {
        SessionQueue queue = sessionQueues.computeIfAbsent(sessionKey, key -> new SessionQueue());
        synchronized (queue) {
            return new SessionState(queue.active, queue.waiting.size());
        }
    }

    /**
     * 提交一个会话任务到调度器。
     *
     * @param sessionKey 会话键
     * @param runnable 任务逻辑
     */
    public void submit(String sessionKey, Runnable runnable) {
        SessionQueue queue = sessionQueues.computeIfAbsent(sessionKey, key -> new SessionQueue());
        synchronized (queue) {
            if (queue.active < maxConcurrentPerConversation) {
                queue.active++;
                dispatch(sessionKey, queue, runnable);
            } else {
                queue.waiting.addLast(runnable);
            }
        }
    }

    /**
     * 将任务提交到线程池执行。
     *
     * @param sessionKey 会话键
     * @param queue 会话队列
     * @param runnable 任务逻辑
     */
    private void dispatch(String sessionKey, SessionQueue queue, Runnable runnable) {
        executor.submit(() -> {
            try {
                runnable.run();
            } finally {
                onTaskFinished(sessionKey, queue);
            }
        });
    }

    /**
     * 在任务结束后推进队列。
     *
     * @param sessionKey 会话键
     * @param queue 会话队列
     */
    private void onTaskFinished(String sessionKey, SessionQueue queue) {
        Runnable next = null;
        synchronized (queue) {
            queue.active--;
            if (!queue.waiting.isEmpty()) {
                queue.active++;
                next = queue.waiting.removeFirst();
            } else if (queue.active == 0) {
                sessionQueues.remove(sessionKey, queue);
            }
        }

        if (next != null) {
            dispatch(sessionKey, queue, next);
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

    /**
     * 保存单个会话的活跃数和等待队列。
     */
    private static class SessionQueue {
        /** 当前执行中的任务数。 */
        private int active;
        /** 当前等待中的任务队列。 */
        private final Deque<Runnable> waiting = new ArrayDeque<>();
    }

    /**
     * 表示会话在某个时刻的调度快照。
     *
     * @param activeCount 活跃任务数
     * @param queuedCount 排队任务数
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

