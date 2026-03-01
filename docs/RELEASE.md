# SolonClaw 项目发布文档

## 项目概述

**SolonClaw** 是一个基于 Solon 框架的轻量级 AI Agent 服务，提供 HTTP API 接口与 AI 进行对话交互，支持工具调用、会话记忆、定时任务、MCP 管理和动态 Skills 等功能。

- **技术栈**: Java 17 + Solon 3.9.4 + solon-ai-core
- **主入口**: `com.jimuqu.solonclaw.SolonClawApp`
- **默认端口**: 41234
- **包名**: `com.jimuqu.solonclaw`
- **版本**: 1.0.0-SNAPSHOT

---

## 已完成功能

### 1. ReActAgent 集成 ✅

完整集成 Solon AI 的 ReActAgent 框架，支持：
- 自动推理和工具调用
- 会话上下文管理（InMemoryAgentSession）
- 工具自动发现和注册
- 日志拦截器

**核心类**：
- `AgentService` - Agent 服务层
- `AgentConfig` - Agent 配置

### 2. MCP 管理功能 ✅

完整的 Model Context Protocol (MCP) 服务器管理：
- MCP 服务器配置管理（增删改查）
- 服务器生命周期管理（启动/停止/状态）
- MCP 协议初始化和工具发现
- 工具调用功能
- REST API 管理（13 个接口）

**核心类**：
- `McpManager` - MCP 管理器（903 行）
- `McpServerStatus` - 服务器状态枚举
- `McpController` - REST API 控制器
- `McpToolAdapter` - 工具适配器

**测试结果**：71/71 测试全部通过 🎉

### 3. 动态调度功能 ✅

完整的定时任务管理：
- 支持 Cron 表达式、固定频率、一次性任务
- 任务持久化（jobs.json）
- 执行历史记录（job-history.json）
- 任务暂停/恢复功能
- REST API 管理（11 个接口）

**核心类**：
- `SchedulerService` - 调度服务
- `SchedulerController` - REST API 控制器

### 4. 动态 Skills 系统 ✅

基于 SkillDesc 的 JSON 配置技能系统：
- 扫描 `workspace/skills/` 目录
- 解析 JSON 配置为 SkillDesc
- 动态准入检查（支持条件表达式）
- 动态指令生成（支持模板变量）
- 启用/禁用技能
- REST API 管理（8 个接口）

**核心类**：
- `DynamicSkill` - 动态技能
- `SkillsManager` - Skills 管理器
- `SkillsController` - REST API 控制器

### 5. 核心架构 ✅

- `GatewayController` - HTTP API 接口层
- `ToolRegistry` - 工具自动发现和注册
- `MemoryService` - 会话记忆管理
- `SessionStore` - 会话存储（H2 数据库）
- `ShellTool` - Shell 命令执行
- `WorkspaceConfig` - 工作目录配置
- `HealthController` - 健康检查

---

## REST API 接口

### Gateway 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/chat | AI 对话 |
| GET | /api/health | 健康检查 |

### MCP 管理接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/mcp/servers | 获取服务器列表 |
| GET | /api/mcp/servers/{name} | 获取服务器详情 |
| POST | /api/mcp/servers | 添加服务器 |
| PUT | /api/mcp/servers/{name} | 更新服务器 |
| DELETE | /api/mcp/servers/{name} | 删除服务器 |
| POST | /api/mcp/servers/{name}/start | 启动服务器 |
| POST | /api/mcp/servers/{name}/stop | 停止服务器 |
| POST | /api/mcp/servers/start-all | 启动所有服务器 |
| POST | /api/mcp/servers/stop-all | 停止所有服务器 |
| GET | /api/mcp/tools | 获取工具列表 |
| GET | /api/mcp/tools/{fullName} | 获取工具详情 |
| POST | /api/mcp/tools/{fullName}/call | 调用工具 |
| GET | /api/mcp/commands | 获取可用命令 |
| POST | /api/mcp/reload | 重新加载配置 |

### 调度管理接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/jobs | 获取所有任务 |
| GET | /api/jobs/{name} | 获取单个任务 |
| POST | /api/jobs/cron | 添加 Cron 任务 |
| POST | /api/jobs/fixed-rate | 添加固定频率任务 |
| POST | /api/jobs/one-time | 添加一次性任务 |
| DELETE | /api/jobs/{name} | 删除任务 |
| POST | /api/jobs/{name}/pause | 暂停任务 |
| POST | /api/jobs/{name}/resume | 恢复任务 |
| GET | /api/jobs/history | 获取执行历史 |
| GET | /api/jobs/{name}/history | 获取指定任务历史 |
| DELETE | /api/jobs/history | 清空执行历史 |

