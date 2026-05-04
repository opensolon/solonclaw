# solon-claw AGENTS.md

## 项目目标

- 本项目目标是使用 Java 语言，基于 Solon 与 Solon AI，对 `D:\projects\hermes-agent` 进行功能复刻。
- 复刻的目标是“行为与能力对齐”，不是逐行或逐模块照搬 Python 实现；允许使用更符合 Java / Solon 生态的实现方式。
- 后续需求、设计、拆解、编码、测试都必须优先服务这个目标，避免偏离成泛用聊天应用、泛工作流平台或无关的 AI Demo。

## 强约束

### 1. 语言与框架

- 主实现语言只能是 Java。
- 核心框架只能优先使用 Solon。
- AI 能力接入、Agent 编排、模型协议封装优先使用 Solon AI。
- 通用工具优先使用 Hutool。
- JSON 序列化与反序列化统一优先使用 `org.noear:snack4`。
- 若 Solon / Solon AI 官方已提供对应模块、skill、dialect、plugin 或适配实现，必须优先直接采用；只有官方能力明确缺失且无法满足目标行为时，才允许自研补充。

### 2. 依赖选型原则

- 没有明确理由时，不要引入 Spring Boot、Spring AI、LangChain4j、Jackson、Fastjson、Gson 等替代性主框架或主序列化方案。
- 如某个外部协议或 SDK 必须依赖额外库，先保证边界清晰，再最小化引入，并在任务说明或变更说明中写明原因。
- 优先使用 Maven Central 中可获取的稳定开源依赖。

### 3. 参考源码目录

- Solon 源码：`D:\projects\solon-main`
- Solon AI 源码：`D:\projects\solon-ai-main`
- Hutool 源码：`D:\projects\hutool-v5-master`
- Hermes Agent 参考源码：`D:\projects\hermes-agent`

遇到框架用法、设计取舍、扩展点不明确时，先查本地源码目录，再决定实现方案。

## 上游参考与同步策略

- `D:\projects\hermes-agent` 是功能对标基线。
- 实现新能力前，先定位 Hermes 对应模块、配置项、交互方式、约束和用户可见行为。
- 若任务依赖 Hermes 的最新变动，先同步参考仓库，再继续实现。建议命令：

```powershell
git -C D:\projects\hermes-agent fetch origin
git -C D:\projects\hermes-agent log origin/main -1 --date=iso
git -C D:\projects\hermes-agent status --short --branch
```

- 若本地参考仓库与上游存在明显差异，优先说明本次实现参考的是哪个提交或本地状态。

## 复刻范围约束

### 1. 渠道范围

- 仅保留中国国内消息渠道支持。
- 海外或非目标渠道默认不做，包括但不限于：Telegram、Discord、Slack、WhatsApp、Signal、Matrix、Mattermost、BlueBubbles、Home Assistant、Email。
- Hermes 当前代码中与国内场景相关、可作为参考候选的渠道适配器，仅保留：`feishu`、`dingtalk`、`wecom`、`weixin`、`qqbot`、`yuanbao`。
- 明确不做：`sms`、`webhook`。
- 在你确认最终渠道清单之前，后续任务只允许建设“国内渠道抽象和适配能力”，不要投入任何海外渠道实现。

### 2. 大模型协议范围

- 只保留以下通用协议或兼容接入面：
- `openai`
- `openai-responses`
- `ollama`
- `gemini`
- `anthropic`

- 以下提供方或专有接入默认不做，除非你后续明确要求：OpenRouter、z.ai/GLM、Kimi/Moonshot、MiniMax、Copilot、Nous Portal、Hugging Face、Vercel AI Gateway 等。

### 3. 设计边界

- 复刻重点是 Agent 核心能力、国内渠道接入、模型协议适配、工具系统、记忆/技能/会话等与 Hermes 主产品价值直接相关的部分。
- 渠道接入与诊断入口默认走现有 dashboard/API，优先补齐 dashboard-first setup / doctor，而不是新增完整 CLI 向导。
- 渠道传输层遵循 websocket-first：平台官方支持 websocket / stream 时优先采用；仅微信保留 Hermes 原有 iLink long-poll。
- 不要因为某个技术点实现方便，就偏离成以脚本、前端展示页、营销官网、实验性研究代码为中心的项目。
- 若出现“为了兼容 Hermes 原实现而牺牲 Java/Solon 可维护性”的情况，优先保持 Java 侧架构清晰，再在行为层面对齐。
- 已明确不做：多模态模型输入、图像生成、独立 TTS/语音转写服务、浏览器自动化内置实现、价格分析/价格计算、研究与实验能力、完整 CLI/TUI 交互层。
- 浏览器自动化能力不进入内置主线；如后续需要，按“用户自行安装 skill 扩展”的方式处理。

## 默认实现原则

### 1. 实现顺序

优先级建议如下：

1. 项目骨架、配置体系、公共模型与异常定义
2. 模型协议抽象与会话/Agent 主循环
3. 工具注册与调用框架
4. 国内消息渠道网关
5. 记忆、技能、上下文文件、会话存储
6. 定时任务、自动化、MCP/ACP 等扩展能力
7. 低优先级或研究型能力

### 2. 任务判断原则

- 每次开发前，先回答“这个任务对应 Hermes 的哪项能力”。
- 如果对应不上 Hermes 的明确能力，或与本项目目标无关，应先暂停并确认，而不是直接扩展范围。
- 没有用户明确要求时，优先实现最小可用版本，再逐步补齐兼容能力。

### 3. 架构原则

