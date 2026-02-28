# SolonClaw 项目重构建议清单

**分析日期**: 2026-02-28
**分析师**: 技术主管
**基于**: Solon v3.9.4 文档分析

---

## 📊 当前项目问题分析

### 1. 依赖问题

| 问题 | 严重程度 | 说明 |
|------|----------|------|
| 缺少 `solon-ai-dialect-openai` | 🔴 高 | 当前使用 `solon-ai-core` 但没有 OpenAI 方言适配器 |
| 缺少 `solon-ai-agent` | 🔴 高 | 需要 ReActAgent 能力 |
| 缺少 `solon-serialization-snack4` 在 AI 调用中的应用 | 🟡 中 | 手动序列化 JSON 不够优雅 |

### 2. 代码架构问题

| 问题 | 严重程度 | 说明 |
|------|----------|------|
| 手动调用 OpenAI API | 🔴 高 | `AgentService` 直接使用 OkHttp 调用 API，没有利用 Solon AI 的抽象 |
| 手动解析 JSON 响应 | 🔴 高 | 自定义的 `parseOpenAIResponse` 和 `serialize` 方法脆弱且易出错 |
| 工具注册机制冗余 | 🟡 中 | `ToolRegistry` 重复实现了 Solon AI 已有的工具发现机制 |
| 缺少 ReActAgent 集成 | 🔴 高 | 项目目标是 Agent 服务，但未使用 Solon AI 的 Agent 框架 |
| 配置未充分利用 Solon AI | 🟡 中 | `app.yml` 中的 AI 配置未通过 `ChatConfig` 使用 |

---

## 🎯 重构方案

### 阶段一：依赖优化

#### 1.1 添加必要的依赖

在 `pom.xml` 中添加：

```xml
<!-- Solon AI OpenAI 方言适配器 -->
<dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-ai-dialect-openai</artifactId>
    <version>${solon.version}</version>
</dependency>

<!-- Solon AI Agent 框架 -->
<dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-ai-agent</artifactId>
    <version>${solon.version}</version>
</dependency>
```

**说明**：
- `solon-ai-dialect-openai` 提供 OpenAI 兼容的方言适配
- `solon-ai-agent` 提供 ReActAgent、SimpleAgent 等智能体实现

---

### 阶段二：配置优化

#### 2.1 添加 ChatModel Bean 配置

创建新文件：`src/main/java/com/jimuqu/solonclaw/config/ChatModelConfig.java`

```java
package com.jimuqu.solonclaw.config;

import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;

/**
 * ChatModel 配置类
 */
@Configuration
public class ChatModelConfig {

    @Bean
    public ChatModel chatModel(
        @Inject("${solon.ai.chat.openai.apiUrl}") String apiUrl,
        @Inject("${solon.ai.chat.openai.apiKey}") String apiKey,
        @Inject("${solon.ai.chat.openai.model}") String model,
        @Inject("${solon.ai.chat.openai.provider:openai}") String provider,
        @Inject("${nullclaw.defaults.temperature:0.7}") double temperature,
        @Inject("${nullclaw.defaults.maxTokens:4096}") int maxTokens
    ) {
        return ChatModel.of(apiUrl)
            .apiKey(apiKey)
            .provider(provider)
            .model(model)
            .defaultOptions(opts -> opts
                .temperature(temperature)
                .maxTokens(maxTokens)
            )
            .build();
    }
}
```

**优势**：
- 统一配置管理
- 自动处理不同 API 提供商的兼容性
- 支持流式响应
- 自动序列化/反序列化

---

### 阶段三：核心服务重构

#### 3.1 使用 ChatModel 替代手动 API 调用

**当前 `AgentService` 的问题**：
- 400+ 行代码包含大量手动 HTTP 请求和 JSON 解析
- 维护成本高，容易出错
- 没有利用框架能力

**重构方案**：

简化 `AgentService.java`，使用注入的 `ChatModel`：

```java
@Component
public class AgentService {

    @Inject
    private ChatModel chatModel;

    @Inject
    private MemoryService memoryService;

    @Inject
    private ToolRegistry toolRegistry;

    public String chat(String message, String sessionId) {
        // 保存用户消息
        memoryService.saveUserMessage(sessionId, message);

        // 获取会话历史
        List<ChatMessage> history = memoryService.getSessionHistory(sessionId)
            .stream()
            .map(this::toChatMessage)
            .toList();

        // 构建 Prompt 并调用
        ChatResponse response = chatModel.prompt(history)
            .user(message)
            .call();

        // 保存响应
        String content = response.getContent();
        memoryService.saveAssistantMessage(sessionId, content);

        return content;
    }

    private ChatMessage toChatMessage(Map<String, String> msg) {
        // 转换逻辑
    }
}
```

