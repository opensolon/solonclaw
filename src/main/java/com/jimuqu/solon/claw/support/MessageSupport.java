package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.ChatMessage;

/** 会话消息 NDJSON 辅助工具。 */
public final class MessageSupport {
    private MessageSupport() {}

    /** 将 NDJSON 反序列化为消息列表。 */
    public static List<ChatMessage> loadMessages(String ndjson) throws IOException {
        if (StrUtil.isBlank(ndjson)) {
            return new ArrayList<ChatMessage>();
        }

        return new ArrayList<ChatMessage>(ChatMessage.fromNdjson(ndjson));
    }

    /** 将消息列表序列化为 NDJSON。 */
    public static String toNdjson(List<ChatMessage> messages) throws IOException {
        return ChatMessage.toNdjson(messages);
    }

    /** 统计消息数量。 */
    public static int countMessages(String ndjson) throws IOException {
        return loadMessages(ndjson).size();
    }

    /** 获取最近一条用户消息。 */
    public static String getLastUserMessage(String ndjson) throws IOException {
        List<ChatMessage> messages = loadMessages(ndjson);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatRole.USER) {
                return message.getContent();
            }
        }

        return null;
    }

    /** 删除最后一轮用户交互，用于 `/undo` 与 `/retry`。 */
    public static String removeLastTurn(String ndjson) throws IOException {
        List<ChatMessage> messages = loadMessages(ndjson);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatRole.SYSTEM) {
                continue;
            }

            messages.remove(i);
            if (message.getRole() == ChatRole.USER) {
                break;
            }
        }

        return toNdjson(messages);
    }
}
