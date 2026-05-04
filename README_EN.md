# solon-claw

English | [简体中文](README.md)

solon-claw is a single-instance Agent service built with Java, Solon, and Solon AI. The project aims to reproduce the core behavior and capabilities of Hermes Agent in the Java / Solon ecosystem, with a focus on the Agent loop, tool calling, sessions, memory, skills, scheduled tasks, Chinese messaging channels, and a dashboard-first setup and diagnostics experience.

> The project is under active development. APIs and configuration keys may change as the implementation evolves. Feedback and contributions are welcome.

## Features

- **Agent core loop**: multi-turn sessions, streaming/non-streaming model calls, tool calls, context compression, retry, rollback, and session search.
- **Model protocols**: supports common interfaces such as `openai`, `openai-responses`, `ollama`, `gemini`, and `anthropic`.
- **Tool system**: built-in tools for file operations, search, patching, Shell/Python/JavaScript execution, Memory, scheduled jobs, web search/fetch, and message delivery.
- **Chinese messaging channels**: focuses on Feishu, DingTalk, WeCom, and Weixin; websocket / stream first, with Weixin iLink long-poll retained.
- **Dashboard-first operations**: status, sessions, configuration, channel doctor, runtime settings, logs, skills, and scheduled jobs.
- **Persistence**: SQLite-backed storage for sessions, policies, scheduled jobs, and channel states.
- **Skills and memory**: local skills, Skills Hub imports, long-term memory, user context, and context file collaboration.
- **Deployment**: supports `java -jar` and Docker / Docker Compose single-instance deployments.

## Tech Stack

- Java source compatibility: 1.8
- Build: Maven, Node.js/npm for the Dashboard frontend
- Web framework: Solon
- AI orchestration: Solon AI, Solon AI Agent, Solon AI Skills
- JSON: Snack4
- Utilities: Hutool
- Database: SQLite
- Frontend: Vue / Vite
- Container: Docker, Docker Compose

## Quick Start

### Requirements

- JDK 8+ (JDK 17 recommended)
- Maven 3.9+
- Node.js 20+ and npm
- Network access to your target LLM provider

### Clone and Build

```bash
git clone https://github.com/chengliang4810/solon-claw.git
cd solon-claw
mvn -DskipTests package
```

Maven runs `npm install` and `npm run build` in the `web` directory during `generate-resources` by default. To build only the backend:

```bash
mvn -DskipTests -Dskip.web.build=true package
```

### Run

```bash
java -jar target/solon-claw-0.0.1.jar
```

The default endpoint is:

```text
http://127.0.0.1:8080
```

On startup, the service creates a local `runtime/` directory for configuration, SQLite data, cache, logs, skills, and context files. Runtime children are derived by the program: `context/`, `skills/`, `cache/`, `logs/`, and `data/state.db`.

### Docker Compose

```bash
docker compose up -d
```

The default Compose file mounts local `./runtime` to `/app/runtime` inside the container for persistent runtime data. The image runs the service as the non-root `solonclaw` user by default, with UID/GID `10000:10000`. If you are migrating from an older image or a root-run container and see `/app/runtime is not writable`, run this from the server project directory:

```bash
sudo mkdir -p runtime
sudo chown -R 10000:10000 runtime
sudo chmod -R u+rwX runtime
docker compose up -d
```

To align the container user with the current host user instead, set:

```bash
export SOLONCLAW_UID="$(id -u)"
export SOLONCLAW_GID="$(id -g)"
docker compose up -d
```

## Configuration

Default configuration lives in:

```text
src/main/resources/app.yml
```

Model providers are managed by the runtime configuration file and the Dashboard. The runtime file is created at:

```text
runtime/config.yml
```

`runtime/config.yml` does not configure its own directory. The runtime directory is decided by startup-level configuration and defaults to `runtime/` under the current working directory.

Recommended model configuration structure:

```yaml
providers:
  default:
    name: DefaultProvider
    baseUrl: https://api.openai.com
    apiKey: ""
    defaultModel: gpt-5.4
    dialect: openai
model:
  providerKey: default
  default: "gpt-5.4"
fallbackProviders: []
solonclaw:
  dashboard:
    accessToken: "admin"
```

