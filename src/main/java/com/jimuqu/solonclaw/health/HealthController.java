package com.jimuqu.solonclaw.health;

import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;

import java.util.Map;

/**
 * 健康检查控制器
 * <p>
 * 提供系统健康检查的 HTTP 接口
 *
 * @author SolonClaw
 */
@Controller
@Mapping("/health")
public class HealthController {

    @Inject
    private HealthCheckService healthCheckService;

    /**
     * 完整健康检查
     * GET /health
     */
    @Get
    @Mapping
    public void health(Context ctx) {
        HealthCheckService.SystemHealth health = healthCheckService.check();

        ctx.output(serializeHealth(health));
        ctx.contentType("application/json");

        // 根据健康状态设置 HTTP 状态码
        int statusCode = switch (health.status()) {
            case UP -> 200;
            case DEGRADED -> 200; // 降级也返回 200，但内容中显示降级
            case DOWN -> 503;
            case UNKNOWN -> 500;
        };
        ctx.status(statusCode);
    }

    /**
     * 快速健康检查
     * GET /health/live
     * 用于 Kubernetes liveness probe
     */
    @Get
    @Mapping("/live")
    public void liveness(Context ctx) {
        boolean isHealthy = healthCheckService.isHealthy();

        ctx.output("{\"status\":\"" + (isHealthy ? "UP" : "DOWN") + "\"}");
        ctx.contentType("application/json");
        ctx.status(isHealthy ? 200 : 503);
    }

    /**
     * 就绪检查
     * GET /health/ready
     * 用于 Kubernetes readiness probe
     */
    @Get
    @Mapping("/ready")
    public void readiness(Context ctx) {
        boolean isHealthy = healthCheckService.isHealthy();

        ctx.output("{\"status\":\"" + (isHealthy ? "READY" : "NOT_READY") + "\"}");
        ctx.contentType("application/json");
        ctx.status(isHealthy ? 200 : 503);
    }

    /**
     * 检查特定组件
     * GET /health/components/{componentName}
     */
    @Get
    @Mapping("/components/{componentName}")
    public void component(Context ctx, String componentName) {
        HealthCheckService.ComponentHealth component = healthCheckService.checkComponent(componentName);

        ctx.output(serializeComponent(component));
        ctx.contentType("application/json");

        int statusCode = switch (component.status()) {
            case UP -> 200;
            case DEGRADED -> 200;
            case DOWN -> 503;
            case UNKNOWN -> 404;
        };
        ctx.status(statusCode);
    }

    /**
     * 获取系统指标
     * GET /health/metrics
     */
    @Get
    @Mapping("/metrics")
    public void metrics(Context ctx) {
        HealthCheckService.SystemHealth health = healthCheckService.check();

        ctx.output(serializeMetrics(health.metrics()));
        ctx.contentType("application/json");
        ctx.status(200);
    }

    /**
     * 健康检查结果（简单格式）
     * GET /health/simple
     */
    @Get
    @Mapping("/simple")
    public void simple(Context ctx) {
        HealthCheckService.SystemHealth health = healthCheckService.check();

        StringBuilder sb = new StringBuilder();
        sb.append("Health Status: ").append(health.status()).append("\n");
        sb.append("Version: ").append(health.version()).append("\n");
        sb.append("Uptime: ").append(formatUptime(health.uptime())).append("\n");
        sb.append("Components:\n");

        for (HealthCheckService.ComponentHealth component : health.components().values()) {
            sb.append("  - ").append(component.name())
              .append(": ").append(component.status())
              .append(" (").append(component.message()).append(")\n");
        }

        ctx.output(sb.toString());
        ctx.contentType("text/plain");
        ctx.status(health.status() == HealthCheckService.HealthStatus.UP ? 200 : 503);
    }

    /**
     * 序列化健康信息
     */
    private String serializeHealth(HealthCheckService.SystemHealth health) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":\"").append(health.status()).append("\",");
        sb.append("\"version\":\"").append(health.version()).append("\",");
        sb.append("\"uptime\":").append(health.uptime()).append(",");
        sb.append("\"uptimeFormatted\":\"").append(formatUptime(health.uptime())).append("\",");

        // 序列化组件信息
        sb.append("\"components\":{");
        boolean first = true;
        for (HealthCheckService.ComponentHealth component : health.components().values()) {
            if (!first) sb.append(",");
            sb.append("\"").append(component.name()).append("\":");
            sb.append(serializeComponent(component));
            first = false;
        }
        sb.append("},");

        // 序列化指标信息
        sb.append("\"metrics\":{");
        first = true;
        for (Map.Entry<String, Object> entry : health.metrics().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    /**
     * 序列化组件信息
     */
    private String serializeComponent(HealthCheckService.ComponentHealth component) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":\"").append(component.status()).append("\",");
        sb.append("\"message\":\"").append(escapeJson(component.message())).append("\",");
        sb.append("\"timestamp\":").append(component.timestamp());
        sb.append("}");
        return sb.toString();
    }

    /**
     * 序列化指标信息
     */
    private String serializeMetrics(java.util.Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (java.util.Map.Entry<String, Object> entry : metrics.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 序列化值
     */
    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "\"" + escapeJson(value.toString()) + "\"";
        }
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * 格式化运行时间
     */
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d days %d hours %d minutes", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d hours %d minutes", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }
}