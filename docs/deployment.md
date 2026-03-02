# SolonClaw 部署文档

## 目录

1. [环境要求](#环境要求)
2. [快速部署](#快速部署)
3. [Docker 部署](#docker-部署)
4. [配置说明](#配置说明)
5. [生产环境部署](#生产环境部署)
6. [Kubernetes 部署](#kubernetes-部署)
7. [监控配置](#监控配置)
8. [故障排查](#故障排查)

---

## 环境要求

### 系统要求

| 项目 | 最低要求 | 推荐配置 |
|------|---------|---------|
| 操作系统 | Linux / macOS / Windows | Linux (Ubuntu 20.04+) |
| CPU | 1 核 | 2 核+ |
| 内存 | 512MB | 2GB+ |
| 磁盘 | 100MB | 1GB+ |

### 软件要求

- **Java**: OpenJDK 17+ 或 Oracle JDK 17+
- **Maven**: 3.8+ (仅构建时需要)
- **Docker**: 20.10+ (Docker 部署时需要)
- **Kubernetes**: 1.20+ (K8s 部署时需要)

---

## 快速部署

### 1. 克隆项目

```bash
git clone https://github.com/your-org/solonclaw.git
cd solonclaw
```

### 2. 构建项目

```bash
mvn clean package -DskipTests
```

### 3. 配置环境变量

```bash
# 必需：设置 OpenAI API 密钥
export OPENAI_API_KEY=your-api-key-here

# 可选：回调配置
export CALLBACK_URL=https://your-callback-url.com/webhook
export CALLBACK_SECRET=your-callback-secret
```

### 4. 运行服务

```bash
java -jar target/solonclaw-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### 5. 验证服务

```bash
# 健康检查
curl http://localhost:41234/health

# 简单检查
curl http://localhost:41234/health/simple
```

---

## Docker 部署

### 构建 Docker 镜像

```bash
# 使用提供的 Dockerfile
docker build -t solonclaw:latest .
```

### 使用 Docker Compose

创建 `docker-compose.yml` 文件：

```yaml
version: '3.8'

services:
  solonclaw:
    image: solonclaw:latest
    container_name: solonclaw
    ports:
      - "41234:41234"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - CALLBACK_URL=${CALLBACK_URL:-}
      - CALLBACK_SECRET=${CALLBACK_SECRET:-}
      - SOLON_ENV=prod
    volumes:
      - ./workspace:/app/workspace
      - ./logs:/app/logs
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:41234/health/live"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
```

### 启动服务

```bash
# 设置环境变量
export OPENAI_API_KEY=your-api-key

# 启动
docker-compose up -d

# 查看日志
docker-compose logs -f
```

### Docker 常用命令

```bash
# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看状态
docker-compose ps

# 进入容器
docker exec -it solonclaw /bin/sh
```

---

## 配置说明

### 主配置文件 (app.yml)

```yaml
solon:
  app:
    name: solonclaw
  port: 41234
  env: prod

  # AI 配置
  ai:
    chat:
      openai:
        apiUrl: "https://api.openai.com/v1/chat/completions"
        apiKey: "${OPENAI_API_KEY}"
        provider: "openai"
        model: "gpt-4"
        temperature: 0.7
        maxTokens: 4096

# SolonClaw 配置
nullclaw:
  workspace: "./workspace"

  directories:
    mcpConfig: "mcp.json"
    skillsDir: "skills"
    jobsFile: "jobs.json"
    jobHistoryFile: "job-history.json"
    database: "memory.db"
    shellWorkspace: "workspace"
    logsDir: "logs"

  defaults:
    temperature: 0.7
    maxTokens: 4096
    timeoutSeconds: 120

  agent:
    model:
      primary: "openai/gpt-4"
    maxHistoryMessages: 50
    maxToolIterations: 25

  tools:
    shell:
      enabled: true
      timeoutSeconds: 60
      maxOutputBytes: 1048576

  memory:
    enabled: true
    session:
      maxHistory: 50
    store:
      enabled: true

  callback:
    enabled: true
    url: "${CALLBACK_URL:}"
    secret: "${CALLBACK_SECRET:}"
```

### 环境变量

| 变量名 | 必需 | 默认值 | 说明 |
|--------|------|--------|------|
| `OPENAI_API_KEY` | 是 | - | OpenAI API 密钥 |
| `CALLBACK_URL` | 否 | - | 回调通知 URL |
| `CALLBACK_SECRET` | 否 | - | 回调签名密钥 |
| `SOLON_ENV` | 否 | dev | 运行环境 (dev/prod) |

### 工作目录结构

```
workspace/
├── mcp.json              # MCP 服务器配置
├── jobs.json             # 定时任务配置
├── job-history.json      # 任务执行历史
├── memory.db             # H2 会话记忆数据库
├── workspace/            # Shell 工具的工作目录
├── skills/               # 用户自定义技能
└── logs/                 # 日志文件
    ├── solonclaw.log         # 应用日志
    ├── solonclaw-error.log   # 错误日志
    └── archive/              # 归档日志
```

---

## 生产环境部署

### JVM 参数优化

```bash
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/log/solonclaw/heapdump.hprof \
     -Dfile.encoding=UTF-8 \
     -Duser.timezone=Asia/Shanghai \
     -jar solonclaw-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### Systemd 服务配置

创建 `/etc/systemd/system/solonclaw.service`：

```ini
[Unit]
Description=SolonClaw AI Agent Service
After=network.target

[Service]
Type=simple
User=solonclaw
Group=solonclaw
WorkingDirectory=/opt/solonclaw
Environment="OPENAI_API_KEY=your-api-key"
Environment="SOLON_ENV=prod"
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar /opt/solonclaw/solonclaw.jar
ExecStop=/bin/kill -TERM $MAINPID
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### 启动服务

```bash
# 重载 systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start solonclaw

# 开机自启
sudo systemctl enable solonclaw

# 查看状态
sudo systemctl status solonclaw

# 查看日志
journalctl -u solonclaw -f
```

---

## Kubernetes 部署

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: solonclaw-config
data:
  SOLON_ENV: "prod"
---
apiVersion: v1
kind: Secret
metadata:
  name: solonclaw-secret
type: Opaque
stringData:
  OPENAI_API_KEY: "your-api-key"
  CALLBACK_SECRET: "your-secret"
```

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: solonclaw
  labels:
    app: solonclaw
spec:
  replicas: 2
  selector:
    matchLabels:
      app: solonclaw
  template:
    metadata:
      labels:
        app: solonclaw
    spec:
      containers:
      - name: solonclaw
        image: solonclaw:latest
        ports:
        - containerPort: 41234
        envFrom:
        - configMapRef:
            name: solonclaw-config
        - secretRef:
            name: solonclaw-secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /health/live
            port: 41234
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 41234
          initialDelaySeconds: 10
          periodSeconds: 5
        volumeMounts:
        - name: workspace
          mountPath: /app/workspace
      volumes:
      - name: workspace
        persistentVolumeClaim:
          claimName: solonclaw-pvc
```

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: solonclaw
spec:
  selector:
    app: solonclaw
  ports:
  - port: 80
    targetPort: 41234
  type: ClusterIP
```

### Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: solonclaw-ingress
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
spec:
  rules:
  - host: solonclaw.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: solonclaw
            port:
              number: 80
```

---

## 监控配置

### Prometheus 集成

添加 Prometheus 抓取配置：

```yaml
scrape_configs:
  - job_name: 'solonclaw'
    metrics_path: '/api/monitor/metrics'
    static_configs:
      - targets: ['solonclaw:41234']
```

### Prometheus 告警规则

```yaml
groups:
  - name: solonclaw-alerts
    rules:
      - alert: HighMemoryUsage
        expr: jvm_heap_used_bytes / jvm_heap_max_bytes > 0.9
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "SolonClaw 内存使用率过高"
          description: "内存使用率超过 90%"

      - alert: HighErrorRate
        expr: rate(requests_failed_total[5m]) / rate(requests_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "SolonClaw 错误率过高"
          description: "请求失败率超过 10%"

      - alert: SlowResponse
        expr: response_time_avg_ms > 5000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "SolonClaw 响应时间过长"
          description: "平均响应时间超过 5 秒"
```

### 监控端点

| 端点 | 说明 |
|------|------|
| `/health` | 完整健康检查 |
| `/health/live` | 存活探针 |
| `/health/ready` | 就绪探针 |
| `/health/metrics` | 系统指标 |
| `/api/monitor/metrics` | Prometheus 格式指标 |
| `/api/monitor/dashboard` | 监控总览 |
| `/api/monitor/performance` | 性能统计 |
| `/api/monitor/resources` | 系统资源 |
| `/api/monitor/alerts` | 告警列表 |

---

## 故障排查

### 常见问题

#### 1. 服务无法启动

**症状**: 服务启动失败或立即退出

**排查步骤**:
```bash
# 检查日志
tail -f logs/solonclaw.log

# 检查端口占用
netstat -tlnp | grep 41234

# 检查 Java 版本
java -version
```

**解决方案**:
- 确认 Java 版本 >= 17
- 检查端口 41234 是否被占用
- 检查工作目录权限

#### 2. API 调用失败

**症状**: 返回 500 错误或超时

**排查步骤**:
```bash
# 检查健康状态
curl http://localhost:41234/health

# 检查组件状态
curl http://localhost:41234/health/components/database
curl http://localhost:41234/health/components/agentService

# 查看错误日志
tail -f logs/solonclaw-error.log
```

**解决方案**:
- 检查 OPENAI_API_KEY 是否正确设置
- 检查网络连接和代理配置
- 查看 API 服务的错误响应

#### 3. 内存不足

**症状**: 服务响应缓慢或崩溃

**排查步骤**:
```bash
# 查看内存使用
curl http://localhost:41234/api/monitor/resources | jq '.["heap.used"], .["heap.max"]'

# 查看 JVM 内存
jstat -gcutil <pid> 1000 10
```

**解决方案**:
- 增加 JVM 堆内存: `-Xmx2g`
- 检查是否有内存泄漏
- 定期重启服务

#### 4. 数据库连接失败

**症状**: 数据库相关操作失败

**排查步骤**:
```bash
# 检查数据库文件
ls -la workspace/memory.db

# 检查文件权限
chmod 644 workspace/memory.db
```

**解决方案**:
- 确认工作目录存在且有写权限
- 删除损坏的数据库文件重新创建

### 日志分析

```bash
# 查看最近错误
grep ERROR logs/solonclaw.log | tail -20

# 按时间范围查询
grep "2024-01-15 10:" logs/solonclaw.log

# 统计错误类型
grep ERROR logs/solonclaw.log | awk -F']' '{print $3}' | sort | uniq -c
```

### 性能诊断

```bash
# 线程转储
jstack <pid> > thread_dump.txt

# 堆转储
jmap -dump:format=b,file=heap.hprof <pid>

# GC 日志分析
java -Xlog:gc*:file=gc.log:time,uptime,level,tags ...
```

---

## 联系支持

如有问题，请联系技术支持或提交 Issue：

- GitHub Issues: https://github.com/your-org/solonclaw/issues
- 文档: https://docs.solonclaw.dev