package com.jimuqu.solonclaw.health;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 健康检查服务
 * <p>
 * 检查系统各组件的健康状态
 *
 * @author SolonClaw
 */
@Component
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    @Inject
    private DataSource dataSource;

    @Inject(required = false)
    private com.jimuqu.solonclaw.agent.AgentService agentService;

    @Inject(required = false)
    private com.jimuqu.solonclaw.tool.ToolRegistry toolRegistry;

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        UP("系统正常"),
        DOWN("系统异常"),
        DEGRADED("系统降级"),
        UNKNOWN("未知状态");

        private final String description;

        HealthStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 组件健康信息
     */
    public static class ComponentHealth {
        private final String name;
        private final HealthStatus status;
        private final String message;
        private final long timestamp;

        public ComponentHealth(String name, HealthStatus status, String message) {
            this.name = name;
            this.status = status;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public String name() {
            return name;
        }

        public HealthStatus status() {
            return status;
        }

        public String message() {
            return message;
        }

        public long timestamp() {
            return timestamp;
        }
    }

    /**
     * 系统健康信息
     */
    public static class SystemHealth {
        private final HealthStatus status;
        private final String version;
        private final long uptime;
        private final Map<String, ComponentHealth> components;
        private final Map<String, Object> metrics;

        public SystemHealth(HealthStatus status, String version, long uptime,
                           Map<String, ComponentHealth> components, Map<String, Object> metrics) {
            this.status = status;
            this.version = version;
            this.uptime = uptime;
            this.components = components;
            this.metrics = metrics;
        }

        public HealthStatus status() {
            return status;
        }

        public String version() {
            return version;
        }

        public long uptime() {
            return uptime;
        }

        public Map<String, ComponentHealth> components() {
            return components;
        }

        public Map<String, Object> metrics() {
            return metrics;
        }
    }

    /**
     * 执行完整的健康检查
     */
    public SystemHealth check() {
        Map<String, ComponentHealth> components = new ConcurrentHashMap<>();
        Map<String, Object> metrics = new HashMap<>();

        // 检查数据库
        components.put("database", checkDatabase());

        // 检查 Agent 服务
        components.put("agentService", checkAgentService());

        // 检查工具注册表
        components.put("toolRegistry", checkToolRegistry());

        // 收集系统指标
        metrics.putAll(collectSystemMetrics());

        // 确定整体健康状态
        HealthStatus overallStatus = determineOverallStatus(components);

        // 获取运行时间
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        // 获取版本号
        String version = getVersion();

        return new SystemHealth(overallStatus, version, uptime, components, metrics);
    }

    /**
     * 检查数据库连接
     */
    private ComponentHealth checkDatabase() {
        if (ObjUtil.isNull(dataSource)) {
            return new ComponentHealth("database", HealthStatus.DOWN, "数据源未配置");
        }

        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                return new ComponentHealth("database", HealthStatus.UP, "数据库连接正常");
            } else {
                return new ComponentHealth("database", HealthStatus.DOWN, "数据库连接无效");
            }
        } catch (Exception e) {
            log.warn("数据库健康检查失败", e);
            return new ComponentHealth("database", HealthStatus.DOWN, "数据库连接失败: " + e.getMessage());
        }
    }

    /**
     * 检查 Agent 服务
     */
    private ComponentHealth checkAgentService() {
        if (ObjUtil.isNull(agentService)) {
            return new ComponentHealth("agentService", HealthStatus.DOWN, "Agent 服务未初始化");
        }

        try {
            // 检查服务是否可用
            agentService.getAvailableTools();
            return new ComponentHealth("agentService", HealthStatus.UP, "Agent 服务正常");
        } catch (Exception e) {
            log.warn("Agent 服务健康检查失败", e);
            return new ComponentHealth("agentService", HealthStatus.DOWN, "Agent 服务异常: " + e.getMessage());
        }
    }

    /**
     * 检查工具注册表
     */
    private ComponentHealth checkToolRegistry() {
        if (ObjUtil.isNull(toolRegistry)) {
            return new ComponentHealth("toolRegistry", HealthStatus.DOWN, "工具注册表未初始化");
        }

        try {
            int toolCount = toolRegistry.getTools().size();
            return new ComponentHealth("toolRegistry", HealthStatus.UP,
                "工具注册表正常，已注册 " + toolCount + " 个工具");
        } catch (Exception e) {
            log.warn("工具注册表健康检查失败", e);
            return new ComponentHealth("toolRegistry", HealthStatus.DOWN,
                "工具注册表异常: " + e.getMessage());
        }
    }

    /**
     * 确定整体健康状态
     */
    private HealthStatus determineOverallStatus(Map<String, ComponentHealth> components) {
        boolean hasDown = false;
        boolean hasDegraded = false;

        for (ComponentHealth component : components.values()) {
            if (component.status() == HealthStatus.DOWN) {
                hasDown = true;
            } else if (component.status() == HealthStatus.DEGRADED) {
                hasDegraded = true;
            }
        }

        if (hasDown) {
            return HealthStatus.DOWN;
        } else if (hasDegraded) {
            return HealthStatus.DEGRADED;
        } else {
            return HealthStatus.UP;
        }
    }

    /**
     * 收集系统指标
     */
    private Map<String, Object> collectSystemMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // JVM 内存信息
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapMemoryUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMemoryMax = memoryBean.getHeapMemoryUsage().getMax();
            double heapUsagePercent = (double) heapMemoryUsed / heapMemoryMax * 100;

            metrics.put("jvm.heap.used", heapMemoryUsed);
            metrics.put("jvm.heap.max", heapMemoryMax);
            metrics.put("jvm.heap.usagePercent", String.format("%.2f%%", heapUsagePercent));

            // 操作系统信息
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            metrics.put("os.arch", osBean.getArch());
            metrics.put("os.name", osBean.getName());
            metrics.put("os.version", osBean.getVersion());
            metrics.put("os.availableProcessors", osBean.getAvailableProcessors());

            // 系统负载
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                double systemLoadAverage = sunOsBean.getSystemLoadAverage();
                metrics.put("os.systemLoadAverage", systemLoadAverage);

                long totalMemory = sunOsBean.getTotalMemorySize();
                long freeMemory = sunOsBean.getFreeMemorySize();
                metrics.put("os.memory.total", totalMemory);
                metrics.put("os.memory.free", freeMemory);
                metrics.put("os.memory.used", totalMemory - freeMemory);
            }

            // 运行时信息
            metrics.put("runtime.uptime", ManagementFactory.getRuntimeMXBean().getUptime());
            metrics.put("runtime.startTime", ManagementFactory.getRuntimeMXBean().getStartTime());

        } catch (Exception e) {
            log.error("收集系统指标失败", e);
            metrics.put("error", "无法收集系统指标: " + e.getMessage());
        }

        return metrics;
    }

    /**
     * 获取应用版本号
     */
    private String getVersion() {
        try {
            Package pkg = HealthCheckService.class.getPackage();
            String version = pkg.getImplementationVersion();
            if (StrUtil.isBlank(version)) {
                return "1.0.0-SNAPSHOT";
            }
            return version;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 快速健康检查（只检查关键组件）
     */
    public boolean isHealthy() {
        ComponentHealth dbHealth = checkDatabase();
        return dbHealth.status() == HealthStatus.UP;
    }

    /**
     * 获取特定组件的健康状态
     */
    public ComponentHealth checkComponent(String componentName) {
        return switch (componentName) {
            case "database" -> checkDatabase();
            case "agentService" -> checkAgentService();
            case "toolRegistry" -> checkToolRegistry();
            default -> new ComponentHealth(componentName, HealthStatus.UNKNOWN, "未知组件");
        };
    }
}