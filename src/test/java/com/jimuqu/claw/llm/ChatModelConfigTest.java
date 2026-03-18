package com.jimuqu.claw.llm;

import com.jimuqu.claw.SolonClawApp;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.annotation.Inject;
import org.noear.solon.test.SolonTest;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证聊天模型配置是否能够正常装配和调用。
 */
@SolonTest(SolonClawApp.class)
public class ChatModelConfigTest {
    /** 注入的聊天模型实例。 */
    @Inject
    private ChatModel chatModel;

    /**
     * 验证聊天模型 Bean 已成功创建。
     */
    @Test
    public void testChatModelBeanCreation() {
        assertNotNull(chatModel, "ChatModel Bean 不应该为 null");
        System.out.println("✓ ChatModel Bean 创建成功");
    }

    /**
     * 验证模型可以完成一次基础对话。
     *
     * @throws Exception 模型调用异常
     */
    @Test
    public void testSimpleChat() throws Exception {
        Assumptions.assumeTrue(isOllamaReachable(), "本地 Ollama 不可用，跳过实际对话测试");

        String userMessage = "你好，请用一句话介绍你自己。";
        ChatResponse response = chatModel.prompt(userMessage).call();

        assertNotNull(response, "响应不应该为 null");
        assertNotNull(response.getMessage(), "响应消息不应该为 null");
        assertNotNull(response.getMessage().getContent(), "响应内容不应该为 null");
        assertFalse(response.getMessage().getContent().isEmpty(), "响应内容不应该为空");

        System.out.println("用户: " + userMessage);
        System.out.println("模型: " + response.getMessage().getContent());
        System.out.println("✓ 简单对话测试通过");
    }

    /**
     * 判断本地 Ollama 是否可访问。
     *
     * @return 若可访问则返回 true
     */
    private boolean isOllamaReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 11434), 500);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
