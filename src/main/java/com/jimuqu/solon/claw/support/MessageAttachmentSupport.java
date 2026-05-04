package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import java.util.List;

/** 入站附件提示组装辅助类。 */
public final class MessageAttachmentSupport {
    private MessageAttachmentSupport() {}

    /** 将附件元信息注入为会话可见文本。 */
    public static String composeEffectiveUserText(GatewayMessage message) {
        String text = StrUtil.nullToEmpty(message == null ? null : message.getText()).trim();
        if (message == null
                || message.getAttachments() == null
                || message.getAttachments().isEmpty()) {
            return text;
        }

        StringBuilder buffer = new StringBuilder();
        if (StrUtil.isNotBlank(text)) {
            buffer.append(text).append("\n\n");
        }
        buffer.append("[attachments]");
        for (MessageAttachment attachment : message.getAttachments()) {
            buffer.append("\n- kind=").append(StrUtil.blankToDefault(attachment.getKind(), "file"));
            buffer.append(", originalName=")
                    .append(StrUtil.blankToDefault(attachment.getOriginalName(), ""));
            buffer.append(", mimeType=")
                    .append(StrUtil.blankToDefault(attachment.getMimeType(), ""));
            buffer.append(", localPath=")
                    .append(StrUtil.blankToDefault(attachment.getLocalPath(), ""));
            buffer.append(", fromQuote=").append(attachment.isFromQuote());
            if (StrUtil.isNotBlank(attachment.getTranscribedText())) {
                buffer.append(", transcribedText=")
                        .append(safeInline(attachment.getTranscribedText()));
            }
        }
        return buffer.toString().trim();
    }

    /** 返回附件清单的副本，避免空指针。 */
    public static List<MessageAttachment> safeAttachments(GatewayMessage message) {
        return message == null
                ? java.util.Collections.<MessageAttachment>emptyList()
                : message.getAttachments();
    }

    private static String safeInline(String text) {
        return StrUtil.nullToEmpty(text).replace('\r', ' ').replace('\n', ' ').trim();
    }
}