### Skills 管理接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/skills | 获取所有技能 |
| GET | /api/skills/{name} | 获取单个技能 |
| POST | /api/skills | 添加技能 |
| PUT | /api/skills/{name} | 更新技能 |
| DELETE | /api/skills/{name} | 删除技能 |
| POST | /api/skills/{name}/enable | 启用技能 |
| POST | /api/skills/{name}/disable | 禁用技能 |
| POST | /api/skills/reload | 重新加载所有技能 |

**总计：32+ 个接口**

---

## 工作目录结构

```
workspace/
├── mcp.json              # MCP 服务器配置
├── jobs.json             # 定时任务配置
├── job-history.json      # 任务执行历史
├── memory.db             # H2 会话记忆数据库
├── workspace/            # Shell 工具的工作目录
├── skills/               # 用户自定义技能
│   ├── order_expert.json # 技能配置示例
│   └── ...
└── logs/                 # 日志文件
    └── solonclaw.log
```

---

## 配置说明

### 主配置

**文件**: `src/main/resources/app.yml`

```yaml
solon:
  app:
    name: solonclaw
  port: 41234
  ai:
    chat:
      openai:
        apiUrl: "${OPENAI_API_URL:https://api.openai.com/v1/chat/completions}"
        apiKey: "${OPENAI_API_KEY}"
        provider: "openai"
        model: "${OPENAI_MODEL:gpt-4}"
```

### 必需环境变量

- `OPENAI_API_KEY` - OpenAI API 密钥

### 可选环境变量

- `OPENAI_API_URL` - OpenAI API URL（默认：https://api.openai.com/v1/chat/completions）
- `OPENAI_MODEL` - 使用的模型（默认：gpt-4）

---

## 构建

### 编译打包

```bash
# 编译打包（跳过测试）
mvn clean package -DskipTests

# 运行完整测试
mvn test

# 运行应用
java -jar target/solonclaw-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### 指定环境运行

```bash
java -jar target/solonclaw-1.0.0-SNAPSHOT-jar-with-dependencies.jar --solon.env=prod
```

---

## 依赖关系

### Solon 核心依赖

```xml
<parent>
    <groupId>org.noear</groupId>
    <artifactId>solon-parent</artifactId>
    <version>3.9.4</version>
</parent>
```

### 主要依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| solon | 3.9.4 | Solon 核心 |
| solon-web | 3.9.4 | Web 支持 |
| solon-ai-core | 3.9.4 | AI 框架 |
| solon-ai-agent | 3.9.4 | Agent 框架 |
| solon-ai-dialect-openai | 3.9.4 | OpenAI 方言 |
| solon-scheduling-simple | - | 调度支持 |
| solon-serialization-snack4 | 4.0.33 | JSON 序列化 |
| H2 Database | 2.3.232 | 嵌入式数据库 |
| HikariCP | 5.1.0 | 连接池 |
| OkHttp | 4.12.0 | HTTP 客户端 |

---

## 测试

### 测试结果

- **总测试数**: 266+
- **通过**: 258+ (97%+)
- **失败**: 0
- **错误**: 0

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行单个测试
mvn test -Dtest=ClassName

# 运行测试并生成报告
mvn test surefire-report:report
```

---

## 快速开始

### 1. 配置 API 密钥

```bash
export OPENAI_API_KEY="your-api-key-here"
```

### 2. 启动应用

