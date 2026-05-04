package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

public class MaxStepsRecoveryTest {
    @Test
    void shouldRecoverWithSummaryWhenReactStepBudgetIsExhausted() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        LlmGateway llmGateway =
                new LlmGateway() {
                    @Override
                    public LlmResult chat(
                            SessionRecord session,
                            String systemPrompt,
                            String userMessage,
                            List<Object> toolObjects) {
                        int current = calls.incrementAndGet();
                        LlmResult result = new LlmResult();
                        if (current == 1) {
                            result.setAssistantMessage(
                                    ChatMessage.ofAssistant(
                                            "Agent error: Maximum steps reached (12)."));
                            result.setNdjson(session.getNdjson());
                            result.setInputTokens(100);
                            result.setOutputTokens(20);
                            result.setTotalTokens(120);
                            return result;
                        }

                        assertThat(toolObjects).isEmpty();
                        assertThat(userMessage).contains("最大推理步数限制");

                        result.setAssistantMessage(
                                ChatMessage.ofAssistant("已完成主要排查：定位到工具链循环；当前还需要缩小目标范围或提高步数预算后继续。"));
                        result.setNdjson(session.getNdjson());
                        result.setInputTokens(30);
                        result.setOutputTokens(40);
                        result.setTotalTokens(70);
                        return result;
                    }

                    @Override
                    public LlmResult resume(
                            SessionRecord session, String systemPrompt, List<Object> toolObjects) {
                        return chat(session, systemPrompt, "", toolObjects);
                    }
                };

        TestEnvironment env = TestEnvironment.withLlm(llmGateway);
        env.appConfig.getGateway().setAllowAllUsers(true);

        String sourceKey = "MEMORY:room-1:user-1";
        SessionRecord session = env.sessionRepository.bindNewSession(sourceKey);
        session.setNdjson("");
        env.sessionRepository.save(session);

        String reply =
                env.conversationOrchestrator
                        .handleIncoming(env.message("room-1", "user-1", "处理一个复杂任务"))
                        .getContent();

        SessionRecord updated = env.sessionRepository.getBoundSession(sourceKey);
        assertThat(reply).contains("已完成主要排查");
        assertThat(reply).doesNotContain("Maximum steps reached");
        assertThat(updated.getLastTotalTokens()).isEqualTo(190);
        assertThat(calls.get()).isEqualTo(2);
    }
}
