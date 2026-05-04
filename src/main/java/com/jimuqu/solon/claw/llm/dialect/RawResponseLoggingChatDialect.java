package com.jimuqu.solon.claw.llm.dialect;

import cn.hutool.core.util.StrUtil;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.ChatChoice;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatOptions;
import org.noear.solon.ai.chat.ChatResponseDefault;
import org.noear.solon.ai.chat.dialect.ChatDialect;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.message.ToolMessage;
import org.noear.solon.ai.chat.tool.ToolCallBuilder;
import org.noear.solon.net.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wraps any Solon AI chat dialect and logs the raw model response when parsing fails. */
public class RawResponseLoggingChatDialect implements ChatDialect {
    private static final Logger log = LoggerFactory.getLogger(RawResponseLoggingChatDialect.class);

    private final ChatDialect delegate;
    private final String dialectName;
    private final boolean parseResponsesReasoning;

    public RawResponseLoggingChatDialect(
            ChatDialect delegate, String dialectName, boolean parseResponsesReasoning) {
        this.delegate = delegate;
        this.dialectName = dialectName;
        this.parseResponsesReasoning = parseResponsesReasoning;
    }

    @Override
    public boolean isDefault() {
        return delegate.isDefault();
    }

    @Override
    public boolean matched(ChatConfig config) {
        return delegate.matched(config);
    }

    @Override
    public HttpUtils createHttpUtils(ChatConfig config, boolean isStream) {
        return delegate.createHttpUtils(config, isStream);
    }

    @Override
    public void prepareOutputSchemaInstruction(
            String outputSchema, StringBuilder instructionBuilder) {
        delegate.prepareOutputSchemaInstruction(outputSchema, instructionBuilder);
    }

    @Override
    public void prepareOutputFormatOptions(ChatOptions options) {
        delegate.prepareOutputFormatOptions(options);
    }

    @Override
    public String buildRequestJson(
            ChatConfig config,
            ChatOptions options,
            List<ChatMessage> messages,
            boolean isStream) {
        return delegate.buildRequestJson(config, options, messages, isStream);
    }

    @Override
    public ONode buildAssistantToolCallMessageNode(
            ChatResponseDefault resp, Map<String, ToolCallBuilder> toolCallBuilders) {
        return delegate.buildAssistantToolCallMessageNode(resp, toolCallBuilders);
    }

    @Override
    public AssistantMessage buildAssistantMessageByToolMessages(
            AssistantMessage toolCallMessage, List<ToolMessage> toolMessages) {
        return delegate.buildAssistantMessageByToolMessages(toolCallMessage, toolMessages);
    }

    @Override
    public boolean parseResponseJson(ChatConfig config, ChatResponseDefault resp, String respJson) {
        try {
            boolean parsed = false;
            if (parseResponsesReasoning) {
                parsed = parseReasoningStreamDelta(resp, respJson);
            }
            return delegate.parseResponseJson(config, resp, respJson) || parsed;
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to parse llm raw response: dialect={}, provider={}, model={}, apiUrl={}, stream={}, bodyLength={}, bodyHexHead={}, body={}",
                    dialectName,
                    StrUtil.blankToDefault(config.getProvider(), ""),
                    StrUtil.blankToDefault(config.getModel(), ""),
                    StrUtil.blankToDefault(config.getApiUrl(), ""),
                    resp != null && resp.isStream(),
                    respJson == null ? 0 : respJson.length(),
                    RawResponseLogSupport.hexHead(respJson),
                    RawResponseLogSupport.preview(respJson),
                    e);
            throw e;
        }
    }

    @Override
    public List<AssistantMessage> parseAssistantMessage(ChatResponseDefault resp, ONode oMessage) {
        return delegate.parseAssistantMessage(resp, oMessage);
    }

    public ChatDialect getDelegate() {
        return delegate;
    }

    public String getDialectName() {
        return dialectName;
    }

    private boolean parseReasoningStreamDelta(ChatResponseDefault resp, String json) {
        if (resp == null || !resp.isStream() || StrUtil.isBlank(json)) {
            return false;
        }
        boolean parsed = false;
        String[] lines = json.split("\n");
        for (String line : lines) {
            String candidate = StrUtil.trim(line);
            if (StrUtil.isBlank(candidate)) {
                continue;
            }
            if (candidate.startsWith("data:")) {
                candidate = StrUtil.trim(candidate.substring(5));
            } else if (candidate.startsWith("event:")) {
                continue;
            }
            if (StrUtil.isBlank(candidate) || "[DONE]".equals(candidate)) {
                continue;
            }
            try {
                ONode node = ONode.ofJson(candidate);
                String type = node.get("type").getString();
                if (StrUtil.isBlank(type) || !type.toLowerCase().contains("reasoning")) {
                    continue;
                }
                String delta =
                        firstText(
                                node.get("delta").getString(),
                                node.get("text").getString(),
                                node.get("summary_text").getString());
                ONode item = node.getOrNull("item");
                if (StrUtil.isBlank(delta) && item != null) {
                    delta =
                            firstText(
                                    item.get("text").getString(),
                                    item.get("summary_text").getString());
                }
                if (StrUtil.isBlank(delta)) {
                    continue;
                }
                resp.reasoningBuilder.append(delta);
                resp.addChoice(
                        new ChatChoice(0, new Date(), null, new AssistantMessage(delta, true)));
                parsed = true;
            } catch (Exception ignored) {
                // Let the wrapped dialect handle the event or report malformed JSON.
            }
        }
        return parsed;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