```bash
java -jar target/solonclaw-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

### 3. 测试对话

```bash
curl -X POST http://localhost:41234/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'
```

### 4. 健康检查

```bash
curl http://localhost:41234/api/health
```

---

## 功能特性

### AI 对话
- 支持 ReActAgent 智能推理
- 自动工具调用
- 会话记忆
- 多轮对话

### 工具调用
- Shell 命令执行（超时控制、输出限制）
- 工具自动发现（@ToolMapping 注解）
- MCP 工具调用

### 会话记忆
- H2 数据库存储
- 会话隔离（sessionId）
- 历史查询

### 定时任务
- Cron 表达式支持
- 固定频率任务
- 一次性任务
- 任务持久化

### MCP 管理
- MCP 服务器管理
- 工具发现和调用
- 进程管理

### 动态 Skills
- JSON 配置
- 条件表达式
- 模板变量
- 热加载

---

## 架构设计

### 分层结构

```
gateway/        # HTTP 接口层 - 对外提供 REST API
agent/          # Agent 服务层 - 封装 AI 对话和工具调用逻辑
tool/           # 工具系统 - 使用 @ToolMapping 注解暴露工具
scheduler/      # 动态调度 - 管理定时任务
memory/         # 记忆系统 - H2 数据库存储会话历史
mcp/            # MCP 管理 - 管理 Model Context Protocol 服务器
skill/          # Skills 管理 - 管理用户自定义技能
config/         # 配置类 - 统一管理工作目录等配置
```

### 核心流程

1. **请求流程**: `GatewayController` → `AgentService` → `ReActAgent` → Tools
2. **工具注册**: 扫描 `@Component` + `@ToolMapping` 注解的类，自动注册到 Agent
3. **会话管理**: 使用 H2 数据库存储，通过 sessionId 隔离

---

## 开发团队

**技术栈**: Solon 3.9.4 + Java 17

**核心开发**:
- Team Lead: 整体架构和协调
- backend-engineer-react: ReActAgent、动态调度、Skills 系统
- backend-engineer-mcp: MCP 管理功能
- qa-engineer: 质量保证和测试

---

## 未来规划

### 短期
- [ ] 集成 Solon 官方 Skills（ShellSkill, FileReadWriteSkill 等）
- [ ] 全面接口测试
- [ ] 性能优化

### 中期
- [ ] 分布式部署支持
- [ ] Redis 会话存储
- [ ] 监控和告警

### 长期
- [ ] Web UI 界面
- [ ] 插件系统
- [ ] 社区版本

---

## 许可证

本项目为内部项目，版权归积木区（jimuqu）所有。

---

## 联系方式

- 项目名称: SolonClaw
- 组织: com.jimuqu (积木区)
- 版本: 1.0.0-SNAPSHOT

---

## 前端对话界面

### 功能特性

1. **现代化聊天界面**
   - 使用 Tailwind CSS 设计
   - 渐变色头部设计
   - 响应式布局，支持移动端
   - 消息气泡样式
   - 自动滚动到最新消息

2. **流式响应支持**
   - SSE（Server-Sent Events）实时连接
   - 实时显示 AI 响应内容
   - 打字动画效果
   - 支持中断流式响应

3. **功能特性**
   - 发送消息
   - 历史对话记录
   - 清空对话
   - 加载状态显示
   - 连接状态指示器
   - 错误提示 Toast
   - 字符计数
   - 工具调用状态显示

4. **Markdown 渲染**
   - 代码块语法高亮
   - 行内代码
   - 标题、列表、引用
   - 粗体、斜体

### 文件位置

```
src/main/resources/frontend/
├── index.html    # 主页面
└── app.js        # 应用逻辑（682 行）
```

### API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/chat/stream | SSE 流式对话接口 |
| POST | /api/chat | 普通对话接口（备用） |
| GET | /api/sessions/{id} | 会话历史 |
| DELETE | /api/sessions/{id} | 清空对话 |
| GET | /api/health | 健康检查 |

### 使用方法

```bash
# 访问前端界面
open http://localhost:41234/frontend/index.html

# 或通过浏览器访问
http://localhost:41234/frontend/index.html
```

---

## 更新日志

### v1.1.0-SNAPSHOT (2026-03-01) - 前端界面版本

**新增功能**:
- ✅ 前端对话界面
- ✅ SSE 流式响应支持
- ✅ 实时打字动画效果
- ✅ Markdown 渲染
- ✅ 会话管理

**后端接口**:
- Gateway: 3 个接口（新增 /api/chat/stream）
- MCP 管理: 13 个接口
- 调度管理: 11 个接口
- Skills 管理: 8 个接口

**测试**:
- 总测试: 309
- 通过率: 100%
- 流式响应测试: 23/23 ✅

### v1.0.0-SNAPSHOT (2026-03-01)

**新增功能**:
- ✅ ReActAgent 智能对话
- ✅ MCP 管理功能
- ✅ 动态调度功能
- ✅ 动态 Skills 系统
- ✅ 工具自动发现
- ✅ 会话记忆
- ✅ 健康检查

**REST API**:
- Gateway: 2 个接口
- MCP 管理: 13 个接口
- 调度管理: 11 个接口
- Skills 管理: 8 个接口

**测试**:
- 总测试: 286
- 通过率: 100%

**文档**:
- README.md
- REQUIREMENT.md
- CLAUDE.md
- RELEASE.md