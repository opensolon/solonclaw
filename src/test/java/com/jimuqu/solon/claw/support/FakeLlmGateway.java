package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import java.util.List;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.session.InMemoryChatSession;

public class FakeLlmGateway implements LlmGateway {
    public String lastSystemPrompt;

    @Override
    public LlmResult chat(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects)
            throws Exception {
        lastSystemPrompt = systemPrompt;
        InMemoryChatSession chatSession = new InMemoryChatSession(session.getSessionId());
        if (StrUtil.isNotBlank(session.getNdjson())) {
            chatSession.loadNdjson(session.getNdjson());
        }
        chatSession.addMessage(ChatMessage.ofUser(userMessage));
        chatSession.addMessage(ChatMessage.ofAssistant("echo:" + userMessage));

        LlmResult result = new LlmResult();
        result.setAssistantMessage(ChatMessage.ofAssistant("echo:" + userMessage));
        result.setNdjson(chatSession.toNdjson());
        result.setRawResponse("fake");
        result.setStreamed(false);
        result.setProvider("openai-responses");
        result.setModel("gpt-5.4");
        result.setInputTokens(Math.max(1, userMessage == null ? 0 : userMessage.length()));
        result.setOutputTokens(Math.max(1, ("echo:" + userMessage).length()));
        result.setCacheReadTokens(2L);
        result.setCacheWriteTokens(1L);
        result.setTotalTokens(
                result.getInputTokens()
                        + result.getOutputTokens()
                        + result.getCacheReadTokens()
                        + result.getCacheWriteTokens());
        return result;
    }

    @Override
    public LlmResult resume(SessionRecord session, String systemPrompt, List<Object> toolObjects)
            throws Exception {
        lastSystemPrompt = systemPrompt;
        InMemoryChatSession chatSession = new InMemoryChatSession(session.getSessionId());
        if (StrUtil.isNotBlank(session.getNdjson())) {
            chatSession.loadNdjson(session.getNdjson());
        }
        chatSession.addMessage(ChatMessage.ofAssistant("echo:resume"));

        LlmResult result = new LlmResult();
        result.setAssistantMessage(ChatMessage.ofAssistant("echo:resume"));
        result.setNdjson(chatSession.toNdjson());
        result.setRawResponse("fake-resume");
        result.setStreamed(false);
        result.setProvider("openai-responses");
        result.setModel("gpt-5.4");
        result.setInputTokens(1L);
        result.setOutputTokens("echo:resume".length());
        result.setCacheReadTokens(1L);
        result.setTotalTokens(
                result.getInputTokens() + result.getOutputTokens() + result.getCacheReadTokens());
        return result;
    }
}
