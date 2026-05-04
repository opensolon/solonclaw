package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.noear.solon.ai.chat.message.AssistantMessage;

/** 大模型调用结果封装。 */
@Getter
@Setter
@NoArgsConstructor
public class LlmResult {
    /** Solon AI 的助手消息对象。 */
    private AssistantMessage assistantMessage;

    /** 追加后的会话 NDJSON。 */
    private String ndjson;

    /** 是否通过流式模式生成。 */
    private boolean streamed;

    /** 原始协议响应。 */
    private String rawResponse;

    /** 本轮 ReAct/模型调用累计输入 token。 */
    private long inputTokens;

    /** 本轮 ReAct/模型调用累计输出 token。 */
    private long outputTokens;

    /** 本轮累计 reasoning token。 */
    private long reasoningTokens;

    /** 本轮可展示的 reasoning 文本。 */
    private String reasoningText;

    /** 本轮累计 cache read token。 */
    private long cacheReadTokens;

    /** 本轮累计 cache write token。 */
    private long cacheWriteTokens;

    /** 本轮 ReAct/模型调用累计总 token。 */
    private long totalTokens;

    /** 本轮最终实际使用的 provider。 */
    private String provider;

    /** 本轮最终实际使用的 model。 */
    private String model;
}