- 先定义清晰的领域边界，再落地代码。
- 配置、协议、渠道、工具、Agent 核心循环要解耦。
- 避免把渠道逻辑、模型协议逻辑、工具执行逻辑直接耦合在一个类中。
- 面向接口或抽象层设计，方便后续逐步补齐 Hermes 功能。

## 待确认的 Hermes 功能清单

以下是 Hermes Agent 现有功能面，后续需要逐项确认哪些保留、哪些裁剪。未确认前，可以做架构预留，但不要默认全部深度实现。

### A. Agent 核心与会话

- 多轮会话与会话持久化
- 上下文压缩与历史裁剪
- `/new`、`/retry`、`/undo`、`/branch`、`/resume` 等会话控制
- 模型切换、推理强度、快速模式
- 会话状态与基础运行信息
- 保留 token 使用量统计；不做价格分析/价格计算
- checkpoint / rollback

### B. CLI / 交互层

- 保留对话内 slash commands 的命令语义，例如：`/new`、`/retry`、`/undo`
- 不做完整交互式 CLI
- 不做富文本/TUI 展示
- 不做皮肤、主题、展示样式
- 不做语音模式入口

### C. 消息网关与渠道适配

- 网关进程
- 渠道鉴权与会话绑定
- 文本消息收发
- 附件能力按最小可用原则处理；保留国内渠道图片/文件/视频/语音的传输、缓存与附件感知主链，但默认不做图片理解、语音转写等内容理解型能力
- dashboard-first setup / doctor
- 优先 websocket / stream，微信保留 long-poll
- home channel / 状态同步 / 跨端连续会话
- 计划任务向渠道投递结果
- 独立 `send_message` 能力
- 仅保留：`feishu`、`dingtalk`、`wecom`、`weixin`、`qqbot`、`yuanbao`
- 不做：`sms`、`webhook`

### D. 模型与协议层

- 流式输出
- 工具调用 / function calling
- 不做多模态模型输入
- Prompt caching
- 模型元数据、上下文长度、token 估算
- 智能模型路由
- 保留 token 估算与使用量统计；不做价格分析/价格计算

### E. 工具系统

- 终端命令执行
- 进程管理
- 文件读写、搜索、补丁
- 保留内置 Websearch / Webfetch
- 不做浏览器自动化内置实现
- 代码执行沙箱
- 子 Agent / delegation / mixture of agents
- 不做图像理解与图像生成
- 不做 TTS / 独立语音转写服务 / voice mode
- Todo / 计划工具
- Memory 工具
- 会话搜索
- 定时任务工具
- 发送消息工具
- 安全扫描与危险命令审批

### F. 技能、记忆、上下文

- Skills 本地管理
- Skills Hub / 手动导入兼容 / 在线 source 搜索安装
- 内置 Websearch / Webfetch
- token 使用量统计
- 任务后沉淀技能或改进技能
- MEMORY / USER / AGENTS 等上下文文件协同
- 跨会话检索与总结
- 用户画像 / 长期记忆
- Honcho 用户建模或兼容能力

### G. 集成与扩展

- MCP 集成
- 第一版不做 ACP / 编辑器集成
- 第一版不做 OpenAI 兼容 API Server
- 不做 Webhook
- 第一版不做插件系统
- 第一版不做 Profiles / 多配置隔离
- Setup / Doctor / Auth 流程保留 dashboard-first 版本；不补完整 CLI wizard

### H. 运行环境与部署

- 仅保留 `java -jar` 部署
- 仅保留 Docker 部署
- 不做 Docker 之外的复杂执行后端
- 不做工作区隔离 / worktree
- 安装脚本与部署脚本按最小可用原则建设

### I. 研究与实验能力

- 不做 Batch runner
- 不做 Trajectory 保存与压缩
- 不做 RL / Atropos 环境
- 不做训练辅助工具

## 建议默认低优先级或暂缓项

若你没有明确要求，以下内容不应抢占主线开发资源：

- 海外消息渠道及其运维能力
- 与限定协议无关的模型提供方适配
- 皮肤/主题/视觉层花活
- 官网、落地页、营销页面
- 研究型工具链
- 复杂多云执行后端
- 与主线复刻无关的迁移工具

## 当前已确认不做

- `sms`
- `webhook`
- 多模态模型输入
- 图像理解/生成
- TTS / 独立语音转写服务

- 浏览器自动化内置实现
- 价格分析/价格计算
- 完整 CLI / TUI 交互层
- 研究与实验能力
- Docker 之外的执行后端
- worktree
- 插件系统
- ACP / 编辑器集成
- OpenAI 兼容 API Server
- Profiles / 多配置隔离
- 多实例 / 多租户 / 多机器人隔离

## 当前已确认保留

- 对话内 slash commands 命令语义
- `feishu`、`dingtalk`、`wecom`、`weixin`、`qqbot`、`yuanbao`
- 国内渠道附件/媒体传输与附件感知主链
- dashboard-first setup / doctor
- websocket-first 国内渠道接入（微信除外）
- `java -jar` 部署
- Docker 部署
- 单实例架构
- Skills Hub / 手动导入兼容 / 在线 source 搜索安装
- 内置 Websearch / Webfetch
- token 使用量统计

## 后续任务执行要求

- 所有任务都应优先说明它对应 Hermes 的哪个能力点。
- 所有实现都应优先复用 Solon、Solon AI、Hutool、Snack4 的能力。
- 做方案设计时，要显式标注“已确认范围”和“待确认范围”。
- 新增依赖、新增协议、新增渠道、新增核心架构分层时，必须检查是否违反本文件约束。
- 若用户后续明确裁剪某些功能，应及时更新本文件，使后续任务持续对齐目标。

## ????

- ??????????????????????????????????????????????????? git commit????????????????
- ????????????????????????????????????????????
