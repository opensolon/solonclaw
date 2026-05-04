package com.jimuqu.solon.claw.support;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded executor factory for runtime background work. */
public final class BoundedExecutorFactory {
    private BoundedExecutorFactory() {}

    public static ExecutorService fixed(String name, int threads, int queueCapacity) {
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(Math.max(1, queueCapacity)),
                namedFactory(name),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public static ScheduledExecutorService scheduled(String name, int threads) {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(Math.max(1, threads), namedFactory(name));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static ThreadFactory namedFactory(final String name) {
        return new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name + "-" + sequence.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
