package com.jimuqu.solonclaw.monitor;

import com.jimuqu.solonclaw.common.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控控制器
 * 提供性能监控和告警相关的 HTTP 接口
 */
@Api(tags = "监控管理")
@Controller
@Mapping("/api/monitor")
public class MonitorController {

    @Inject
    private PerformanceMonitor performanceMonitor;

    @Inject
    private AlertService alertService;

    @Inject
    private SystemResourceMonitor systemResourceMonitor;

    /**
     * 获取性能统计
     */
    @ApiOperation(value = "获取性能统计", notes = "获取系统性能统计，包括请求数、响应时间、成功率等")
    @Get
    @Mapping("/performance")
    public Result getPerformanceStats() {
        try {
            PerformanceStats stats = performanceMonitor.getStats();
            return Result.success("获取性能统计成功", stats);
        } catch (Exception e) {
            return Result.error("获取性能统计失败: " + e.getMessage());
        }
    }

    /**
     * 重置性能统计
     */
    @ApiOperation(value = "重置性能统计", notes = "重置所有性能统计数据")
    @Post
    @Mapping("/performance/reset")
    public Result resetPerformanceStats() {
        try {
            performanceMonitor.reset();
            return Result.success("重置性能统计成功");
        } catch (Exception e) {
            return Result.error("重置性能统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统资源
     */
    @ApiOperation(value = "获取系统资源", notes = "获取当前系统资源使用情况，包括内存、CPU、线程等")
    @Get
    @Mapping("/resources")
    public Result getSystemResources() {
        try {
            Map<String, Object> resources = systemResourceMonitor.getCurrentResources();
            return Result.success("获取系统资源成功", resources);
        } catch (Exception e) {
            return Result.error("获取系统资源失败: " + e.getMessage());
        }
    }

    /**
     * 获取资源历史
     */
    @ApiOperation(value = "获取资源历史", notes = "获取系统资源使用历史记录")
    @Get
    @Mapping("/resources/history")
    public Result getResourceHistory(
            @ApiParam(value = "返回记录数") @Param(required = false, defaultValue = "10") Integer limit) {
        try {
            List<SystemResourceMonitor.ResourceSnapshot> history = systemResourceMonitor.getResourceHistory();

            // 限制返回数量
            if (limit != null && limit > 0 && history.size() > limit) {
                history = history.subList(history.size() - limit, history.size());
            }

            return Result.success("获取资源历史成功", history);
        } catch (Exception e) {
            return Result.error("获取资源历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取告警列表
     */
    @ApiOperation(value = "获取告警列表", notes = "获取系统告警历史记录")
    @Get
    @Mapping("/alerts")
    public Result getAlerts(
            @ApiParam(value = "页码") @Param(required = false, defaultValue = "1") Integer page,
            @ApiParam(value = "每页数量") @Param(required = false, defaultValue = "20") Integer pageSize) {
        try {
            List<AlertService.AlertRecord> alerts = alertService.getAlertHistory(page, pageSize);
            return Result.success("获取告警列表成功", alerts);
        } catch (Exception e) {
            return Result.error("获取告警列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取告警统计
     */
    @ApiOperation(value = "获取告警统计", notes = "获取各级别告警数量统计")
    @Get
    @Mapping("/alerts/stats")
    public Result getAlertStats() {
        try {
            Map<AlertLevel, Long> counts = alertService.getAlertCounts();
            return Result.success("获取告警统计成功", counts);
        } catch (Exception e) {
            return Result.error("获取告警统计失败: " + e.getMessage());
        }
    }

    /**
     * 清除告警历史
     */
    @ApiOperation(value = "清除告警历史", notes = "清除所有告警历史记录")
    @Delete
    @Mapping("/alerts")
    public Result clearAlerts() {
        try {
            alertService.clearHistory();
            return Result.success("清除告警历史成功");
        } catch (Exception e) {
            return Result.error("清除告警历史失败: " + e.getMessage());
        }
    }

    /**
     * 发送测试告警
     */
    @ApiOperation(value = "发送测试告警", notes = "发送一条测试告警，用于验证告警功能")
    @Post
    @Mapping("/alerts/test")
    public Result sendTestAlert(
            @ApiParam(value = "告警级别") @Param(required = false, defaultValue = "INFO") String level) {
        try {
            AlertLevel alertLevel = AlertLevel.valueOf(level.toUpperCase());
            alertService.sendAlert(alertLevel, "测试告警", "这是一条测试告警消息，时间: " + System.currentTimeMillis());
            return Result.success("发送测试告警成功");
        } catch (Exception e) {
            return Result.error("发送测试告警失败: " + e.getMessage());
        }
    }

    /**
     * 获取监控总览
     */
    @ApiOperation(value = "获取监控总览", notes = "获取系统监控总览，包含性能、资源和告警摘要")
    @Get
    @Mapping("/dashboard")
    public Result getDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();

            // 性能统计
            dashboard.put("performance", performanceMonitor.getStats());

            // 系统资源
            dashboard.put("resources", systemResourceMonitor.getCurrentResources());

            // 告警统计
            dashboard.put("alertStats", alertService.getAlertCounts());

            // 最近告警（最近 5 条）
            List<AlertService.AlertRecord> recentAlerts = alertService.getAlertHistory();
            if (recentAlerts.size() > 5) {
                recentAlerts = recentAlerts.subList(recentAlerts.size() - 5, recentAlerts.size());
            }
            dashboard.put("recentAlerts", recentAlerts);

            return Result.success("获取监控总览成功", dashboard);
        } catch (Exception e) {
            return Result.error("获取监控总览失败: " + e.getMessage());
        }
    }

    /**
     * Prometheus 格式指标导出
     */
    @ApiOperation(value = "Prometheus 指标", notes = "导出 Prometheus 格式的监控指标")
    @Get
    @Mapping("/metrics")
    public void metrics(org.noear.solon.core.handle.Context ctx) {
        try {
            StringBuilder sb = new StringBuilder();

            // 性能指标
            PerformanceStats stats = performanceMonitor.getStats();
            sb.append("# HELP requests_total Total number of requests\n");
            sb.append("# TYPE requests_total counter\n");
            sb.append("requests_total ").append(stats.getTotalRequests()).append("\n\n");

            sb.append("# HELP requests_success_total Total successful requests\n");
            sb.append("# TYPE requests_success_total counter\n");
            sb.append("requests_success_total ").append(stats.getSuccessRequests()).append("\n\n");

            sb.append("# HELP requests_failed_total Total failed requests\n");
            sb.append("# TYPE requests_failed_total counter\n");
            sb.append("requests_failed_total ").append(stats.getFailedRequests()).append("\n\n");

            sb.append("# HELP response_time_avg_ms Average response time in milliseconds\n");
            sb.append("# TYPE response_time_avg_ms gauge\n");
            sb.append("response_time_avg_ms ").append(stats.getAverageResponseTime()).append("\n\n");

            sb.append("# HELP success_rate Success rate percentage\n");
            sb.append("# TYPE success_rate gauge\n");
            sb.append("success_rate ").append(String.format("%.2f", stats.getSuccessRate())).append("\n\n");

            sb.append("# HELP conversations_active Active conversations\n");
            sb.append("# TYPE conversations_active gauge\n");
            sb.append("conversations_active ").append(stats.getActiveConversations()).append("\n\n");

            sb.append("# HELP conversations_total Total conversations\n");
            sb.append("# TYPE conversations_total counter\n");
            sb.append("conversations_total ").append(stats.getTotalConversations()).append("\n\n");

            // 系统资源指标
            Map<String, Object> resources = systemResourceMonitor.getCurrentResources();

            sb.append("# HELP jvm_heap_used_bytes JVM heap memory used in bytes\n");
            sb.append("# TYPE jvm_heap_used_bytes gauge\n");
            sb.append("jvm_heap_used_bytes ").append(resources.get("heap.used")).append("\n\n");

            sb.append("# HELP jvm_heap_max_bytes JVM heap memory max in bytes\n");
            sb.append("# TYPE jvm_heap_max_bytes gauge\n");
            sb.append("jvm_heap_max_bytes ").append(resources.get("heap.max")).append("\n\n");

            sb.append("# HELP jvm_threads_current Current number of JVM threads\n");
            sb.append("# TYPE jvm_threads_current gauge\n");
            sb.append("jvm_threads_current ").append(resources.get("thread.count")).append("\n\n");

            sb.append("# HELP os_cpu_load_percent OS CPU load percentage\n");
            sb.append("# TYPE os_cpu_load_percent gauge\n");
            Object cpuLoad = resources.get("os.cpuLoad");
            if (cpuLoad instanceof String) {
                sb.append("os_cpu_load_percent ").append(((String) cpuLoad).replace("%", "")).append("\n\n");
            } else {
                sb.append("os_cpu_load_percent 0\n\n");
            }

            sb.append("# HELP os_memory_used_bytes OS memory used in bytes\n");
            sb.append("# TYPE os_memory_used_bytes gauge\n");
            sb.append("os_memory_used_bytes ").append(resources.get("os.memory.used")).append("\n\n");

            // 告警统计
            Map<AlertLevel, Long> alertCounts = alertService.getAlertCounts();
            sb.append("# HELP alerts_total Total alerts by level\n");
            sb.append("# TYPE alerts_total counter\n");
            for (Map.Entry<AlertLevel, Long> entry : alertCounts.entrySet()) {
                sb.append("alerts_total{level=\"").append(entry.getKey().name()).append("\"} ")
                        .append(entry.getValue()).append("\n");
            }

            ctx.contentType("text/plain; version=0.0.4");
            ctx.output(sb.toString());
        } catch (Exception e) {
            ctx.status(500);
            ctx.output("Error generating metrics: " + e.getMessage());
        }
    }
}