# SolonClaw 技术文档

## 1. 技术栈

### 1.1 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| Solon | 3.9.4 | 轻量级 Java 框架 |
| JDK | 17 | Java 运行环境 |
| solon-ai-core | 3.9.4 | AI Agent 框架 |
| solon-scheduling-simple | 3.9.4 | 动态调度支持 |

### 1.2 依赖库

| 依赖 | 版本 | 用途 |
|------|------|------|
| okhttp | 4.12.0 | HTTP 客户端 |
| solon-serialization-snack4 | 3.9.4 | JSON 序列化 |
| H2 | 2.3.232 | 嵌入式数据库 |
| HikariCP | 5.1.0 | 连接池 |
| logback | 1.5.12 | 日志框架 |

## 2. 项目结构

```
SolonClaw/
├── pom.xml                          # Maven 配置
├── src/main/java/com/jimuqu/solonclaw/
│   ├── SolonClawApp.java            # 主入口
│   ├── gateway/                     # HTTP 接口层
│   │   └── GatewayController.java
│   ├── agent/                       # Agent 服务
│   │   ├── AgentService.java
│   │   └── AgentConfig.java
│   ├── tool/                        # 工具系统
│   │   ├── ToolRegistry.java
│   │   └── impl/
│   │       └── ShellTool.java
│   ├── scheduler/                   # 动态调度
│   │   ├── SchedulerService.java
│   │   └── JobManager.java
│   ├── mcp/                         # MCP 管理
│   │   ├── McpManager.java
│   │   └── McpConfig.java
│   ├── skill/                       # Skill 管理
│   │   ├── SkillManager.java
│   │   └── SkillLoader.java
│   ├── memory/                      # 记忆存储
│   │   ├── MemoryService.java
│   │   └── SessionStore.java
│   └── config/                      # 配置类
│       └── WorkspaceConfig.java
├── src/main/resources/
│   ├── app.yml                      # 主配置
│   ├── app-dev.yml                  # 开发环境
│   ├── app-prod.yml                 # 生产环境
│   └── logback.xml                  # 日志配置
└── workspace/                       # 工作目录（运行时生成）
    ├── mcp.json
    ├── jobs.json
    ├── job-history.json
    ├── memory.db
    ├── workspace/
    ├── skills/
    └── logs/
```

## 3. 核心模块设计

### 3.1 Gateway（网关层）

**职责**: 提供 HTTP 接口供外部调用

**类**: `GatewayController`

**接口**:
| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 健康检查 | GET | /api/health | 检查服务状态 |
| 对话 | POST | /api/chat | 与 AI 对话 |
| 会话历史 | GET | /api/sessions/{id} | 获取会话历史 |

**实现要点**:
- 使用 `@Controller` 和 `@Mapping` 注解
- 请求/响应使用 Snack4 自动序列化
- 统一返回格式 `Result(code, message, data)`

### 3.2 Agent（AI 服务层）

**职责**: 封装 AI 对话和工具调用逻辑

**类**: `AgentService`

**核心方法**:
```java
public String chat(String message, String sessionId);
public String chatWithTools(String message, String sessionId, List<Object> tools);
```

**实现方案**:
- 使用 Solon AI 的 `ReActAgent`
- 自动发现和管理工具
- 会话上下文管理

### 3.3 Tool（工具系统）

**职责**: 暴露 Java 方法为 AI 可调用的工具

**接口**: `@ToolMapping`

**工具定义示例**:
```java
@Component
public class ShellTool {
    @ToolMapping(description = "执行 Shell 命令")
    public String exec(
        @Param(description = "要执行的命令") String command
    ) {
        // 实现...
    }
}
```

**工具注册**:
- 扫描 `@Component` + `@ToolMapping` 注解的类
- 自动注册到 Agent

### 3.4 Scheduler（调度系统）

**职责**: 管理动态定时任务

**类**: `SchedulerService`

**核心功能**:
| 功能 | 说明 |
|------|------|
| 添加任务 | 通过 IJobManager 动态注册 |
| 删除任务 | 取消已注册的任务 |
| 任务持久化 | 保存到 jobs.json |
| 执行历史 | 记录到 job-history.json |

**实现要点**:
```java
@Component
public class SchedulerService {

    @Inject
    private IJobManager jobManager;

    public void addJob(String name, String cron, Runnable action) {
        jobManager.register(name, cron, action);
    }
}
```

