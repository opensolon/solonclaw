# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**SolonClaw** 是一个基于 Solon 框架的轻量级 AI Agent 服务，提供 HTTP API 接口与 AI 进行对话交互，支持工具调用、会话记忆、定时任务等功能。

- **技术栈**: Java 17 + Solon 3.9.4 + solon-ai-core
- **主入口**: `com.jimuqu.solonclaw.SolonClawApp`
- **默认端口**: 41234
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
