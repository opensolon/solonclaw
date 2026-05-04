package com.jimuqu.solon.claw.gateway.service;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.ChannelStatus;
import com.jimuqu.solon.claw.core.service.ChannelAdapter;
import com.jimuqu.solon.claw.core.service.InboundMessageHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Centralized lifecycle manager for domestic channel adapters. */
public class ChannelConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ChannelConnectionManager.class);
    private static final long[] BACKOFF_SECONDS = new long[] {5L, 15L, 30L, 60L};

    private final Map<PlatformType, ChannelAdapter> adapters;
    private final ScheduledExecutorService reconnectExecutor;

    public ChannelConnectionManager(Map<PlatformType, ChannelAdapter> adapters) {
        this.adapters = adapters;
        this.reconnectExecutor =
                Executors.newScheduledThreadPool(
                        1,
                        new ThreadFactory() {
                            private final AtomicInteger sequence = new AtomicInteger(1);

                            @Override
                            public Thread newThread(Runnable runnable) {
                                Thread thread =
                                        new Thread(
                                                runnable,
                                                "channel-reconnect-" + sequence.getAndIncrement());
                                thread.setDaemon(true);
                                return thread;
                            }
                        });
    }

    public Map<PlatformType, ChannelAdapter> adapters() {
        return adapters;
    }

    public void bindInboundHandler(final InboundMessageHandler handler) {
        for (ChannelAdapter adapter : adapters.values()) {
            adapter.setInboundMessageHandler(handler);
        }
    }

    public void startAll() {
        for (ChannelAdapter adapter : adapters.values()) {
            connectIsolated(adapter, 0);
        }
    }

    public void refreshAll() {
        for (ChannelAdapter adapter : adapters.values()) {
            disconnectIsolated(adapter);
            connectIsolated(adapter, 0);
        }
    }

    public void disconnectAll() {
        for (ChannelAdapter adapter : adapters.values()) {
            disconnectIsolated(adapter);
        }
    }

    public List<ChannelStatus> statusSnapshots() {
        List<ChannelStatus> statuses = new ArrayList<ChannelStatus>();
        for (ChannelAdapter adapter : adapters.values()) {
            statuses.add(adapter.statusSnapshot());
        }
        return statuses;
    }

    public void scheduleReconnect(final PlatformType platform) {
        final ChannelAdapter adapter = adapters.get(platform);
        if (adapter == null) {
            return;
        }
        scheduleReconnect(adapter, 0);
    }

    public void shutdown() {
        reconnectExecutor.shutdownNow();
        disconnectAll();
    }

    private void connectIsolated(ChannelAdapter adapter, int attempt) {
        try {
            boolean connected = adapter.connect();
            log.info(
                    "[CHANNEL] platform={}, enabled={}, connected={}, detail={}",
                    adapter.platform(),
                    adapter.isEnabled(),
                    connected,
                    adapter.detail());
            if (adapter.isEnabled() && !connected) {
                scheduleReconnect(adapter, attempt);
            }
        } catch (Exception e) {
            log.warn(
                    "[CHANNEL] platform={} connect failed, application will continue: {}",
                    adapter.platform(),
                    e.getMessage(),
                    e);
            disconnectIsolated(adapter);
            scheduleReconnect(adapter, attempt);
        }
    }

    private void disconnectIsolated(ChannelAdapter adapter) {
        try {
            adapter.disconnect();
        } catch (Exception e) {
            log.debug(
                    "[CHANNEL] platform={} disconnect failed: {}",
                    adapter.platform(),
                    e.getMessage(),
                    e);
        }
    }

    private void scheduleReconnect(final ChannelAdapter adapter, final int attempt) {
        if (!adapter.isEnabled()) {
            return;
        }
        int index = Math.min(attempt, BACKOFF_SECONDS.length - 1);
        reconnectExecutor.schedule(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!adapter.isConnected()) {
                            connectIsolated(adapter, attempt + 1);
                        }
                    }
                },
                BACKOFF_SECONDS[index],
                TimeUnit.SECONDS);
    }
}
