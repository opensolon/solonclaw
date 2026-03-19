# SolonClaw

[中文](./README.md)

> Pinned Note  
> The architectural ideas behind this project were learned from and inspired by the open-source project [HKUDS/nanobot](https://github.com/HKUDS/nanobot).  
> `SolonClaw` is not a direct port of nanobot. It is a localized implementation built around `Solon + Solon AI + file-based workspace + multi-channel runtime`. Always treat the current repository code and tests as the source of truth.

`SolonClaw` is a lightweight Agent service built on `Solon 3.9.5`. It unifies model execution, conversation history, child-task orchestration, workspace tools, scheduled jobs, a local debug UI, and DingTalk integration under one runtime.

## Highlights

- 🤖 Unified Agent runtime
- 💬 Shared runtime for Debug Web and DingTalk
- 🧠 Workspace-driven prompt assembly and memory files
- 🛠️ Built-in tools for file IO, notifications, job management, and CLI terminal skills
- 🧩 Child task spawning with continuation back to the parent conversation
- ⏰ Persistent scheduled jobs restored on startup
- 📁 File-based runtime storage for runs, conversations, dedup, routes, and media

## Architecture

```text
Inbound Message
  -> ChannelAdapter / Debug Web / System Job
  -> AgentRuntimeService
  -> RuntimeStoreService
  -> ConversationScheduler (per sessionKey)
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

Main modules:

- `agent/runtime`
- `agent/store`
- `agent/workspace`
- `agent/tool`
- `agent/job`
- `channel/dingtalk`
- `web`

## Current Capabilities

Built-in tools currently include:

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

Behavioral notes:

- File access is restricted to the configured workspace.
- Command execution now goes through CLI `TerminalSkill` via `bash`.
- `TerminalSkill` sandbox mode is enabled by default and can be controlled with `solonclaw.agent.tools.sandboxMode`.
- Child runs use independent session keys and can be aggregated by `batchKey`.
- Scheduled jobs are bound to the latest external reply route.
- Heartbeat checks read `HEARTBEAT.md` and trigger a silent internal run.

`sandboxMode` rules:

- `true`: CLI abilities such as `bash`, `ls`, `read`, `grep`, and `glob` only allow workspace-relative paths, `~/`, and logical pool paths like `@skills`
- `true`: absolute paths are blocked, and relative traversal outside the workspace is blocked
- `true`: logical pool paths such as `@skills` remain readable/executable but are still read-only
- `false`: absolute-path access is allowed and the CLI becomes more open

## Workspace Layout

Default workspace root:

- `./workspace`

Typical structure:

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

## Implemented Channels

### Debug Web

Available debug endpoints:

- `POST /api/debug/chat`
- `GET /api/debug/runs/{runId}`
- `GET /api/debug/runs/{runId}/events`
- `GET /api/debug/runs/{runId}/children`

### DingTalk

Current DingTalk integration:

- Inbound: `DingTalkStreamTopics.BOT_MESSAGE_TOPIC`
- Outbound: official bot OpenAPI
- Group send: `orgGroupSend`
- Private send: `batchSendOTO`
- Reply format: markdown text

Current behavior:

- Group and private chats use isolated session keys.
- Replies always rely on `ReplyTarget`.
- Attachments are currently degraded into text-only fallback.
- Empty allowlists mean allow by default; once configured, only matched entries are accepted.

## Quick Start

Requirements:

- JDK `8`
- Maven `3.9+`
- Ollama is recommended for local development

Compile and test:

```bash
mvn -q -DskipTests compile
mvn -q test
```

Run:

```bash
java -jar target/solonclaw.jar
```

Development mode:

```bash
java -jar target/solonclaw.jar --env=dev
```

Default port:

- `12345`

Open the local debug page:

- [http://localhost:12345](http://localhost:12345)

## Copy-Paste Startup Commands

If you want ready-to-run commands that first set variables and then launch the jar, use one of the examples below.

PowerShell:

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

Bash:

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

Production example:

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

## Configuration

Main config:

- `src/main/resources/app.yml`

Dev config example:

- `src/main/resources/app-dev.yml`

External config example:

- `scripts/config.example.yml`

Current important settings:

- `solonclaw.workspace=./workspace`
- `solonclaw.agent.scheduler.maxConcurrentPerConversation=4`
- `solonclaw.agent.scheduler.ackWhenBusy=false`
- `solonclaw.agent.tools.sandboxMode=true`
- `solonclaw.agent.heartbeat.enabled=true`
- `solonclaw.agent.heartbeat.intervalSeconds=1800`
- `solonclaw.channels.dingtalk.*`

## Docker Deployment

The repository now includes:

- `Dockerfile`
- `docker-compose.yml`
- `.dockerignore`

### Build the image

```bash
docker build -t solonclaw:latest .
```

### Prepare host files

Create these in the project root:

- `config.yml`
- `workspace/`

Use `config.yml` for production secrets, model config, and DingTalk config. Use `workspace/` for runtime data, memory files, skills, and persisted jobs.

You can start from:

- `scripts/config.example.yml`

### Run with `docker run`

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

PowerShell:

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

Notes:

- The container working directory is `/app`.
- `APP_ARGS="--env=prod"` activates the `prod` profile and loads `/app/config.yml`.
- Mounting `workspace/` keeps runtime data outside the container lifecycle.

### Run with Docker Compose

```bash
docker compose up -d --build
```

Stop:

```bash
docker compose down
```

Logs:

```bash
docker compose logs -f solonclaw
```

The provided compose file already:

- exposes port `12345`
- mounts `./workspace` to `/app/workspace`
- mounts `./config.yml` to `/app/config.yml`
- starts the app with `--env=prod`

### Customize runtime options

You can modify these environment variables in `docker-compose.yml`:

- `JAVA_OPTS`
- `APP_ARGS`

Example:

```yaml
environment:
  JAVA_OPTS: "-Xms512m -Xmx1024m"
  APP_ARGS: "--env=prod"
```

### Deployment recommendations

- Always mount a persistent `workspace` directory in production.
- Do not bake real secrets into the image.
- If you use Ollama outside the container, make sure the container can reach that endpoint.
- If you use DingTalk, provide `clientId`, `clientSecret`, and `robotCode` in `config.yml`.

## Tests

The current test suite covers:

- Solon startup and `ChatModel` wiring
- Workspace template bootstrap and prompt assembly
- Workspace tool path boundaries
- Runtime file persistence
- Per-conversation concurrency and busy acknowledgements
- Child run spawning, continuation, aggregation, and batch filtering
- Proactive notifications
- Silent heartbeat execution
- DingTalk inbound mapping and markdown payload generation
- Persistent job storage

## Collaboration Rules

- Add new channels through `ChannelAdapter` and `ChannelRegistry`
- Never guess reply routes outside `ReplyTarget`
- Maintain conversation history only through `RuntimeStoreService`
- Prefer Solon lifecycle hooks for long-running resources
- Add new project config under `SolonClawProperties`
- Reuse the existing Debug Web entrypoint for local debugging

## PR Guidelines

Every Pull Request is recommended to include:

- `Background`
- `Changes`
- `Impact`
- `Validation`
- `Risk and Rollback`

Recommended template:

```md
## Background
- Why this change is needed

## Changes
- What was changed in this PR

## Impact
- Which modules, APIs, configs, or deployment paths are affected

## Validation
- What tests were run
- What manual verification was performed

## Risk and Rollback
- What could go wrong
- How to roll back if needed
```

Additional expectations:

- Keep one PR focused on one kind of change whenever possible.
- PR titles and commit messages are encouraged to use bilingual Chinese/English wording in this repository.
- If behavior or configuration changes, update the related docs in the same PR.

## Commit Guidelines

Commit messages should follow the Conventional Commits style:

```text
<type>(<scope>): <subject>
```

Notes:

- There must be one space after the colon `:`
- `scope` is optional and describes the affected area, such as a module name, directory name, or a responsibility like `runtime`, `workspace`, or `web`
- `subject` is required and should briefly describe the change; bilingual Chinese/English wording is recommended in this repository

`type` is required and may be one of:

- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation changes
- `style`: formatting-only changes that do not affect runtime behavior
- `refactor`: refactoring or optimization that is neither a feature nor a bug fix
- `perf`: performance optimization
- `test`: test additions or adjustments
- `chore`: build process or auxiliary tooling changes
- `revert`: revert
- `build`: packaging/build changes

Recommended examples:

```text
feat(agent): 增加了子任务聚合能力 (Add child-run aggregation)
docs(readme): 补充了提交信息格式说明 (Add commit message format notes)
fix(runtime): 修复了 continuation 丢失问题 (Fix continuation dispatch issue)
```

Additional conventions:

- Split commits by responsibility whenever possible, preferably into small units such as prompt/context, runtime governance, config defaults/comments, and tests
- Try to keep each commit focused on one kind of change instead of mixing unrelated work

## AI-Assisted Development

AI-assisted code and documentation authoring is allowed in this project.

But the following rules apply:

- AI can assist implementation, but it does not replace developer responsibility.
- All AI-generated or AI-assisted code must be reviewed by a human developer.
- All changes pending merge must be manually tested and verified by a developer.
- Using AI is never a reason to skip review, testing, or regression checks on critical flows.

Recommended human verification should match the risk level of the change, such as:

- local compilation
- unit or integration tests
- manual verification of critical behavior
- configuration and deployment checks

In short:

- AI-written code is allowed
- merging without developer manual testing and verification is not allowed

For the repository-specific collaboration guide, read:

- [AGENTS.md](./AGENTS.md)
