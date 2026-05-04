package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.session.InMemoryChatSession;

public class AttachmentAwareConversationTest {
    @Test
    void shouldInjectAttachmentSummaryIntoUserTurnAndLlmInput() throws Exception {
        CapturingLlmGateway llmGateway = new CapturingLlmGateway();
        TestEnvironment env = TestEnvironment.withLlm(llmGateway);
        env.appConfig.getGateway().setAllowAllUsers(true);

        GatewayMessage message =
                new GatewayMessage(PlatformType.MEMORY, "room-1", "user-1", "请处理这些附件");
        message.setAttachments(new ArrayList<MessageAttachment>());
        message.getAttachments()
                .add(
                        attachment(
                                "image",
                                "diagram.png",
                                "image/png",
                                "D:\\temp\\diagram.png",
                                false,
                                null));
        message.getAttachments()
                .add(
                        attachment(
                                "file",
                                "report.pdf",
                                "application/pdf",
                                "D:\\temp\\report.pdf",
                                true,
                                null));
        message.getAttachments()
                .add(
                        attachment(
                                "voice",
                                "note.silk",
                                "audio/silk",
                                "D:\\temp\\note.silk",
                                false,
                                "客户说先别发版"));

        env.conversationOrchestrator.handleIncoming(message);

        assertThat(llmGateway.lastUserMessage).contains("请处理这些附件");
        assertThat(llmGateway.lastUserMessage).contains("[attachments]");
        assertThat(llmGateway.lastUserMessage).contains("kind=image");
        assertThat(llmGateway.lastUserMessage).contains("originalName=diagram.png");
        assertThat(llmGateway.lastUserMessage).contains("fromQuote=true");
        assertThat(llmGateway.lastUserMessage).contains("transcribedText=客户说先别发版");

        SessionRecord session = env.sessionRepository.getBoundSession(message.sourceKey());
        assertThat(MessageSupport.getLastUserMessage(session.getNdjson()))
                .contains("[attachments]");
        assertThat(MessageSupport.getLastUserMessage(session.getNdjson())).contains("report.pdf");
    }

    private MessageAttachment attachment(
            String kind,
            String originalName,
            String mimeType,
            String localPath,
            boolean fromQuote,
            String transcribedText) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setKind(kind);
        attachment.setOriginalName(originalName);
        attachment.setMimeType(mimeType);
        attachment.setLocalPath(localPath);
        attachment.setFromQuote(fromQuote);
        attachment.setTranscribedText(transcribedText);
        return attachment;
    }

    private static class CapturingLlmGateway implements LlmGateway {
        private String lastUserMessage;

        @Override
        public LlmResult chat(
                SessionRecord session,
                String systemPrompt,
                String userMessage,
                List<Object> toolObjects)
                throws Exception {
            lastUserMessage = userMessage;
            InMemoryChatSession chatSession = new InMemoryChatSession(session.getSessionId());
            if (StrUtil.isNotBlank(session.getNdjson())) {
                chatSession.loadNdjson(session.getNdjson());
            }
            chatSession.addMessage(ChatMessage.ofUser(userMessage));
            chatSession.addMessage(ChatMessage.ofAssistant("ok"));

            LlmResult result = new LlmResult();
            result.setAssistantMessage(ChatMessage.ofAssistant("ok"));
            result.setNdjson(chatSession.toNdjson());
            result.setRawResponse("captured");
            result.setStreamed(false);
            return result;
        }

        @Override
        public LlmResult resume(
                SessionRecord session, String systemPrompt, List<Object> toolObjects)
                throws Exception {
            return chat(session, systemPrompt, "", toolObjects);
        }
    }
}
