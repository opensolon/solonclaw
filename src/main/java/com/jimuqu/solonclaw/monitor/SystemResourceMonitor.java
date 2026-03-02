package com.jimuqu.solonclaw.monitor;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 系统资源监控服务
 * 定期监控 CPU、内存、磁盘等资源使用情况
 */
@Component
public class SystemResourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(SystemResourceMonitor.class);

    @Inject
    private PerformanceMonitor performanceMonitor;

    private ScheduledExecutorService scheduler;

    // 监控间隔（秒）
    private int monitoringInterval = 60;

    // 资源使用历史（最近 N 次采样）
    private static final int MAX_HISTORY = 60;
    private final java.util.concurrent.ConcurrentLinkedDeque<ResourceSnapshot> resourceHistory = new java.util.concurrent.ConcurrentLinkedDeque<>();

    @Init
    public void init() {
        startMonitoring();
    }

    /**
     * 启动监控
     */
    public void startMonitoring() {
        if (scheduler != null && !scheduler.isShutdown()) {
            log.warn("System resource monitor is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "system-resource-monitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::collectAndCheck,
                10, // 初始延迟 10 秒
                monitoringInterval,
                TimeUnit.SECONDS
        );

        log.info("System resource monitor started, interval: {} seconds", monitoringInterval);
    }

    /**
     * 停止监控
     */
    public void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("System resource monitor stopped");
        }
    }

    /**
     * 收集并检查资源
     */
    private void collectAndCheck() {
        try {
            ResourceSnapshot snapshot = collectSnapshot();

            // 添加到历史记录
            resourceHistory.addLast(snapshot);
            while (resourceHistory.size() > MAX_HISTORY) {
                resourceHistory.removeFirst();
            }

            // 检查资源使用情况
            performanceMonitor.checkSystemResources();

        } catch (Exception e) {
            log.error("Error collecting system resources", e);
        }
    }

    /**
     * 收集资源快照
     */
    public ResourceSnapshot collectSnapshot() {
        ResourceSnapshot snapshot = new ResourceSnapshot();
        snapshot.setTimestamp(System.currentTimeMillis());

        // JVM 内存
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        snapshot.setHeapUsed(heapUsage.getUsed());
        snapshot.setHeapMax(heapUsage.getMax());
        snapshot.setHeapCommitted(heapUsage.getCommitted());

        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        snapshot.setNonHeapUsed(nonHeapUsage.getUsed());

        // 线程
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        snapshot.setThreadCount(threadBean.getThreadCount());
        snapshot.setDaemonThreadCount(threadBean.getDaemonThreadCount());
        snapshot.setPeakThreadCount(threadBean.getPeakThreadCount());

        // 类加载
        ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
        snapshot.setLoadedClassCount(classBean.getLoadedClassCount());
        snapshot.setTotalLoadedClassCount(classBean.getTotalLoadedClassCount());
        snapshot.setUnloadedClassCount(classBean.getUnloadedClassCount());

        // 操作系统
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        snapshot.setAvailableProcessors(osBean.getAvailableProcessors());
        snapshot.setSystemLoadAverage(osBean.getSystemLoadAverage());

        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            snapshot.setCpuLoad(sunOsBean.getCpuLoad() * 100);
            snapshot.setProcessCpuLoad(sunOsBean.getProcessCpuLoad() * 100);
            snapshot.setTotalPhysicalMemory(sunOsBean.getTotalMemorySize());
            snapshot.setFreePhysicalMemory(sunOsBean.getFreeMemorySize());

            // 进程 CPU 时间
            snapshot.setProcessCpuTime(sunOsBean.getProcessCpuTime());
        }

        // 运行时
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        snapshot.setUptime(runtimeBean.getUptime());
        snapshot.setStartTime(runtimeBean.getStartTime());

        // GC
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = gcBean.getName();
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            snapshot.addGcInfo(name, count, time);
        }

        return snapshot;
    }

    /**
     * 获取资源历史
     */
    public java.util.List<ResourceSnapshot> getResourceHistory() {
        return new java.util.ArrayList<>(resourceHistory);
    }

    /**
     * 获取当前资源使用情况
     */
    public Map<String, Object> getCurrentResources() {
        ResourceSnapshot snapshot = collectSnapshot();
        return snapshot.toMap();
    }

    /**
     * 设置监控间隔
     */
    public void setMonitoringInterval(int seconds) {
        this.monitoringInterval = seconds;
    }

    /**
     * 资源快照
     */
    public static class ResourceSnapshot {
        private long timestamp;
        private long heapUsed;
        private long heapMax;
        private long heapCommitted;
        private long nonHeapUsed;
        private int threadCount;
        private int daemonThreadCount;
        private int peakThreadCount;
        private int loadedClassCount;
        private long totalLoadedClassCount;
        private long unloadedClassCount;
        private int availableProcessors;
        private double systemLoadAverage;
        private double cpuLoad;
        private double processCpuLoad;
        private long totalPhysicalMemory;
        private long freePhysicalMemory;
        private long processCpuTime;
        private long uptime;
        private long startTime;
        private final Map<String, long[]> gcInfo = new HashMap<>();

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", timestamp);
            map.put("heap.used", heapUsed);
            map.put("heap.max", heapMax);
            map.put("heap.committed", heapCommitted);
            map.put("heap.usagePercent", String.format("%.2f%%", (double) heapUsed / heapMax * 100));
            map.put("nonHeap.used", nonHeapUsed);
            map.put("thread.count", threadCount);
            map.put("thread.daemon", daemonThreadCount);
            map.put("thread.peak", peakThreadCount);
            map.put("class.loaded", loadedClassCount);
            map.put("class.totalLoaded", totalLoadedClassCount);
            map.put("class.unloaded", unloadedClassCount);
            map.put("os.availableProcessors", availableProcessors);
            map.put("os.systemLoadAverage", systemLoadAverage);
            map.put("os.cpuLoad", String.format("%.2f%%", cpuLoad));
            map.put("os.processCpuLoad", String.format("%.2f%%", processCpuLoad));
            map.put("os.memory.total", totalPhysicalMemory);
            map.put("os.memory.free", freePhysicalMemory);
            map.put("os.memory.used", totalPhysicalMemory - freePhysicalMemory);
            map.put("runtime.uptime", uptime);
            map.put("runtime.startTime", startTime);
            map.put("gc", gcInfo);
            return map;
        }

        public void addGcInfo(String name, long count, long time) {
            gcInfo.put(name, new long[]{count, time});
        }

        // Getters and Setters
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public long getHeapUsed() { return heapUsed; }
        public void setHeapUsed(long heapUsed) { this.heapUsed = heapUsed; }
        public long getHeapMax() { return heapMax; }
        public void setHeapMax(long heapMax) { this.heapMax = heapMax; }
        public long getHeapCommitted() { return heapCommitted; }
        public void setHeapCommitted(long heapCommitted) { this.heapCommitted = heapCommitted; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public void setNonHeapUsed(long nonHeapUsed) { this.nonHeapUsed = nonHeapUsed; }
        public int getThreadCount() { return threadCount; }
        public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
        public int getDaemonThreadCount() { return daemonThreadCount; }
        public void setDaemonThreadCount(int daemonThreadCount) { this.daemonThreadCount = daemonThreadCount; }
        public int getPeakThreadCount() { return peakThreadCount; }
        public void setPeakThreadCount(int peakThreadCount) { this.peakThreadCount = peakThreadCount; }
        public int getLoadedClassCount() { return loadedClassCount; }
        public void setLoadedClassCount(int loadedClassCount) { this.loadedClassCount = loadedClassCount; }
        public long getTotalLoadedClassCount() { return totalLoadedClassCount; }
        public void setTotalLoadedClassCount(long totalLoadedClassCount) { this.totalLoadedClassCount = totalLoadedClassCount; }
        public long getUnloadedClassCount() { return unloadedClassCount; }
        public void setUnloadedClassCount(long unloadedClassCount) { this.unloadedClassCount = unloadedClassCount; }
        public int getAvailableProcessors() { return availableProcessors; }
        public void setAvailableProcessors(int availableProcessors) { this.availableProcessors = availableProcessors; }
        public double getSystemLoadAverage() { return systemLoadAverage; }
        public void setSystemLoadAverage(double systemLoadAverage) { this.systemLoadAverage = systemLoadAverage; }
        public double getCpuLoad() { return cpuLoad; }
        public void setCpuLoad(double cpuLoad) { this.cpuLoad = cpuLoad; }
        public double getProcessCpuLoad() { return processCpuLoad; }
        public void setProcessCpuLoad(double processCpuLoad) { this.processCpuLoad = processCpuLoad; }
        public long getTotalPhysicalMemory() { return totalPhysicalMemory; }
        public void setTotalPhysicalMemory(long totalPhysicalMemory) { this.totalPhysicalMemory = totalPhysicalMemory; }
        public long getFreePhysicalMemory() { return freePhysicalMemory; }
        public void setFreePhysicalMemory(long freePhysicalMemory) { this.freePhysicalMemory = freePhysicalMemory; }
        public long getProcessCpuTime() { return processCpuTime; }
        public void setProcessCpuTime(long processCpuTime) { this.processCpuTime = processCpuTime; }
        public long getUptime() { return uptime; }
        public void setUptime(long uptime) { this.uptime = uptime; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
    }
}