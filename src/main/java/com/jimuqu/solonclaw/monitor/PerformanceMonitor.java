package com.jimuqu.solonclaw.monitor;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控服务
 * 收集和跟踪系统性能指标
 */
@Component
public class PerformanceMonitor {
    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitor.class);

    // 请求统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    // 对话统计
    private final AtomicLong totalConversations = new AtomicLong(0);
    private final AtomicLong activeConversations = new AtomicLong(0);

    // 工具调用统计
    private final Map<String, AtomicLong> toolCallCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> toolCallErrors = new ConcurrentHashMap<>();

    // 告警阈值
    private static final double MEMORY_WARNING_THRESHOLD = 80.0;  // 80%
    private static final double MEMORY_CRITICAL_THRESHOLD = 90.0; // 90%
    private static final double CPU_WARNING_THRESHOLD = 70.0;     // 70%
    private static final double CPU_CRITICAL_THRESHOLD = 90.0;    // 90%
    private static final long RESPONSE_TIME_WARNING = 5000;       // 5秒
    private static final long RESPONSE_TIME_CRITICAL = 10000;     // 10秒

    @Inject(required = false)
    private AlertService alertService;

    @Init
    public void init() {
        log.info("Performance monitor initialized");
    }

    /**
     * 记录请求
     */
    public void recordRequest(long responseTime, boolean success) {
        totalRequests.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);

        if (success) {
            successRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }

        // 检查响应时间告警
        if (responseTime > RESPONSE_TIME_CRITICAL) {
            triggerAlert(AlertLevel.CRITICAL, "响应时间过长",
                    String.format("响应时间 %dms 超过临界阈值 %dms", responseTime, RESPONSE_TIME_CRITICAL));
        } else if (responseTime > RESPONSE_TIME_WARNING) {
            triggerAlert(AlertLevel.WARNING, "响应时间警告",
                    String.format("响应时间 %dms 超过警告阈值 %dms", responseTime, RESPONSE_TIME_WARNING));
        }
    }

    /**
     * 记录对话开始
     */
    public void recordConversationStart(String sessionId) {
        totalConversations.incrementAndGet();
        activeConversations.incrementAndGet();
    }

    /**
     * 记录对话结束
     */
    public void recordConversationEnd(String sessionId) {
        activeConversations.decrementAndGet();
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String toolName, boolean success) {
        toolCallCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        if (!success) {
            toolCallErrors.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * 检查系统资源
     */
    public void checkSystemResources() {
        // 检查内存使用
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double memoryUsage = (double) heapUsed / heapMax * 100;

        if (memoryUsage > MEMORY_CRITICAL_THRESHOLD) {
            triggerAlert(AlertLevel.CRITICAL, "内存使用率过高",
                    String.format("堆内存使用率 %.2f%% 超过临界阈值 %.2f%%", memoryUsage, MEMORY_CRITICAL_THRESHOLD));
        } else if (memoryUsage > MEMORY_WARNING_THRESHOLD) {
            triggerAlert(AlertLevel.WARNING, "内存使用率警告",
                    String.format("堆内存使用率 %.2f%% 超过警告阈值 %.2f%%", memoryUsage, MEMORY_WARNING_THRESHOLD));
        }

        // 检查 CPU 使用
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double cpuUsage = sunOsBean.getCpuLoad() * 100;
            if (cpuUsage > CPU_CRITICAL_THRESHOLD) {
                triggerAlert(AlertLevel.CRITICAL, "CPU使用率过高",
                        String.format("CPU使用率 %.2f%% 超过临界阈值 %.2f%%", cpuUsage, CPU_CRITICAL_THRESHOLD));
            } else if (cpuUsage > CPU_WARNING_THRESHOLD) {
                triggerAlert(AlertLevel.WARNING, "CPU使用率警告",
                        String.format("CPU使用率 %.2f%% 超过警告阈值 %.2f%%", cpuUsage, CPU_WARNING_THRESHOLD));
            }
        }
    }

    /**
     * 获取性能统计
     */
    public PerformanceStats getStats() {
        PerformanceStats stats = new PerformanceStats();

        // 请求统计
        stats.setTotalRequests(totalRequests.get());
        stats.setSuccessRequests(successRequests.get());
        stats.setFailedRequests(failedRequests.get());

        long requests = totalRequests.get();
        if (requests > 0) {
            stats.setAverageResponseTime(totalResponseTime.get() / requests);
            stats.setSuccessRate((double) successRequests.get() / requests * 100);
        }

        // 对话统计
        stats.setTotalConversations(totalConversations.get());
        stats.setActiveConversations(activeConversations.get());

        // 工具调用统计
        stats.setToolCallCounts(new ConcurrentHashMap<>());
        toolCallCounts.forEach((k, v) -> stats.getToolCallCounts().put(k, v.get()));

        stats.setToolCallErrors(new ConcurrentHashMap<>());
        toolCallErrors.forEach((k, v) -> stats.getToolCallErrors().put(k, v.get()));

        return stats;
    }

    /**
     * 重置统计
     */
    public void reset() {
        totalRequests.set(0);
        successRequests.set(0);
        failedRequests.set(0);
        totalResponseTime.set(0);
        totalConversations.set(0);
        activeConversations.set(0);
        toolCallCounts.clear();
        toolCallErrors.clear();
        log.info("Performance statistics reset");
    }

    /**
     * 触发告警
     */
    private void triggerAlert(AlertLevel level, String title, String message) {
        log.warn("[ALERT][{}] {} - {}", level, title, message);

        if (alertService != null) {
            alertService.sendAlert(level, title, message);
        }
    }
}