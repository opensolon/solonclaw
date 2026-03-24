# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

SolonClaw 是基于 Solon 3.9.6 构建的轻量级 AI Agent 服务，将模型调用、会话历史、子任务编排、工作区工具、定时任务、钉钉/飞书渠道统一到同一套运行时中。主包名 `com.jimuqu.claw`，Java 8，Maven 构建。

基础架构思路参考了 [HKUDS/nanobot](https://github.com/HKUDS/nanobot)，但以当前仓库代码与测试为准。

## 常用命令

```bash
mvn -q -DskipTests compile          # 编译（跳过测试）
mvn -q test                         # 运行所有测试
mvn -q test -Dtest=ClassName        # 运行单个测试类
mvn -q test -Dtest=ClassName#method # 运行单个测试方法
mvn clean package -DskipTests       # 打包
java -jar target/solonclaw.jar      # 生产模式启动（端口 12345）
java -jar target/solonclaw.jar --env=dev   # 开发模式启动
```

`ChatModelConfigTest` 在本地 Ollama 不可达时会自动跳过真实调用测试。

## 核心架构

### 消息处理主链路

```
外部消息（钉钉/飞书/系统任务）
  → ChannelAdapter（入站适配）
  → AgentRuntimeService.submitInbound()（去重 → 写会话事件 → 创建 AgentRun）
  → ConversationScheduler（按 sessionKey 并发控制，最大 4 并发）
  → SolonAiConversationAgent.execute()
    → WorkspacePromptService.buildSystemPrompt()（拼装系统提示词）
    → ReActAgent（Solon AI ReAct 框架）
      → ChatModel + WorkspaceAgentTools + ConversationRuntimeTools + CliSkillProvider + JobTools
  → 写回 RunEvent / ConversationEvent
  → ChannelRegistry.send()（OutboundEnvelope 回发）
```

### 核心抽象

| 抽象 | 职责 |
|------|------|
| `InboundEnvelope` / `OutboundEnvelope` | 标准化入站/出站消息 |
| `ReplyTarget` | 唯一可信回复路由，**不允许猜测** |
| `AgentRun` | 一次 Agent 执行任务 |
| `ConversationEvent` / `RunEvent` | 会话/运行过程事件 |
| `ChannelAdapter` / `ChannelRegistry` | 渠道适配器接口与注册表 |
| `RuntimeStoreService` | 统一文件持久化（JSONL），会话历史**只能通过此类维护** |

### 关键模块位置

- **运行时**：`agent/runtime/` — AgentRuntimeService, ConversationScheduler, SolonAiConversationAgent, HeartbeatService
- **持久化**：`agent/store/RuntimeStoreService` — 所有文件落盘（runs, conversations, dedup, meta, media）
- **工作区**：`agent/workspace/` — 路径边界管理、模板初始化、系统提示词拼装
- **工具**：`agent/tool/` — WorkspaceAgentTools, ConversationRuntimeTools, JobTools
- **定时任务**：`agent/job/` — WorkspaceJobService, JobStoreService, JobDefinition
- **渠道**：`channel/dingtalk/`, `channel/feishu/`
- **装配**：`config/SolonClawConfig`（统一 @Bean）、`config/SolonClawProperties`（@BindProps prefix=solonclaw）
- **配置POJO**：`config/props/` — 各模块独立配置类（不允许在 SolonClawProperties 中堆叠内部类）
- **入口**：`SolonClawApp`（@SolonMain + @EnableScheduling）

### 工作区文件系统

默认根目录 `./workspace`，自动初始化模板文件并参与系统提示词拼装：

`AGENTS.md`, `SOUL.md`, `IDENTITY.md`, `USER.md`, `TOOLS.md`, `HEARTBEAT.md`, `MEMORY.md`, `GOVERNANCE.md`, `BOOTSTRAP.md`, `memory/YYYY-MM-DD.md`

运行时数据落在 `workspace/runtime/`（runs, conversations, dedup, meta, media）和 `workspace/jobs.json`。

## 开发硬规则

1. 新增渠道先抽象成 `ChannelAdapter`，再注册到 `ChannelRegistry`，不要绕开统一运行时
2. 回复必须来自 `ReplyTarget`，不可猜测路由
3. 会话历史只能通过 `RuntimeStoreService` 维护，不要在别处自拼 JSONL
4. **禁止把系统退回成全局串行队列**
5. 长时运行资源必须接入 Solon 生命周期（initMethod / destroyMethod）
6. 新增配置优先并入 `SolonClawProperties`，配置承载对象拆成独立类放 `config/props/`
7. 数据类（实体、DTO、事件载荷、配置承载）优先使用 Lombok `@Data + @NoArgsConstructor`
8. 不允许或尽量减少内部类，尤其是配置承载对象
9. 需要持久化或序列化的类应按需实现 `Serializable`
10. 工具/子任务/通知/定时任务都应复用统一运行时，不要平行造轮子
11. 提示词相关改动走 `WorkspacePromptService`，不在控制器或渠道层手拼
12. 模型调用走 `SolonAiConversationAgent`，渠道层不直接耦合 LLM

## Commit 规范

Conventional Commits，中英双语，按职责拆分 commit：

```
<type>(<scope>): <中文描述> (<English description>)
```

- `type` 必填：feat / fix / docs / style / refactor / perf / test / chore / revert / build
- `scope` 选填：模块名或职责范围（runtime / workspace / agent / web 等）
- 冒号后必须有空格
- 一个 commit 只解决一类问题

## 配置要点

- 主配置：`src/main/resources/app.yml`，开发配置：`app-dev.yml`，测试配置：`src/test/resources/app.yml`
- 生产密钥通过外部 `./config.yml` 注入（`solon.config.add=./config.yml`），不进仓库
- 默认偏向本地 Ollama，模型参数在 `solon.ai.chat.default` 下配置
- 关键配置项前缀：`solonclaw.agent.scheduler.*`, `solonclaw.agent.tools.*`, `solonclaw.agent.heartbeat.*`, `solonclaw.channels.*`
