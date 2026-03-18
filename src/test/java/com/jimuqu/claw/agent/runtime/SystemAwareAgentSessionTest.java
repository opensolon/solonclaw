package com.jimuqu.claw.agent.runtime;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 验证自定义 AgentSession 会保留系统消息。
 */
class SystemAwareAgentSessionTest {
    @Test
    void keepsSystemMessagesInHistoryWindow() {
        SystemAwareAgentSession session = SystemAwareAgentSession.of("session-a");

        ChatMessage system = ChatMessage.ofSystem("child task completed");
        ChatMessage user = ChatMessage.ofUser("继续处理");
        ChatMessage assistant = ChatMessage.ofAssistant("收到");
        session.addMessage(List.of(system, user, assistant));

        List<ChatMessage> history = session.getLatestMessages(10);

        assertEquals(3, history.size());
        assertSame(system, history.get(0));
        assertSame(user, history.get(1));
        assertSame(assistant, history.get(2));
    }
}
