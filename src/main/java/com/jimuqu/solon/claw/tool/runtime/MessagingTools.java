package com.jimuqu.solon.claw.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.core.model.ToolResultEnvelope;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** MessagingTools 实现。 */
@RequiredArgsConstructor
public class MessagingTools {
    private final DeliveryService deliveryService;
    private final String sourceKey;
    private final AttachmentCacheService attachmentCacheService;
    private final AppConfig appConfig;

    @ToolMapping(
            name = "send_message",
            description =
                    "Send a text message with optional local media attachments to a target platform and chat. If platform or chatId is empty, send back to the current source.")
    public String sendMessage(
            @Param(name = "platform", description = "目标平台名", required = false) String platform,
            @Param(name = "chatId", description = "目标聊天 ID", required = false) String chatId,
            @Param(name = "text", description = "要发送的文本") String text,
            @Param(
                            name = "mediaPaths",
                            description =
                                    "可选本地附件路径数组；优先传 PDF/文件工具返回的路径或文件名。文件必须位于 runtime/cache 下，或是 runtime 根目录直接生成的安全附件文件。",
                            required = false)
                    List<String> mediaPaths,
            @Param(
                            name = "channelExtrasJson",
                            description = "可选渠道扩展 JSON；例如钉钉 AI card 所需参数",
                            required = false)
                    String channelExtrasJson)
            throws Exception {
        String[] parts = sourceKey == null ? new String[0] : sourceKey.split(":", 3);
        if (parts.length < 2) {
            return error("invalid sourceKey");
        }
        PlatformType sourcePlatform = PlatformType.fromName(parts[0]);
        if (sourcePlatform == null) {
            return error("invalid source platform: " + parts[0]);
        }
        String sourceChatId = parts[1];
        PlatformType targetPlatform =
                PlatformType.fromName(StrUtil.isBlank(platform) ? parts[0] : platform);
        if (targetPlatform == null) {
            return error("invalid target platform: " + platform);
        }
        String targetChatId = StrUtil.isBlank(chatId) ? parts[1] : chatId;
        String targetUserId = parts.length > 2 ? parts[2] : null;
        List<MessageAttachment> attachments = resolveAttachments(targetPlatform, mediaPaths);
        DeliveryRequest request = new DeliveryRequest();
        request.setPlatform(targetPlatform);
        request.setChatId(targetChatId);
        request.setUserId(targetUserId);
        request.setText(text);
        request.setAttachments(attachments);
        request.setChannelExtras(parseChannelExtras(channelExtrasJson));
        deliveryService.deliver(request);
        MessageDeliveryTracker.recordEcho(
                sourceKey,
                sourcePlatform,
                sourceChatId,
                targetPlatform,
                targetChatId,
                text,
                attachments != null && !attachments.isEmpty());
        return ToolResultEnvelope.ok("Message delivered")
                .data("platform", targetPlatform.name())
                .data("chatId", targetChatId)
                .data("attachmentCount", Integer.valueOf(attachments.size()))
                .preview(text)
                .metadata("sourceKey", sourceKey)
                .toJson();
    }

    private String error(String message) {
        return ToolResultEnvelope.error(message).toJson();
    }

    private List<MessageAttachment> resolveAttachments(
            PlatformType platform, List<String> mediaPaths) {
        List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
        if (mediaPaths == null) {
            return attachments;
        }

        for (String rawPath : mediaPaths) {
            if (StrUtil.isBlank(rawPath)) {
                continue;
            }
            File file = resolveAttachmentFile(rawPath.trim());
            attachments.add(
                    attachmentCacheService.fromLocalOrGeneratedFile(
                            platform, file.getAbsoluteFile(), null, false, null));
        }
        return attachments;
    }

    private File resolveAttachmentFile(String rawPath) {
        File direct = new File(rawPath);
        if (direct.isFile()) {
            return direct;
        }

        File workspaceFile = new File(System.getProperty("user.dir"), rawPath);
        if (workspaceFile.isFile()) {
            return workspaceFile;
        }

        String name = direct.getName();
        for (File candidate : fallbackCandidates(name)) {
            if (candidate.isFile()) {
                return candidate;
            }
        }

        return direct.isAbsolute() ? direct : workspaceFile;
    }

    private List<File> fallbackCandidates(String fileName) {
        List<File> candidates = new ArrayList<File>();
        if (appConfig != null && appConfig.getRuntime() != null) {
            File runtimeHome = new File(appConfig.getRuntime().getHome());
            File cacheDir = new File(appConfig.getRuntime().getCacheDir());
            candidates.add(new File(cacheDir, "pdf/" + fileName));
            candidates.add(new File(cacheDir, fileName));
            candidates.add(new File(runtimeHome, fileName));
        }
        candidates.add(new File(System.getProperty("user.dir"), fileName));
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseChannelExtras(String channelExtrasJson) {
        if (StrUtil.isBlank(channelExtrasJson)) {
            return new LinkedHashMap<String, Object>();
        }
        Object parsed = org.noear.snack4.ONode.deserialize(channelExtrasJson.trim(), Object.class);
        if (parsed instanceof Map) {
            return new LinkedHashMap<String, Object>((Map<String, Object>) parsed);
        }
        throw new IllegalArgumentException("channelExtrasJson must be a JSON object");
    }
}
