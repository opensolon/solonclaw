# 健康检查接口使用说明

## 功能概述

健康检查接口提供了系统状态监控功能，可以用于：
- Kubernetes liveness/readiness probe
- 系统监控和告警
- 运维健康检查
- 性能指标收集

## 接口列表

### 1. 完整健康检查
**端点**: `GET /health`
**功能**: 返回完整的系统健康状态
**响应格式**: JSON
**HTTP 状态码**:
- `200` - 系统正常或降级
- `503` - 系统异常
- `500` - 未知状态

**响应示例**:
```json
{
  "status": "UP",
  "version": "1.0.0-SNAPSHOT",
  "uptime": 3600000,
  "uptimeFormatted": "1 hours 0 minutes",
  "components": {
    "database": {
      "status": "UP",
      "message": "数据库连接正常",
      "timestamp": 1709097600000
    },
    "agentService": {
      "status": "UP",
      "message": "Agent 服务正常",
      "timestamp": 1709097600000
    },
    "toolRegistry": {
      "status": "UP",
      "message": "工具注册表正常，已注册 2 个工具",
      "timestamp": 1709097600000
    }
  },
  "metrics": {
    "jvm.heap.used": 104857600,
    "jvm.heap.max": 536870912,
    "jvm.heap.usagePercent": "19.53%",
    "os.arch": "amd64",
    "os.name": "Linux",
    "os.version": "5.15.0",
    "os.availableProcessors": 4
  }
}
```

---

### 2. 存活探针 (Liveness Probe)
**端点**: `GET /health/live`
**功能**: 快速健康检查，用于 Kubernetes liveness probe
**响应格式**: JSON
**HTTP 状态码**:
- `200` - 存活
- `503` - 不存活

**响应示例**:
```json
{
  "status": "UP"
}
```

---

### 3. 就绪探针 (Readiness Probe)
**端点**: `GET /health/ready`
**功能**: 就绪检查，用于 Kubernetes readiness probe
**响应格式**: JSON
**HTTP 状态码**:
- `200` - 就绪
- `503` - 未就绪

**响应示例**:
```json
{
  "status": "READY"
}
```

---

### 4. 组件检查
**端点**: `GET /health/components/{componentName}`
**功能**: 检查特定组件的健康状态
**支持的组件**: `database`, `agentService`, `toolRegistry`
**响应格式**: JSON
**HTTP 状态码**:
- `200` - 组件正常或降级
- `503` - 组件异常
- `404` - 组件不存在

**响应示例**:
```json
{
  "status": "UP",
  "message": "数据库连接正常",
  "timestamp": 1709097600000
}
```

---

### 5. 系统指标
**端点**: `GET /health/metrics`
**功能**: 获取系统性能指标
**响应格式**: JSON

**响应示例**:
```json
{
  "jvm.heap.used": 104857600,
  "jvm.heap.max": 536870912,
  "jvm.heap.usagePercent": "19.53%",
  "os.arch": "amd64",
  "os.name": "Linux",
  "os.version": "5.15.0",
  "os.availableProcessors": 4,
  "os.memory.total": 16739098624,
  "os.memory.free": 536870912,
  "os.memory.used": 16202227712,
  "runtime.uptime": 3600000,
  "runtime.startTime": 1709094000000
}
```

---

### 6. 简单文本格式
**端点**: `GET /health/simple`
**功能**: 返回简单的文本格式健康状态
**响应格式**: 纯文本

**响应示例**:
```
Health Status: UP
Version: 1.0.0-SNAPSHOT
Uptime: 1 hours 0 minutes
Components:
  - database: UP (数据库连接正常)
  - agentService: UP (Agent 服务正常)
  - toolRegistry: UP (工具注册表正常，已注册 2 个工具)
```

---

## 使用场景

### Kubernetes 集成

在 `deployment.yaml` 中配置 liveness 和 readiness probe:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: solonclaw
spec:
  template:
    spec:
      containers:
      - name: solonclaw
        image: solonclaw:latest
        ports:
        - containerPort: 12345
        livenessProbe:
          httpGet:
            path: /health/live
            port: 12345
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 12345
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
```

---

### 监控系统集成

#### Prometheus 示例

```prometheus
# 采集 JVM 堆内存使用率
jvm_heap_usage_percent{service="solonclaw"}

# 采集系统运行时间
runtime_uptime_milliseconds{service="solonclaw"}

# 采集组件健康状态
component_health_status{service="solonclaw",component="database"}
```

---

### 告警规则示例

```yaml
groups:
  - name: solonclaw_alerts
    rules:
      # 服务异常告警
      - alert: SolonClawServiceDown
        expr: component_health_status{service="solonclaw",component="database"} != 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "SolonClaw 服务异常"
          description: "{{ $labels.component }} 组件已经异常超过 1 分钟"

      # JVM 内存使用率告警
      - alert: SolonClawHighMemoryUsage
        expr: jvm_heap_usage_percent{service="solonclaw"} > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "SolonClaw 内存使用率过高"
          description: "JVM 堆内存使用率为 {{ $value }}%"
```

---

## 测试示例

### 使用 curl 测试

```bash
# 完整健康检查
curl http://localhost:12345/health

# 存活探针
curl http://localhost:12345/health/live

# 就绪探针
curl http://localhost:12345/health/ready

# 检查数据库组件
curl http://localhost:12345/health/components/database

# 获取系统指标
curl http://localhost:12345/health/metrics

# 简单文本格式
curl http://localhost:12345/health/simple
```

---

## 健康状态说明

| 状态 | 描述 | HTTP 状态码 |
|------|------|------------|
| UP | 系统正常运行 | 200 |
| DOWN | 系统异常，关键组件不可用 | 503 |
| DEGRADED | 系统降级运行，非关键组件异常 | 200 |
| UNKNOWN | 状态未知 | 500 |

---

## 扩展建议

### 添加自定义组件检查

在 `HealthCheckService` 中添加新的检查方法：

```java
private ComponentHealth checkCustomComponent() {
    // 实现自定义检查逻辑
    try {
        // 检查逻辑
        return new ComponentHealth("custom", HealthStatus.UP, "正常");
    } catch (Exception e) {
        return new ComponentHealth("custom", HealthStatus.DOWN, "异常: " + e.getMessage());
    }
}
```

### 添加自定义指标

在 `collectSystemMetrics()` 方法中添加：

```java
metrics.put("custom.metric.name", metricValue);
```

---

## 故障排查

### 健康检查失败

1. **检查数据库连接**
   ```bash
   curl http://localhost:12345/health/components/database
   ```

2. **查看详细错误信息**
   ```bash
   curl http://localhost:12345/health
   ```

3. **查看系统指标**
   ```bash
   curl http://localhost:12345/health/metrics
   ```

### 组件状态 DOWN

1. 检查相关组件日志
2. 验证配置文件是否正确
3. 确认外部依赖是否可用

---

## 更新日志

### v1.0.0 (2025-02-28)
- ✅ 实现基础健康检查接口
- ✅ 支持 Kubernetes liveness/readiness probe
- ✅ 提供系统性能指标
- ✅ 支持组件级健康检查
- ✅ 完整的单元测试覆盖 (60个测试用例)