### 3.5 Memory（记忆系统）

**职责**: 存储和检索会话历史

**类**: `MemoryService`, `SessionStore`

**数据结构**:
```sql
CREATE TABLE sessions (
    id VARCHAR PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR,
    role VARCHAR,
    content TEXT,
    timestamp TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);
```

**搜索实现**:
- H2 全文搜索（FTS）
- 简单的关键词匹配
- 按时间排序

### 3.6 MCP（MCP 管理）

**职责**: 管理 Model Context Protocol 服务器

**类**: `McpManager`

**配置格式**:
```json
{
  "servers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/files"]
    }
  }
}
```

**启动流程**:
1. 读取 `workspace/mcp.json`
2. 为每个服务器启动子进程
3. 通过 stdio 进行 JSON-RPC 通信
4. 发现并注册工具

### 3.7 Skill（技能管理）

**职责**: 管理用户自定义技能

**类**: `SkillManager`

**技能目录结构**:
```
workspace/skills/
├── my-skill/
│   ├── skill.json       # 清单文件
│   ├── SKILL.md         # 说明文档
│   └── impl/            # 实现代码
```

**skill.json 格式**:
```json
{
  "name": "my-skill",
  "version": "1.0.0",
  "description": "技能描述",
  "author": "作者",
  "tools": ["impl.MyTool"]
}
```

**安装方式**:
- 从 GitHub 克隆
- 复制到 skills 目录
- 重新加载技能

## 4. 配置说明

### 4.1 主配置文件（app.yml）

```yaml
solon:
  app:
    name: solonclaw
  port: 41234
  env: dev

  # AI 配置
  ai:
    chat:
      openai:
        apiUrl: "https://api.openai.com/v1/chat/completions"
        apiKey: "${OPENAI_API_KEY:}"
        provider: "openai"
        model: "gpt-4"
        temperature: 0.7
        maxTokens: 4096

  # 序列化配置
  serialization:
    json:
      dateAsFormat: 'yyyy-MM-dd HH:mm:ss'
      dateAsTimeZone: 'GMT+8'
      nullAsWriteable: true

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

### 4.2 环境变量

| 变量 | 说明 | 必需 |
|------|------|------|
| OPENAI_API_KEY | OpenAI API 密钥 | 是 |
| CALLBACK_URL | 回调通知 URL | 否 |
| CALLBACK_SECRET | 回调签名密钥 | 否 |

## 5. API 设计

### 5.1 统一响应格式

```json
{
  "code": 200,
  "message": "成功",
  "data": {}
}
```

### 5.2 对话接口

**请求**
```http
POST /api/chat
Content-Type: application/json

{
  "message": "用户消息",
  "sessionId": "会话ID（可选）"
}
```

**响应**
```json
{
  "code": 200,
  "message": "对话成功",
  "data": {
    "sessionId": "sess-1234567890",
    "response": "AI 回复内容"
  }
}
```

### 5.3 健康检查接口

**请求**
```http
GET /api/health
```

**响应**
```json
{
  "code": 200,
  "message": "SolonClaw is running",
  "data": {
    "status": "ok",
    "timestamp": 1706745600000
  }
}
```

## 6. 关键技术点

### 6.1 ReActAgent 集成

Solon AI 提供的 ReActAgent 支持自动推理和工具调用：

```java
@Component
public class AgentService {

    @Inject
    private ReActAgent reactAgent;

    public String chat(String message, String sessionId) {
        return reactAgent.chat(message)
            .tools(toolRegistry.getTools())
            .sessionId(sessionId)
            .execute();
    }
}
```

### 6.2 工具自动发现

通过扫描注解自动注册工具：

```java
@Component
public class ToolRegistry {

    private final Map<String, Object> tools = new HashMap<>();

    @Init
    public void scanTools(ApplicationContext context) {
        context.getBeansOfType(Object.class).forEach((name, bean) -> {
            Method[] methods = bean.getClass().getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(ToolMapping.class)) {
                    registerTool(method);
                }
            }
        });
    }
}
```

### 6.3 JSON 序列化配置

Snack4 序列化器配置：

```java
@Configuration
public class SerializationConfig {

    @Bean
    public void configSerializer(Snack4StringSerializer serializer) {
        serializer.getSerializeConfig()
            .addFeatures(Feature.Write_DateUseFormat);
    }
}
```

### 6.4 工作目录初始化

```java
@Configuration
public class WorkspaceConfig {

