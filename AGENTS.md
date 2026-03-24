# AGENTS

## 置顶说明

本仓库的基础架构思路学习和参考了开源项目 [HKUDS/nanobot](https://github.com/HKUDS/nanobot)。

当前仓库不是对该项目的直接搬运，而是基于 `Solon + Solon AI + 文件工作区` 重新实现的一套统一 Agent 运行时。后续理解和改造本项目时，应以当前仓库代码与测试为准。

## 文档目标

本文件面向当前 `SolonClaw` 仓库，帮助新的代理或开发者快速理解：

- 当前真实技术栈
- 运行时装配方式
- 会话/任务/子任务的行为边界
- 工作区、持久化、调试与渠道约束
- 修改代码时默认要遵守的协作规则

这不是 Solon 教程摘录，而是“基于当前代码状态”的项目协作说明。

## 当前技术栈

- Java `8`
- Solon `3.9.5`
- `solon-web`
- `solon-ai`
- `solon-ai-agent`
- `solon-ai-skill-cli`
- `solon-scheduling-simple`
- `solon-serialization-snack4`
- `solon-logging-logback-jakarta`
- `solon-test`
- Hutool `5.8.44`
- 钉钉 Stream SDK：`com.dingtalk.open:dingtalk-stream:1.1.0`
- 钉钉 OpenAPI SDK：`com.aliyun:dingtalk:1.5.59`

参考文档仍可看 `docs/Solon-v3.9.4.md`，但实际行为以当前代码和测试为准。

## 项目入口与装配

应用入口：

- [src/main/java/com/jimuqu/claw/SolonClawApp.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/SolonClawApp.java)

统一装配入口：

- [src/main/java/com/jimuqu/claw/config/SolonClawConfig.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/config/SolonClawConfig.java)
- [src/main/java/com/jimuqu/claw/config/SolonClawProperties.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/config/SolonClawProperties.java)

当前装配特点：

- 项目自定义配置通过 `@BindProps(prefix = "solonclaw")` 绑定
- 运行时依赖统一在 `SolonClawConfig` 中以 `@Bean` 装配
- 长时资源统一走 `initMethod` / `destroyMethod`
- `@EnableScheduling` 已在应用入口启用

当前已接入生命周期管理的资源包括：

- `WorkspaceJobService`：启动时恢复持久化任务
- `DingTalkAccessTokenService`
- `DingTalkChannelAdapter`
- `HeartbeatService`

默认约定：

- 新增组件、控制器、配置类优先放在 `com.jimuqu.claw` 包下
- 第三方对象或复杂对象优先用 `@Configuration + @Bean`
- 普通业务对象优先保持容器托管，不要手动 `new`

## 当前核心架构

项目当前已经不是单纯的 Solon Web Demo，而是一套“统一运行时 + 多渠道适配 + 工作区驱动提示词 + 可派生子任务 + 可持久化定时任务”的 Agent 服务。

### 1. 统一消息模型

位于 [src/main/java/com/jimuqu/claw/agent/model](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/model) 及其子包：

- [src/main/java/com/jimuqu/claw/agent/model/envelope](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/model/envelope)
- [src/main/java/com/jimuqu/claw/agent/model/event](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/model/event)
- [src/main/java/com/jimuqu/claw/agent/model/route](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/model/route)
- [src/main/java/com/jimuqu/claw/agent/model/run](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/model/run)
- [src/main/java/com/jimuqu/claw/agent/model/enums](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/model/enums)

- `InboundEnvelope`：标准化后的入站消息
- `OutboundEnvelope`：标准化后的出站消息
- `ReplyTarget`：唯一可信的回复路由
- `AgentRun`：一次运行任务
- `ConversationEvent`：会话事件
- `RunEvent`：运行过程事件
- `LatestReplyRoute`：最近一次可回复外部路由
- `ChildRunSpawnedData` / `ChildRunCompletedData`：子任务事件载荷

硬规则：

- 回复路由只能来自 `ReplyTarget`
- 不允许根据“当前上下文”猜回复目标
- 渠道之间的 `sessionKey` 必须隔离，不能共享命名空间
- `SYSTEM` 类型消息不应覆盖最近一次真实外部会话路由

### 2. 运行时主链路

核心类位于 [src/main/java/com/jimuqu/claw/agent/runtime](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime)：

- [AgentRuntimeService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime/AgentRuntimeService.java)
- [ConversationScheduler.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime/ConversationScheduler.java)
- [SolonAiConversationAgent.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime/SolonAiConversationAgent.java)
- [HeartbeatService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime/HeartbeatService.java)

当前实际流程：

1. 渠道或系统构造 `InboundEnvelope`
2. `AgentRuntimeService` 先做去重、写入会话事件、保存外部 `ReplyTarget`
3. 为该消息创建独立 `runId`
4. `ConversationScheduler` 按 `sessionKey` 控制会话级并发
5. `SolonAiConversationAgent` 基于历史、当前消息、工具和技能执行
6. 结果写入 `RunEvent` 和 `ConversationEvent`
7. 若允许对外回发，则通过原渠道回发

### 3. 会话并发与一致性规则

这是当前项目最重要的行为约束：

- 每条消息都是独立 run
- 并发控制是“按会话”，不是“全局串行”
- 单会话最大并发来自 `solonclaw.agent.scheduler.maxConcurrentPerConversation`
- 当前 `app.yml` 中有效值是 `4`
- `SolonClawProperties` 代码默认 `ackWhenBusy=true`，但当前 `app.yml` 覆盖为 `false`
- 当 `ackWhenBusy=true` 且该会话已有活跃任务时，系统会立刻发送“已收到”回执
- 历史重建按“用户消息顺序 + 已完成回复 + 可渲染系统事件”组织，不按完成时间倒灌重排
- 应用重启后，未完成 run 会被标记为 `ABORTED`

任何扩展都不能把系统退回成“全局单线程串行队列”。

### 4. 子任务与 continuation 机制

当前运行时支持把一个大任务拆成多个独立子任务。

相关能力：

- `spawn_task`
- `list_child_runs`
- `get_run_status`
- `get_child_summary`

实现特点：

- 子任务使用独立 `childSessionKey`
- 子任务应显式携带 `taskTitle` 与 `taskDescription`；标题用于日志、汇总和区分不同子任务
- 子任务 run 会记录 `parentRunId`、`parentSessionKey`、`parentReplyTarget`
- 子任务完成后，会向父会话写入结构化事件并自动触发一次 continuation run
- 父运行可进入 `WAITING_CHILDREN`
- 父运行派生子任务后，默认应先向用户说明已经安排了哪些子任务以及后续同步方式
- 子任务每次完成后，父会话都可以基于该次结果立即增量回复，不必默认等待全部子任务结束
- 只有在确实需要“全部结束后统一收口”的场景，才使用 `FINAL_REPLY_ONCE:` 前缀实现“仅发送一次最终聚合回复”
- `batchKey` 可用于给同一批子任务分组聚合

### 5. 主动通知能力

当前运行时支持在一次运行中主动向当前外部会话发送通知。

相关能力：

- `notify_user`
- `NotificationSupport`

边界：

- 只有当前会话已经绑定可用 `ReplyTarget` 时才能主动通知
- 主动通知会写入 `RunEvent`
- 主动通知不等于普通最终回复，两者可以分离

## 工作区与提示词系统

工作区由 [src/main/java/com/jimuqu/claw/agent/workspace](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/workspace) 负责：

- [AgentWorkspaceService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/workspace/AgentWorkspaceService.java)
- [WorkspacePromptService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/workspace/WorkspacePromptService.java)

当前行为：

- 默认工作区根目录为 `./workspace`
- 所有运行期文件默认都落在该工作区下
- 新工作区启动时会自动初始化一组模板文件
- 系统提示词会拼装工作区中的引导文件和最近两天的记忆文件

当前会自动关注的文件包括：

- `AGENTS.md`
- `SOUL.md`
- `IDENTITY.md`
- `USER.md`
- `TOOLS.md`
- `HEARTBEAT.md`
- `BOOTSTRAP.md`
- `MEMORY.md`
- `memory/YYYY-MM-DD.md`（今天和昨天）

内置模板位于：

- [src/main/resources/template](D:/IdeaProjects/SolonClaw/src/main/resources/template)

协作规则：

- 如果你在做“行为约束、人格、用户偏好、长期记忆”相关改动，要同时理解工作区模板机制
- 不要在控制器或渠道层手拼系统提示词
- 这部分应优先改 `WorkspacePromptService`

## 工具、技能与定时任务

### 1. 工作区工具

相关类位于 [src/main/java/com/jimuqu/claw/agent/tool](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/tool)：

- `WorkspaceAgentTools`
- `ConversationRuntimeTools`
- `JobTools`

当前内置工具能力包括：

- `read_file`
- `write_file`
- `edit_file`
- `notify_user`
- `spawn_task`
- `list_child_runs`
- `get_run_status`
- `get_child_summary`
- `list_jobs`
- `get_job`
- `add_job`
- `remove_job`
- `start_job`
- `stop_job`

工作区工具边界：

- 文件读写路径必须在工作区内
- 工具输出会截断，避免模型上下文过大

### 2. CLI 技能

当前已启用 `solon-ai-skill-cli`，并通过 `CliSkillProvider` 把工作区下的 `skills` 目录挂成技能池：

- 技能池名：`@skills`
- 实际目录：`./workspace/skills`
- `TerminalSkill` 的 `bash` 能力默认启用沙盒模式，可通过 `solonclaw.agent.tools.sandboxMode` 配置开关

当前 `sandboxMode` 行为边界：

- `true`：`bash` / `ls` / `read` / `grep` / `glob` 等 CLI 能力只允许工作区相对路径、`~/` 和 `@skills` 这类逻辑路径
- `true`：禁止在命令或路径参数中直接使用绝对路径，也禁止通过相对路径越出工作区
- `true`：`@skills` 这类逻辑路径仍可读、可执行，但保持只读，不能写入
- `false`：允许绝对路径访问，CLI 能力会进入更开放模式
- 无论开关如何，`@skills` 逻辑路径始终是只读挂载池

协作规则：

- 如果要扩展 CLI 技能能力，优先查看 `SolonClawConfig.cliSkillProvider`
- 不要把技能机制绕开成“硬编码一堆特判”

### 3. 定时任务

相关类位于 [src/main/java/com/jimuqu/claw/agent/job](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/job)：

- [JobDefinition.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/job/JobDefinition.java)
- [JobStoreService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/job/JobStoreService.java)
- [WorkspaceJobService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/job/WorkspaceJobService.java)

当前行为：

- 定时任务定义持久化到工作区根目录 `jobs.json`
- 应用启动时会自动恢复任务
- 新建任务时，会绑定“最近一次外部会话路由”
- 定时触发后，本质上仍然是向统一运行时提交一条系统消息

支持的模式：

- `fixed_rate`
- `fixed_delay`
- `once_delay`
- `cron`

协作规则：

- 任务执行闭环仍然必须走 `AgentRuntimeService`
- 不要单独实现第二套调度执行链路

## 文件持久化约定

运行时落盘统一由 [src/main/java/com/jimuqu/claw/agent/store/RuntimeStoreService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/store/RuntimeStoreService.java) 负责。

当前目录语义：

- `workspace/runtime/runs`：run 明细与 run 事件
- `workspace/runtime/conversations`：会话事件和会话元数据
- `workspace/runtime/dedup`：消息去重标记
- `workspace/runtime/meta`：最近回复路由等元数据
- `workspace/runtime/media`：按渠道分目录的媒体缓存
- `workspace/jobs.json`：定时任务定义

协作规则：

- 会话历史只能通过 `RuntimeStoreService` 读取和追加
- 不要在别处自己拼 JSON / JSONL 落盘结构
- 如果新增事件类型，优先保持向后兼容，不要破坏已有 JSONL 结构
- 系统事件是否进入历史，需要遵守 `RuntimeStoreService.loadConversationHistoryBefore` 的重建逻辑

## 当前渠道实现

### 钉钉

钉钉实现位于 [src/main/java/com/jimuqu/claw/channel/dingtalk](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/channel/dingtalk)：

- [DingTalkChannelAdapter.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkChannelAdapter.java)
- [DingTalkAccessTokenService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkAccessTokenService.java)
- [DingTalkRobotSender.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkRobotSender.java)

当前方案固定为：

- 收消息：`DingTalkStreamTopics.BOT_MESSAGE_TOPIC`
- 回调类型：`OpenDingTalkCallbackListener<ChatbotMessage, JSONObject>`
- 发消息：官方机器人 OpenAPI
- 群聊发送：`orgGroupSend`
- 私聊发送：`batchSendOTO`
- 回复格式：markdown 文本

当前行为边界：

- 群聊与私聊会映射到不同 `sessionKey`
- 群聊消息回群，私聊消息回原用户
- 回复内容只走 `ReplyTarget`
- 入站文本优先取 `text.content`，其次回退 `content.content` / `recognition`
- 附件当前只做文本退化，不做复杂媒体回发
- 如果白名单为空，当前代码行为是“默认允许”
- 一旦配置白名单，则只允许命中项通过

如果要新增企业微信、QQ 等渠道，应直接复用：

- `ChannelAdapter`
- `ChannelRegistry`
- `InboundEnvelope / OutboundEnvelope / ReplyTarget`
- `AgentRuntimeService`

不要绕开统一运行时单独写一套消息处理闭环。

## AI 模型约定

聊天模型由 [src/main/java/com/jimuqu/claw/llm/ChatModelConfig.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/llm/ChatModelConfig.java) 提供 Bean。

当前约定：

- 统一注入 `ChatModel`
- 具体模型参数来自 `solon.ai.chat.default`
- 当前仓库默认偏向本地 Ollama
- Agent 执行层由 `SolonAiConversationAgent` 封装
- 控制器、渠道层不要直接拼模型调用

当前 `app-dev.yml` / 测试配置均使用本地 Ollama 示例：

- `apiUrl: http://127.0.0.1:11434/api/chat`
- `provider: ollama`
- `model: qwen3.5:0.8b`

协作建议：

- 要扩展 tool、memory、prompt、技能、任务编排，优先修改 `ConversationAgent` 实现层
- 不要在渠道代码里直接耦合具体 LLM 细节

## 配置规则

主配置文件：

- [src/main/resources/app.yml](D:/IdeaProjects/SolonClaw/src/main/resources/app.yml)

开发配置：

- [src/main/resources/app-dev.yml](D:/IdeaProjects/SolonClaw/src/main/resources/app-dev.yml)

测试配置：

- [src/test/resources/app.yml](D:/IdeaProjects/SolonClaw/src/test/resources/app.yml)

外部示例配置：

- [scripts/config.example.yml](D:/IdeaProjects/SolonClaw/scripts/config.example.yml)

当前关键配置：

- `solon.env=prod`
- `server.port=12345`
- `solon.config.add=./config.yml` 仅在 `prod` 段追加
- `solonclaw.workspace=./workspace`
- `solonclaw.agent.scheduler.maxConcurrentPerConversation=4`
- `solonclaw.agent.scheduler.ackWhenBusy=false`
- `solonclaw.agent.tools.sandboxMode=true`
- `solonclaw.agent.heartbeat.enabled=true`
- `solonclaw.agent.heartbeat.intervalSeconds=1800`
- `solonclaw.channels.dingtalk.*`

敏感信息规则：

- 密钥不进仓库
- 生产环境通过外部 `./config.yml` 注入
- 不要随意覆盖他人的本地模型配置

## 心跳机制

心跳由 [HeartbeatService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime/HeartbeatService.java) 负责。

当前行为：

- 定时读取工作区根目录 `HEARTBEAT.md`
- 如果文件不存在或为空则跳过
- 使用最近一次外部会话路由投递一条静默系统消息
- 心跳检查不会直接对外发送消息
- 是否最终对外通知，由本次内部运行自己决定

协作规则：

- 心跳是“静默内部检查”，不是“固定外发播报器”
- 如果修改心跳，不要破坏“默认不直接外发”的约束

## 测试与验证

当前测试覆盖已包括：

- 基础 Solon 启动与 HTTP 测试
- `ChatModel` Bean 装配
- 工作区提示词模板装配
- 工作区工具边界
- 运行时落盘
- 同会话并发与忙时回执
- 子任务派生、聚合、按批次查询
- 主动通知能力
- 心跳静默执行
- 钉钉入站转换
- 钉钉 markdown 发送参数
- 定时任务持久化

常用命令：

```bash
mvn -q -DskipTests compile
mvn -q test
mvn clean package -DskipTests
java -jar target/solonclaw.jar
java -jar target/solonclaw.jar --env=dev
```

说明：

- [ChatModelConfigTest.java](D:/IdeaProjects/SolonClaw/src/test/java/com/jimuqu/claw/llm/ChatModelConfigTest.java) 在本地 Ollama 不可达时会跳过真实对话测试

## 修改代码时的默认规则

1. 新增渠道先抽象成 `ChannelAdapter`，再注册到 `ChannelRegistry`
2. 回复必须绑定 `ReplyTarget`，不能临时猜测去向
3. 会话历史只能通过 `RuntimeStoreService` 维护
4. 长时运行资源必须显式接入 Solon 生命周期
5. 新增配置优先并入 `SolonClawProperties`
6. 调试能力优先通过现有测试、运行事件和日志观测完成，不要另造本地消息通道
7. 钉钉相关改动要同时考虑私聊、群聊、白名单、markdown 发送和回复路由
8. 工具、子任务、通知、定时任务都应复用统一运行时，不要平行造轮子
9. 工作区相关能力优先改 `AgentWorkspaceService / WorkspacePromptService / WorkspaceAgentTools`
10. Git 提交信息默认使用 Conventional Commits 风格：`<type>(<scope>): <subject>`；注意冒号 `:` 后必须有一个空格
11. `scope` 选填，表示 commit 作用范围；可以写模块名、目录名，或数据层 / 视图层 / runtime / workspace 这类职责范围
12. `subject` 必填，用于对 commit 做简短描述；默认继续使用中英双语描述，例如：`feat(agent): 增加了子任务聚合能力 (Add child-run aggregation)`
13. `type` 必填，可选值固定为：`feat` 新功能、`fix` 修复 bug、`docs` 文档注释、`style` 代码格式、`refactor` 重构优化、`perf` 性能优化、`test` 增加测试、`chore` 构建过程或辅助工具变动、`revert` 回退、`build` 打包
14. 提交代码时，默认按职责拆分 commit；优先拆成“提示词与上下文 / 运行时治理 / 配置默认值与注释 / 测试”这类最小修改单元，尽量做到一个 commit 只解决一类问题，避免把无关改动混在一起
15. 实体类、DTO、事件载荷、结果对象、配置承载对象这类数据类，优先使用 Lombok 管理字段访问器；明确适合的类优先使用 `@Data`
16. 无参构造优先交给 Lombok 管理；这类数据类默认优先使用 `@NoArgsConstructor`，不要继续手写大量空构造
17. 需要持久化、序列化传输、缓存或作为稳定数据载体的类，应按需实现 `Serializable`
18. 不允许或尽量减少内部类的使用；尤其是配置承载对象，应优先拆成独立类，例如不要在 `SolonClawProperties` 中持续堆叠大量静态内部类

## PR 规范

提交 Pull Request 时，默认遵守以下规范：

### 1. 基本要求

- 一个 PR 只解决一类问题，避免把无关改动混在一起
- PR 标题应清晰描述改动目的，建议与提交信息保持一致的中英双语风格
- PR 描述至少应说明：变更内容、变更原因、影响范围、验证方式
- 如果变更涉及接口、配置、运行时行为或用户可见结果，应在 PR 描述中明确写出

### 2. 推荐的 PR 描述结构

- `背景`
- `改动内容`
- `影响范围`
- `验证方式`
- `风险与回滚`

可参考下面的模板：

```md
## 背景
- 说明为什么要改

## 改动内容
- 列出本次核心变更

## 影响范围
- 说明涉及的模块、接口、配置或渠道

## 验证方式
- 说明执行过的测试、人工验证步骤和结果

## 风险与回滚
- 说明潜在风险，以及出现问题时如何回滚
```

### 3. 合并前检查

- 确认代码已完成自查
- 确认新增或修改的配置项已经补充文档
- 确认必要测试已执行
- 确认没有把无关调试代码、临时日志、无意义格式化一并提交
- 确认 PR 描述与实际改动一致

## AI 辅助开发说明

项目允许使用 AI 辅助编写代码、测试样例、脚本和文档。

但必须遵守以下规则：

- AI 生成内容可以作为草稿或实现辅助，不能替代开发者责任
- 所有 AI 生成或 AI 参与修改的代码，必须经过开发者人工阅读
- 所有待合并改动，必须经过开发者人工测试和验证
- 对高风险改动，必须由开发者确认实际行为符合预期后才能合并
- 不能因为“代码是 AI 生成的”而跳过 Review、测试或回归验证

这里的“人工测试和验证”至少包括其中一部分，且应与改动风险匹配：

- 本地编译通过
- 单元测试或集成测试通过
- 关键链路的手工验证通过
- 配置和部署方式经过人工检查

结论规则：

- 允许 AI 写代码
- 不允许未经开发者人工测试和验证就直接合并

## 参考入口

- [src/main/java/com/jimuqu/claw/SolonClawApp.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/SolonClawApp.java)
- [src/main/java/com/jimuqu/claw/config/SolonClawConfig.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/config/SolonClawConfig.java)
- [src/main/java/com/jimuqu/claw/config/SolonClawProperties.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/config/SolonClawProperties.java)
- [src/main/java/com/jimuqu/claw/agent/runtime/AgentRuntimeService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime/AgentRuntimeService.java)
- [src/main/java/com/jimuqu/claw/agent/runtime/SolonAiConversationAgent.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/runtime/SolonAiConversationAgent.java)
- [src/main/java/com/jimuqu/claw/agent/store/RuntimeStoreService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/store/RuntimeStoreService.java)
- [src/main/java/com/jimuqu/claw/agent/workspace/WorkspacePromptService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/workspace/WorkspacePromptService.java)
- [src/main/java/com/jimuqu/claw/agent/job/WorkspaceJobService.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/agent/job/WorkspaceJobService.java)
- [src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkChannelAdapter.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkChannelAdapter.java)
- [src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkRobotSender.java](D:/IdeaProjects/SolonClaw/src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkRobotSender.java)
- [src/main/resources/app.yml](D:/IdeaProjects/SolonClaw/src/main/resources/app.yml)
- [scripts/config.example.yml](D:/IdeaProjects/SolonClaw/scripts/config.example.yml)