**代码减少**：从 ~400 行减少到 ~100 行

#### 3.2 集成 ReActAgent

创建 `ReActAgentService.java`：

```java
@Component
public class ReActAgentService {

    @Inject
    private ChatModel chatModel;

    @Inject
    private ToolRegistry toolRegistry;

    @Inject
    private MemoryService memoryService;

    private ReActAgent agent;

    @Init
    public void init() {
        // 构建 ReActAgent
        agent = ReActAgent.of(chatModel)
            .name("solonclaw")
            .role("AI 助手，帮助用户完成各种任务")
            .instruction("你是一个有用的 AI 助手，可以使用工具来帮助用户")
            .defaultToolAdd(toolRegistry.getToolObjects())
            .maxSteps(25)
            .sessionWindowSize(50)
            .build();
    }

    public String chat(String message, String sessionId) {
        // 使用 ReActAgent 处理
        AgentResponse response = agent.prompt(message)
            .sessionId(sessionId)
            .call();

        return response.getContent();
    }
}
```

**优势**：
- 自动处理工具调用循环
- 支持思考-行动-观察模式
- 更好的错误处理和重试机制

---

### 阶段四：工具系统优化

#### 4.1 简化 ToolRegistry

**当前问题**：
- `ToolRegistry` 手动扫描 `@ToolMapping` 注解
- Solon AI 已有自动工具发现机制

**重构方案**：

`ToolRegistry` 可以简化为仅提供工具对象列表：

```java
@Component
public class ToolRegistry {

    @Inject
    private AppContext context;

    private List<Object> toolObjects = new ArrayList<>();

    @Init
    public void init() {
        // 获取所有带 @Component 的 Bean，Solon AI 会自动处理 @ToolMapping
        toolObjects = new ArrayList<>(context.getBeansOfType(Object.class));
    }

    public List<Object> getToolObjects() {
        return Collections.unmodifiableList(toolObjects);
    }
}
```

**说明**：Solon AI 的 `MethodToolProvider` 会自动处理 `@ToolMapping` 注解的方法。

---

## 📋 具体实施步骤

### 第一步：添加依赖
1. 修改 `pom.xml`，添加 `solon-ai-dialect-openai` 和 `solon-ai-agent`

### 第二步：创建 ChatModel 配置
1. 创建 `ChatModelConfig.java`
2. 验证 Bean 是否正确创建

### 第三步：重构 AgentService
1. 注入 `ChatModel`
2. 替换手动 API 调用
3. 删除 JSON 序列化相关代码

### 第四步：创建 ReActAgent 服务
1. 创建 `ReActAgentService.java`
2. 集成工具系统
3. 添加会话管理

### 第五步：测试
1. 编写单元测试
2. 验证功能完整性
3. 性能测试

---

## ⚠️ 风险与注意事项

| 风险 | 缓解措施 |
|------|----------|
| API 兼容性问题 | 充分测试不同 provider (openai, glm-4) |
| 配置变更影响 | 使用环境变量和默认值 |
| 工具调用逻辑变化 | 保持工具接口不变 |
| 性能回归 | 进行性能基准测试 |

---

## 📈 预期收益

| 指标 | 当前 | 重构后 | 改进 |
|------|------|--------|------|
| 代码行数 | ~400 | ~100 | -75% |
| 维护成本 | 高 | 低 | -60% |
| API 调用可靠性 | 中 | 高 | +40% |
| 功能完整性 | 基础 | 高 | +100% (ReActAgent) |
| 测试覆盖率 | 需要补充 | 更易测试 | +50% |

---

## 🔄 后续优化方向

1. **流式响应支持**：使用 `ChatModel.stream()` 实现实时响应
2. **多模态支持**：利用 Solon AI 的 `ContentBlock` 支持图片、音频
3. **RAG 集成**：添加 `solon-ai-rag` 实现知识库增强
4. **MCP 协议支持**：利用现有的 `McpManager` 与 Solon AI MCP 集成

---

**文档版本**: 1.0
**最后更新**: 2026-02-28