    @Bean
    public WorkspaceInfo workspaceInfo() {
        Path workspace = Paths.get(workspacePath);
        workspace.toFile().mkdirs();

        return new WorkspaceInfo(
            workspace,
            workspace.resolve("mcp.json"),
            workspace.resolve("skills"),
            // ...
        );
    }
}
```

## 7. 开发指南

### 7.1 添加新工具

1. 创建工具类：
```java
package com.jimuqu.solonclaw.tool.impl;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;

@Component
public class MyTool {

    @ToolMapping(description = "工具描述")
    public String execute(
        @Param(description = "参数描述") String param
    ) {
        // 实现逻辑
        return "结果";
    }
}
```

2. 工具自动注册到 Agent

### 7.2 添加新接口

1. 在 `GatewayController` 添加方法：
```java
@Get
@Mapping("/api/new-endpoint")
public Result newEndpoint() {
    return Result.success("消息", data);
}
```

2. 测试接口：
```bash
curl http://localhost:41234/api/new-endpoint
```

### 7.3 调试配置

开发环境配置（app-dev.yml）：
```yaml
solon:
  port: 41234
  env: dev
  logging:
    level:
      root: INFO
      com.jimuqu.solonclaw: DEBUG
```

## 8. 部署指南

### 8.1 编译打包

```bash
mvn clean package -DskipTests
```

生成文件：`target/solonclaw.jar`

### 8.2 运行

```bash
java -jar solonclaw.jar
```

### 8.3 系统服务配置

创建 `/etc/systemd/system/solonclaw.service`：

```ini
[Unit]
Description=SolonClaw AI Assistant
After=network.target

[Service]
Type=simple
User=solonclaw
WorkingDirectory=/opt/solonclaw
ExecStart=/usr/bin/java -jar /opt/solonclaw/solonclaw.jar
Restart=always
Environment=OPENAI_API_KEY=your_api_key

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl enable solonclaw
sudo systemctl start solonclaw
```

## 9. 性能优化

### 9.1 数据库连接池

使用 HikariCP 管理数据库连接：

```yaml
nullclaw:
  database:
    pool:
      maxSize: 10
      connectionTimeout: 30000
```

### 9.2 请求限流

在 Gateway 添加限流：

```java
@Component
public class RateLimiter {

    private final RateLimiter limiter = RateLimiter.create(100.0);

    @Around("execution(* com.jimuqu.solonclaw.gateway..*(..))")
    public Object limit(ProceedingJoinPoint pjp) throws Throwable {
        if (!limiter.tryAcquire()) {
            return Result.error("请求过于频繁");
        }
        return pjp.proceed();
    }
}
```

## 10. 安全考虑

### 10.1 Shell 命令执行

- 超时控制（默认 60 秒）
- 输出大小限制（默认 1MB）
- 禁止交互式命令
- 命令白名单（可选）

### 10.2 API 访问控制

- API Key 验证（可选）
- IP 白名单（可选）
- 请求签名验证（回调接口）

## 11. 测试

### 11.1 单元测试

```java
@Test
public void testShellTool() {
    ShellTool tool = new ShellTool();
    String result = tool.exec("echo hello");
    assertEquals("hello\n", result);
}
```

### 11.2 集成测试

```java
@Test
public void testChatEndpoint() {
    String response = Rest.post("http://localhost:41234/api/chat")
        .body("{\"message\":\"test\"}")
        .executeAsString();
    assertNotNull(response);
}
```

## 12. 故障排查

### 12.1 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 端口占用 | 端口已被使用 | 修改配置或释放端口 |
| AI 调用失败 | API Key 无效 | 检查 OPENAI_API_KEY |
| 数据库错误 | 文件权限问题 | 检查 workspace 目录权限 |

### 12.2 日志查看

```bash
tail -f workspace/logs/solonclaw.log
```

## 13. 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0-SNAPSHOT | 2026-02-28 | 初始版本 |

## 14. 参考资料

- [Solon 官方文档](https://solon.noear.org/)
- [Solon AI 文档](https://solon.noear.org/article/ai-introduction)
- [OpenAI API 文档](https://platform.openai.com/docs)
- [MCP 协议规范](https://modelcontextprotocol.io/)
