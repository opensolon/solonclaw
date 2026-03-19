# SolonClaw

[English](./README.en.md)

> 置顶说明  
> 本项目的基础架构思路学习和参考了开源项目 [HKUDS/nanobot](https://github.com/HKUDS/nanobot)。  
> `SolonClaw` 不是对 nanobot 的直接移植，而是基于 `Solon + Solon AI + 文件工作区 + 多渠道适配` 的本地化实现。理解项目时，请以当前仓库代码与测试为准。

`SolonClaw` 是一个基于 `Solon 3.9.5` 构建的轻量级 Agent 服务。它把模型调用、会话历史、子任务编排、工作区工具、定时任务、调试页和钉钉渠道统一到同一套运行时中。

适合用来做这些事情：

- 🤖 构建一个可持续运行的个人/团队 AI 助手
- 💬 同时接本地 Debug Web 与钉钉机器人
- 🧠 让 Agent 基于工作区文件获得记忆、身份和行为约束
- 🛠️ 让 Agent 通过工具读写工作区、通过 CLI 技能执行命令、管理任务
- 🧩 把复杂问题拆成多个子任务并回流父会话
- ⏰ 创建持久化定时任务，让 Agent 定期执行工作

## 核心特性

- 🚀 统一运行时
  所有消息都会先进入 `AgentRuntimeService`，统一完成去重、会话落盘、调度、模型执行与渠道回发。

- 🔀 会话级并发
  并发控制按 `sessionKey` 生效，而不是全局串行。当前默认单会话最大并发数为 `4`。

- 🧵 子任务编排
  Agent 可以通过 `spawn_task` 派生独立子任务；子任务完成后会回流父会话，并支持按 `batchKey` 聚合状态。

- 🔔 主动通知
  Agent 在一次运行中可以主动向当前外部会话发送通知，而不必等最终回复。

- 🗂️ 工作区驱动提示词
  `AGENTS.md / SOUL.md / IDENTITY.md / USER.md / TOOLS.md / HEARTBEAT.md / MEMORY.md / memory/YYYY-MM-DD.md` 会自动参与系统提示词拼装。

- 🧰 内置工具与技能
  支持文件读写、片段编辑、任务查询、定时任务管理，并可从 `workspace/skills` 挂载 CLI 技能池 `@skills`。

- 🧪 本地调试友好
  自带 Debug Web 页面，可直接查看 run 状态、流式事件、子任务列表与聚合摘要。

- 📁 文件持久化
  会话、运行、去重标记、路由状态和任务定义都可以直接在工作区中看到。

## 当前架构

```text
Inbound Message
  -> ChannelAdapter / Debug Web / System Job
  -> AgentRuntimeService
  -> RuntimeStoreService (dedup + conversation events + run events + reply target)
  -> ConversationScheduler (per sessionKey concurrency)
  -> SolonAiConversationAgent
     -> ChatModel
     -> Workspace Tools
     -> Runtime Tools
     -> Job Tools
     -> CLI Skills (@skills)
  -> OutboundEnvelope
  -> ChannelRegistry
  -> DingTalk / Debug Web
```

核心模块：

- `agent/runtime`
  统一运行时、调度器、心跳、子任务与通知能力。

- `agent/store`
  统一文件落盘与历史重建。

- `agent/workspace`
  工作区路径边界、模板初始化、提示词拼装。

- `agent/tool`
  工作区工具、运行时工具、定时任务工具。

- `agent/job`
  持久化定时任务与恢复机制。

- `channel/dingtalk`
  钉钉 Stream 入站与机器人 OpenAPI 出站。

- `web`
  Debug Web 页面与调试 API。

## 目录与持久化

默认工作区根目录为 `./workspace`。

运行后常见文件结构如下：

```text
workspace/
  AGENTS.md
  SOUL.md
  IDENTITY.md
  USER.md
  TOOLS.md
  HEARTBEAT.md
  MEMORY.md
  memory/
  skills/
  jobs.json
  runtime/
    runs/
    conversations/
    dedup/
    meta/
    media/
```

其中：

- `workspace/runtime/runs`
  保存 run 明细与 run 事件。

- `workspace/runtime/conversations`
  保存会话事件和会话元数据。

- `workspace/runtime/meta`
  保存最近一次外部回复路由等状态。

- `workspace/jobs.json`
  保存定时任务定义。

## 当前已实现的渠道

### 1. Debug Web

本地调试页默认可用，访问根路径即可进入：

- `GET /`
- `POST /api/debug/chat`
- `GET /api/debug/runs/{runId}`
- `GET /api/debug/runs/{runId}/events`
- `GET /api/debug/runs/{runId}/children`

特性：

- 与钉钉共用同一个运行时
- `debug-web` 会话与外部渠道隔离
- 可查看流式事件、最终回复、子任务和聚合摘要

### 2. DingTalk

当前钉钉接入方式：

- 入站：`DingTalkStreamTopics.BOT_MESSAGE_TOPIC`
- 出站：机器人 OpenAPI
- 群聊：`orgGroupSend`
- 私聊：`batchSendOTO`
- 输出格式：markdown 文本

当前行为边界：

- 群聊与私聊会使用不同 `sessionKey`
- 回复目标完全依赖 `ReplyTarget`
- 附件暂时只做文本化降级
- 白名单为空时默认允许；一旦配置白名单，则只允许命中项通过

## Agent 当前能力

当前 Agent 可以使用的核心工具包括：

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

能力特点：

- 文件读写受工作区边界保护
- 命令执行走 CLI `TerminalSkill` 的 `bash` 能力
- `TerminalSkill` 默认启用沙盒模式，可通过 `solonclaw.agent.tools.sandboxMode` 控制
- 定时任务会绑定最近一次外部会话路由
- 心跳检查会读取 `HEARTBEAT.md` 并触发静默内部运行

`sandboxMode` 规则：

- `true`：CLI 的 `bash` / `ls` / `read` / `grep` / `glob` 等能力只允许工作区相对路径、`~/` 和 `@skills` 逻辑路径
- `true`：禁止绝对路径，禁止通过 `../` 等方式越出工作区
- `true`：`@skills` 逻辑路径可读、可执行，但仍是只读挂载池
- `false`：放开绝对路径访问，CLI 能力进入更开放模式

## 快速开始

### 1. 环境要求

- JDK `8`
- Maven `3.9+`
- 推荐本地安装并启动 Ollama

### 2. 配置模型

开发环境可直接使用 [src/main/resources/app-dev.yml](./src/main/resources/app-dev.yml) 中的 Ollama 示例配置。

生产或本地自定义配置建议参考：

- [scripts/config.example.yml](./scripts/config.example.yml)

可在项目根目录创建 `config.yml`，注入你的模型与钉钉配置。

### 3. 编译与测试

```bash
mvn -q -DskipTests compile
mvn -q test
```

说明：

- `ChatModelConfigTest` 在本地 Ollama 不可达时会自动跳过真实调用测试。

### 4. 启动应用

```bash
java -jar target/solonclaw.jar
```

或开发模式：

```bash
java -jar target/solonclaw.jar --env=dev
```

默认端口：

- `12345`

启动后可打开：

- [http://localhost:12345](http://localhost:12345)

### 5. 一键复制的启动命令

如果你希望 README 里直接给出“先设置变量，再启动”的现成命令，可以直接使用下面这些示例。

PowerShell：

```powershell
$env:APP_JAR="target/solonclaw.jar"
$env:APP_ENV="dev"
$env:APP_PORT="12345"
$env:APP_WORKSPACE="./workspace"
$env:APP_XMS="256m"
$env:APP_XMX="512m"

java `
  "-Xms$env:APP_XMS" `
  "-Xmx$env:APP_XMX" `
  "-Dserver.port=$env:APP_PORT" `
  "-Dsolonclaw.workspace=$env:APP_WORKSPACE" `
  -jar $env:APP_JAR `
  --env=$env:APP_ENV
```

Bash：

```bash
export APP_JAR="target/solonclaw.jar"
export APP_ENV="dev"
export APP_PORT="12345"
export APP_WORKSPACE="./workspace"
export JAVA_OPTS="-Xms256m -Xmx512m"

java ${JAVA_OPTS} \
  -Dserver.port="${APP_PORT}" \
  -Dsolonclaw.workspace="${APP_WORKSPACE}" \
  -jar "${APP_JAR}" \
  --env="${APP_ENV}"
```

生产环境示例：

```bash
export APP_JAR="target/solonclaw.jar"
export APP_ENV="prod"
export APP_PORT="12345"
export APP_WORKSPACE="./workspace"
export JAVA_OPTS="-Xms512m -Xmx1024m"

java ${JAVA_OPTS} \
  -Dserver.port="${APP_PORT}" \
  -Dsolonclaw.workspace="${APP_WORKSPACE}" \
  -jar "${APP_JAR}" \
  --env="${APP_ENV}"
```

## 配置说明

主配置文件：

- `src/main/resources/app.yml`

当前关键项：

- `solonclaw.workspace=./workspace`
- `solonclaw.agent.scheduler.maxConcurrentPerConversation=4`
- `solonclaw.agent.scheduler.ackWhenBusy=false`
- `solonclaw.agent.tools.sandboxMode=true`
- `solonclaw.agent.heartbeat.enabled=true`
- `solonclaw.agent.heartbeat.intervalSeconds=1800`
- `solonclaw.channels.dingtalk.*`

注意：

- 仓库内不提交生产密钥
- `prod` 环境默认追加加载根目录 `./config.yml`

## Docker 部署

仓库现在已经提供：

- [Dockerfile](./Dockerfile)
- [docker-compose.yml](./docker-compose.yml)
- [.dockerignore](./.dockerignore)

### 1. 构建镜像

```bash
docker build -t solonclaw:latest .
```

### 2. 准备宿主机文件

建议在项目根目录准备：

- `config.yml`
- `workspace/`

其中：

- `config.yml` 用来放生产环境密钥、模型配置、钉钉配置
- `workspace/` 用来持久化运行时数据、记忆文件、技能和任务定义

可先参考：

- [scripts/config.example.yml](./scripts/config.example.yml)

### 3. 直接用 `docker run` 启动

```bash
docker run -d \
  --name solonclaw \
  -p 12345:12345 \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  -e APP_ARGS="--env=prod" \
  -v "$(pwd)/workspace:/app/workspace" \
  -v "$(pwd)/config.yml:/app/config.yml:ro" \
  solonclaw:latest
```

Windows PowerShell：

```powershell
docker run -d `
  --name solonclaw `
  -p 12345:12345 `
  -e JAVA_OPTS="-Xms256m -Xmx512m" `
  -e APP_ARGS="--env=prod" `
  -v "${PWD}/workspace:/app/workspace" `
  -v "${PWD}/config.yml:/app/config.yml:ro" `
  solonclaw:latest
```

说明：

- 容器工作目录固定为 `/app`
- `APP_ARGS="--env=prod"` 会启用 `app.yml` 中的 `prod` 段，并加载 `/app/config.yml`
- `workspace` 目录挂载后，运行数据不会随容器删除而丢失

### 4. 使用 Docker Compose 启动

```bash
docker compose up -d --build
```

停止：

```bash
docker compose down
```

查看日志：

```bash
docker compose logs -f solonclaw
```

当前 `docker-compose.yml` 默认会：

- 暴露端口 `12345`
- 挂载 `./workspace` 到 `/app/workspace`
- 挂载 `./config.yml` 到 `/app/config.yml`
- 以 `--env=prod` 启动应用

### 5. Docker Compose 文件说明

如果你需要自定义 JVM 参数或启动参数，可以直接修改 [docker-compose.yml](./docker-compose.yml) 里的环境变量：

- `JAVA_OPTS`
- `APP_ARGS`

例如：

```yaml
environment:
  JAVA_OPTS: "-Xms512m -Xmx1024m"
  APP_ARGS: "--env=prod"
```

### 6. 容器部署建议

- 生产环境务必挂载独立的 `workspace` 目录
- 生产环境不要把真实密钥写进镜像
- 如果使用本地 Ollama，请确保容器能访问你的模型服务地址
- 如果要接钉钉，请在 `config.yml` 中补齐 `clientId`、`clientSecret`、`robotCode`

## 测试覆盖

当前测试已覆盖这些方向：

- Solon 启动与 ChatModel 装配
- 工作区模板初始化与提示词拼装
- 工作区工具路径边界
- 运行时文件落盘
- 会话并发与忙时回执
- 子任务派生、回流、聚合与按批次查询
- 主动通知
- 心跳静默执行
- 钉钉入站转换与 markdown 发送参数
- 定时任务持久化

## 开发约束

- 新增渠道先抽象成 `ChannelAdapter`
- 回复必须来自 `ReplyTarget`
- 会话历史只能通过 `RuntimeStoreService` 维护
- 长时资源优先接入 Solon 生命周期
- 新增配置优先并入 `SolonClawProperties`
- 调试能力优先复用现有 Debug Web
- 不要把系统退回成全局串行队列

## PR 规范

建议所有 Pull Request 默认包含这些内容：

- `背景`
- `改动内容`
- `影响范围`
- `验证方式`
- `风险与回滚`

推荐模板：

```md
## 背景
- 为什么要做这次修改

## 改动内容
- 这次具体改了什么

## 影响范围
- 涉及哪些模块、接口、配置或部署方式

## 验证方式
- 执行了哪些测试
- 做了哪些人工验证

## 风险与回滚
- 潜在风险是什么
- 出现问题如何回滚
```

附加要求：

- 一个 PR 尽量只做一类改动
- PR 标题与提交信息建议使用中英双语
- 改动涉及配置或行为变化时，要同步更新文档

## Commit 规范

提交信息默认采用 Conventional Commits 风格：

```text
<type>(<scope>): <subject>
```

说明：

- 冒号 `:` 后必须有一个空格
- `scope` 选填，表示 commit 的作用范围，可以写模块名、目录名，或 `runtime`、`workspace`、`web` 这类职责范围
- `subject` 必填，用于简短描述本次提交内容，建议使用中英双语

`type` 必填，允许值如下：

- `feat`：新功能 feature
- `fix`：修复 bug
- `docs`：文档注释
- `style`：代码格式（不影响代码运行的变动）
- `refactor`：重构、优化（既不增加新功能，也不是修复 bug）
- `perf`：性能优化
- `test`：增加测试
- `chore`：构建过程或辅助工具的变动
- `revert`：回退
- `build`：打包

推荐示例：

```text
feat(agent): 增加了子任务聚合能力 (Add child-run aggregation)
docs(readme): 补充了提交信息格式说明 (Add commit message format notes)
fix(runtime): 修复了 continuation 丢失问题 (Fix continuation dispatch issue)
```

额外约定：

- 默认按职责拆分 commit，优先拆成“提示词与上下文 / 运行时治理 / 配置默认值与注释 / 测试”这类最小修改单元
- 尽量做到一个 commit 只解决一类问题，避免把无关改动混在一起

## AI 辅助开发说明

本项目允许使用 AI 辅助编写代码和文档。

但需要明确：

- AI 可以参与实现，不可以替代开发者责任
- 所有 AI 参与生成的代码，都必须由开发者人工阅读
- 所有待合并改动，都必须经过开发者人工测试和验证
- 不能因为使用了 AI，就跳过 Review、测试或关键链路回归

建议至少完成与风险等级匹配的人工验证：

- 本地编译
- 单元测试或集成测试
- 关键功能手工验证
- 配置与部署检查

结论很简单：

- 允许使用 AI 写代码
- 不允许未经开发者人工测试和验证直接合并

更完整的仓库协作说明请阅读：

- [AGENTS.md](./AGENTS.md)

## 参考入口

- [src/main/java/com/jimuqu/claw/SolonClawApp.java](./src/main/java/com/jimuqu/claw/SolonClawApp.java)
- [src/main/java/com/jimuqu/claw/config/SolonClawConfig.java](./src/main/java/com/jimuqu/claw/config/SolonClawConfig.java)
- [src/main/java/com/jimuqu/claw/agent/runtime/AgentRuntimeService.java](./src/main/java/com/jimuqu/claw/agent/runtime/AgentRuntimeService.java)
- [src/main/java/com/jimuqu/claw/agent/store/RuntimeStoreService.java](./src/main/java/com/jimuqu/claw/agent/store/RuntimeStoreService.java)
- [src/main/java/com/jimuqu/claw/agent/workspace/WorkspacePromptService.java](./src/main/java/com/jimuqu/claw/agent/workspace/WorkspacePromptService.java)
- [src/main/java/com/jimuqu/claw/agent/job/WorkspaceJobService.java](./src/main/java/com/jimuqu/claw/agent/job/WorkspaceJobService.java)
- [src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkChannelAdapter.java](./src/main/java/com/jimuqu/claw/channel/dingtalk/DingTalkChannelAdapter.java)
- [src/main/resources/static/index.html](./src/main/resources/static/index.html)

## License

当前仓库未单独声明许可证时，请以仓库实际发布方式和作者说明为准。
