# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**SolonClaw** 是一个基于 Solon 框架的轻量级 AI Agent 服务，提供 HTTP API 接口与 AI 进行对话交互，支持工具调用、会话记忆、定时任务等功能。

- **技术栈**: Java 17 + Solon 3.9.4 + solon-ai-core
- **主入口**: `com.jimuqu.solonclaw.SolonClawApp`
- **默认端口**: 12345
- **包名**: `com.jimuqu.solonclaw`

## 常用命令

### 构建与运行
```bash
# 编译打包（跳过测试）
mvn clean package -DskipTests

# 运行完整测试
mvn test

# 运行应用
java -jar target/solonclaw-1.0.0-SNAPSHOT-jar-with-dependencies.jar

# 指定环境运行
java -jar target/solonclaw-1.0.0-SNAPSHOT-jar-with-dependencies.jar --solon.env=prod
```

### 开发调试
```bash
# 编译
mvn compile

# 清理
mvn clean

# 运行单个测试
mvn test -Dtest=ClassName

# 运行所有测试
mvn test

# 运行测试并生成报告
mvn test surefire-report:report
```

## 开发规则

### ⚠️ 项目特点（重要）

**这是一个新项目，不需要考虑版本迭代的兼容问题。**

- 删除旧代码时可以直接删除，无需保留兼容性代码
- 配置变更时直接修改，无需考虑旧版本兼容
- 重构时可以大胆调整，无需担心破坏现有用户

### ⚠️ 测试要求（重要）

**每个功能必须添加对应的测试用例，只有测试通过后才算任务完成。**

添加新功能时必须同时编写：
1. 单元测试：测试单个类或方法
2. 集成测试：测试组件间的交互
3. 确保所有测试通过：`mvn test`

测试用例应覆盖：
- 正常场景
- 边界条件
- 异常处理
- 数据验证

测试失败时，必须修复后才能标记任务为完成。

### 🤝 团队模式规范（重要）

**当使用 Agent 工具创建团队时，必须遵循以下规范：**

#### Team Lead 的核心职责

**作为 team lead，你的职责是协调和分配任务，而不是自己执行。请始终将工作分配给 teammates。**

- ❌ **禁止行为**：team lead 直接使用工具（Read、Edit、Write、Bash 等）完成任务
- ✅ **正确行为**：通过 `SendMessage` 将任务分配给合适的 teammates
- ✅ **正确行为**：使用 `TaskCreate` 和 `TaskUpdate` 管理任务列表
- ✅ **正确行为**：监控 teammates 的工作进度，协调依赖关系
- ✅ **正确行为**：汇总 teammates 的结果，向用户汇报

#### 团队协作流程

1. **创建团队**：使用 `TeamCreate` 创建团队和任务列表
2. **拆分任务**：分析用户需求，拆分为独立的任务，使用 `TaskCreate` 创建
3. **分配任务**：通过 `TaskUpdate` 的 `owner` 参数将任务分配给 teammates
4. **协调工作**：使用 `SendMessage` 通知 teammates 工作内容
5. **跟踪进度**：定期使用 `TaskList` 检查任务状态
6. **汇总结果**：收集所有 teammates 的完成结果，统一回复用户

#### Teammates 工作方式

- 自动检查 `TaskList`，认领未分配的任务（`status: pending`, `owner: 空`）
- 完成任务后使用 `TaskUpdate` 标记为 `completed`
- 遇到阻塞时，使用 `TaskUpdate` 设置 `addBlockedBy`
- 向 team lead 汇报进展或寻求帮助

#### 示例

```javascript
// ❌ 错误示例：team lead 直接干活
TaskCreate({ subject: "实现新功能" })
Read("some-file.js")  // team lead 不应该直接读取文件
Edit(...)             // team lead 不应该直接编辑

// ✅ 正确示例：team lead 分配任务
TaskCreate({ subject: "实现新功能", description: "..." })
TaskUpdate({ taskId: "1", owner: "researcher" })  // 分配给 researcher
SendMessage({ type: "message", recipient: "researcher", content: "请调研相关技术方案" })
```

## 核心架构

### 分层结构

```
gateway/        # HTTP 接口层 - 对外提供 REST API
agent/          # Agent 服务层 - 封装 AI 对话和工具调用逻辑
tool/           # 工具系统 - 使用 @ToolMapping 注解暴露工具
scheduler/      # 动态调度 - 管理定时任务（使用 IJobManager）
memory/         # 记忆系统 - H2 数据库存储会话历史
mcp/            # MCP 管理 - 管理 Model Context Protocol 服务器
skill/          # Skills 管理 - 管理用户自定义技能
config/         # 配置类 - 统一管理工作目录等配置
```

### 核心流程

1. **请求流程**: `GatewayController` → `AgentService` → `ReActAgent` → Tools
2. **工具注册**: 扫描 `@Component` + `@ToolMapping` 注解的类，自动注册到 Agent
3. **会话管理**: 使用 H2 数据库存储，通过 sessionId 隔离

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
```

## 添加新工具

工具使用 `@ToolMapping` 注解定义：

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

工具会自动被发现并注册到 Agent，无需额外配置。

## 添加新接口

在 `GatewayController` 中添加：

```java
@Get
@Mapping("/api/new-endpoint")
public Result newEndpoint() {
    return Result.success("消息", data);
}
```

使用统一响应格式 `Result(code, message, data)`。

## 配置说明

- **主配置**: `src/main/resources/app.yml`
- **开发环境**: `src/main/resources/app-dev.yml`
- **生产环境**: `src/main/resources/app-prod.yml`

### 必需环境变量

- `OPENAI_API_KEY` - OpenAI API 密钥

### 可选环境变量

- `CALLBACK_URL` - 回调通知 URL
- `CALLBACK_SECRET` - 回调签名密钥

## 关键实现细节

### ReActAgent 集成

`AgentService` 封装了 Solon AI 的 `ReActAgent`，支持：
- 自动推理和工具调用
- 会话上下文管理
- 工具自动发现

当前 `AgentService.chat()` 为简化实现，待集成完整的 ReActAgent 功能。

### 安全考虑

Shell 工具执行有内置保护：
- 超时控制：默认 60 秒
- 输出大小限制：默认 1MB
- 自动处理 Windows 和 Linux 命令差异

### 数据库

使用 H2 嵌入式数据库，数据结构：
- `sessions` 表 - 会话信息
- `messages` 表 - 消息记录

## 开发优先级

根据 `docs/requirement.md`，项目分为四个阶段：

- **第一阶段（当前）**: 基础框架、Shell 工具
- **第二阶段**: ReActAgent 集成、会话记忆
- **第三阶段**: 动态调度、回调、MCP 管理
- **第四阶段**: Skills 管理、性能优化
