# SolonClaw 需求文档

## 项目信息

- **项目名称**: SolonClaw
- **组织**: com.jimuqu (积木区)
- **描述**: 基于 Solon 框架的轻量级 AI 助手服务
- **版本**: 1.0.0-SNAPSHOT

## 1. 功能需求

### 1.1 核心功能

| 功能 | 描述 | 优先级 |
|------|------|--------|
| AI 对话 | 通过 HTTP API 与 AI 进行对话交互 | 高 |
| 工具调用 | AI 可以调用 Shell 等工具执行命令 | 高 |
| 会话记忆 | 记录对话历史，支持多轮对话 | 中 |
| 定时任务 | Agent 可以创建和管理定时任务 | 中 |
| 主动通知 | 通过 Cron 和 HTTP 回调实现主动通知 | 中 |
| MCP 管理 | Agent 可以安装和管理 MCP 服务器 | 低 |
| Skills 管理 | Agent 可以安装和管理自定义技能 | 低 |

### 1.2 非功能需求

| 需求 | 要求 |
|------|------|
| 运行环境 | Linux 服务器 |
| 接口方式 | HTTP API（无 CLI） |
| 响应方式 | 一次性返回（非流式） |
| 用户模型 | 单用户助手（无用户隔离） |
| 数据存储 | 统一工作目录 |

## 2. AI 对话功能

### 2.1 需求描述

通过 HTTP 接口与 AI 进行交互，支持单轮和多轮对话。

### 2.2 API 设计

**请求**
```
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
    "sessionId": "会话ID",
    "response": "AI 响应内容"
  }
}
```

### 2.3 技术实现

- 使用 Solon AI 的 `ReActAgent` 实现
- 支持自动工具调用
- 集成 OpenAI 模型

## 3. 工具调用功能

### 3.1 需求描述

AI 可以调用预定义的工具执行具体操作，如执行 Shell 命令。

### 3.2 工具列表

| 工具 | 功能 | 权限 |
|------|------|------|
| Shell | 执行 Shell 命令 | 完全权限 |

### 3.3 技术实现

- 使用 `@ToolMapping` 注解暴露工具方法
- 使用 `@Param` 注解定义参数
- 实现工具结果标准化返回

### 3.4 安全考虑

- Shell 命令执行超时控制（默认 60 秒）
- 输出大小限制（默认 1MB）
- 命令执行日志记录

## 4. 会话记忆功能

### 4.1 需求描述

记录对话历史，支持上下文相关的多轮对话。

### 4.2 存储方式

- 使用 H2 嵌入式数据库
- 简单的关键词搜索
- 会话隔离（通过 sessionId）

### 4.3 数据结构

| 表名 | 字段 | 说明 |
|------|------|------|
| sessions | id, created_at, updated_at | 会话信息 |
| messages | id, session_id, role, content, timestamp | 消息记录 |

## 5. 定时任务功能

### 5.1 需求描述

Agent 可以创建和管理定时任务，无需轮询文件或数据库。

### 5.2 任务类型

- Cron 表达式任务
- 一次性任务
- 周期性任务

### 5.3 技术实现

- 使用 Solon 的 `IJobManager` 动态注册任务
- 任务持久化到 `jobs.json`
- 执行历史记录到 `job-history.json`

### 5.4 API 设计

```json
{
  "name": "任务名称",
  "cron": "0 0 * * *",  // Cron 表达式
  "action": {
    "type": "callback",
    "url": "http://...",
    "data": {}
  }
}
```

## 6. 主动通知功能

### 6.1 需求描述

Agent 可以主动向用户发送通知，不依赖用户请求。

### 6.2 通知方式

| 方式 | 实现方式 |
|------|----------|
| 定时触发 | Cron 调度器 |
| 事件触发 | HTTP 回调 |

### 6.3 回调配置

```yaml
nullclaw:
  callback:
    enabled: true
    url: "${CALLBACK_URL:}"
    secret: "${CALLBACK_SECRET:}"
```

## 7. MCP 管理功能

### 7.1 需求描述

Agent 可以安装和管理 MCP（Model Context Protocol）服务器，扩展工具能力。

### 7.2 安装方式

通过工具调用 `mcp_install` 安装 MCP 服务器。

### 7.3 配置存储

```json
{
  "servers": {
    "mcp-name": {
      "command": "path/to/executable",
      "args": ["--arg1", "value1"],
      "env": {}
    }
  }
}
```

### 7.4 启动加载

项目启动时自动加载 `workspace/mcp.json` 中的 MCP 配置。

## 8. Skills 管理功能

### 8.1 需求描述

Agent 可以安装和管理自定义技能（Skills），扩展功能。

### 8.2 安装方式

通过工具调用 `skill_install_from_github` 从 GitHub 安装技能。

### 8.3 技能目录结构

```
workspace/skills/
├── skill-name/
│   ├── skill.json      # 技能清单
│   ├── SKILL.md        # 技能说明
│   └── ...
```

### 8.4 启动加载

项目启动时扫描 `workspace/skills/` 目录，自动加载技能。

## 9. 数据管理

### 9.1 工作目录结构

```
workspace/
├── mcp.json                 # MCP 配置
├── jobs.json                # 定时任务配置
├── job-history.json         # 任务历史
├── memory.db                # 会话记忆数据库
├── workspace/               # Shell 工作目录
│   └── ...
├── skills/                  # Skills 目录
│   └── ...
└── logs/                    # 日志目录
    └── solonclaw.log
```

### 9.2 配置方式

所有数据目录在配置文件中统一管理：

```yaml
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
```

## 10. 环境要求

### 10.1 运行环境

- **操作系统**: Linux
- **Java**: JDK 17+
- **框架**: Solon 3.9.4+

### 10.2 启动方式

```bash
java -jar solonclaw.jar
```

### 10.3 端口配置

- 默认端口: 41234
- 可通过配置文件或系统属性修改

## 11. 依赖服务

### 11.1 AI 服务

- **Provider**: OpenAI
- **配置**: 环境变量 `OPENAI_API_KEY`

### 11.2 回调服务

- **URL**: 环境变量 `CALLBACK_URL`（可选）
- **Secret**: 环境变量 `CALLBACK_SECRET`（可选）

## 12. 开发优先级

### 第一阶段（当前）

- [x] 项目框架搭建
- [x] HTTP 接口基础
- [x] Shell 工具实现
- [x] 工作目录配置

### 第二阶段

- [ ] 集成 ReActAgent
- [ ] 实现会话记忆存储
- [ ] 实现工具自动发现与注册

### 第三阶段

- [ ] 实现动态调度（定时任务）
- [ ] 实现回调机制
- [ ] 实现 MCP 管理

### 第四阶段

- [ ] 实现 Skills 管理
- [ ] 性能优化
- [ ] 日志完善