Common runtime settings:

| Key | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP server port |
| `providers.<key>.baseUrl` | - | Model service base URL |
| `providers.<key>.apiKey` | - | Model service API key |
| `providers.<key>.defaultModel` | - | Default model for the provider |
| `providers.<key>.dialect` | `openai` | Protocol dialect |
| `model.providerKey` | `default` | Active default provider |
| `model.default` | empty | Global model override; when empty, provider `defaultModel` is used |
| `solonclaw.llm.stream` | `true` | Enables streaming output |
| `solonclaw.llm.reasoningEffort` | `medium` | Default reasoning effort |
| `solonclaw.scheduler.enabled` | `true` | Enables scheduled jobs |

Prefer the Dashboard for provider and default-model management, or edit `runtime/config.yml` directly. Keep secrets out of Git.

## Messaging Channels

Supported and prioritized channels:

| Channel | Prefix | Inbound mode | Status |
| --- | --- | --- | --- |
| Feishu | `solonclaw.channels.feishu.*` | websocket / platform capabilities | In progress |
| DingTalk | `solonclaw.channels.dingtalk.*` | stream mode | In progress |
| WeCom | `solonclaw.channels.wecom.*` | websocket / platform capabilities | In progress |
| Weixin | `solonclaw.channels.weixin.*` | iLink long-poll | In progress |
| QQBot | `solonclaw.channels.qqbot.*` | websocket / REST | In progress |
| Yuanbao | `solonclaw.channels.yuanbao.*` | websocket / REST | In progress |

The Dashboard includes channel status and doctor endpoints. Prefer the Dashboard for setup, diagnostics, and troubleshooting.

## Slash Commands

Common in-conversation commands:

- `/new`: start a new session
- `/retry`: retry the previous turn
- `/undo`: undo the previous turn
- `/branch`: branch from the current session
- `/resume`: resume a session
- `/status`: show runtime status
- `/usage`: show token usage
- `/model`: inspect or switch models
- `/tools`: inspect tool state
- `/skills`: manage skills
- `/cron`: manage scheduled jobs
- `/pairing`: channel user pairing and approvals
- `/approve` / `/deny`: dangerous command approval

## API Overview

Main HTTP endpoints:

- `GET /api/status`: runtime status
- `POST /api/gateway/message`: signed gateway message injection
- `GET /api/gateway/doctor`: channel diagnostics
- `GET /api/sessions`: session list
- `POST /api/chat/runs`: Dashboard chat run
- `GET /api/config`: read configuration
- `GET /api/runtime-config`: runtime settings

Dashboard APIs require a session token by default. Gateway injection uses HMAC signature headers.

## Project Layout

```text
src/main/java/com/jimuqu/solon/claw/
├── agent/          # Agent profiles
├── bootstrap/      # Solon startup, bean wiring, HTTP controllers
├── config/         # Config-file loading, runtime overrides, path normalization
├── context/        # AGENTS / MEMORY / USER / Skills context
├── core/           # Domain models, repository interfaces, service interfaces
├── engine/         # Agent loop, compression, delegation
├── gateway/        # Messaging channels, auth, delivery, runtime refresh
├── llm/            # Model protocol adapters and Solon AI integration
├── scheduler/      # Cron and heartbeat scheduling
├── skillhub/       # Skills Hub, imports, guardrails, sources
├── storage/        # SQLite repository implementations
├── support/        # Runtime support utilities
├── tool/           # Built-in tool registry and implementations
└── web/            # Dashboard backend services and controllers
```

## Testing

Run the full Maven test lifecycle, including the frontend-bound build steps:

```bash
mvn test
```

Compile only the backend:

```bash
mvn -DskipTests -Dskip.web.build=true compile
```

Run selected tests:

```bash
mvn "-Dtest=DashboardControllerHttpTest" test
```

> In Windows PowerShell, quote `-Dtest=...` when using comma-separated test names.

## Contributing

Issues and pull requests are welcome. Before contributing, please check existing issues, run relevant tests, and describe the motivation, main implementation details, and verification scope in your pull request.

## License

This project is licensed under the [MIT License](LICENSE).
