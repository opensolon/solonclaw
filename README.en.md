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
- 🛠️ Built-in tools for file IO, command execution, notifications, and job management
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
- `exec_command`
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
- Commands are executed inside the workspace directory.
- Child runs use independent session keys and can be aggregated by `batchKey`.
- Scheduled jobs are bound to the latest external reply route.
- Heartbeat checks read `HEARTBEAT.md` and trigger a silent internal run.

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

- JDK `17`
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
- `solonclaw.agent.heartbeat.enabled=true`
- `solonclaw.agent.heartbeat.intervalSeconds=1800`
- `solonclaw.channels.dingtalk.*`

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

For the repository-specific collaboration guide, read:

- [AGENTS.md](./AGENTS.md)
