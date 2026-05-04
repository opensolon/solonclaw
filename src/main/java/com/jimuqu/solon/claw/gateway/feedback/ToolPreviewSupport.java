package com.jimuqu.solon.claw.gateway.feedback;

import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;

/** 工具参数预览辅助。 */
public final class ToolPreviewSupport {
    private ToolPreviewSupport() {}

    public static String buildPreview(
            String toolName, Map<String, Object> args, int maxLen, boolean verbose) {
        if (args == null || args.isEmpty()) {
            return "";
        }

        String preview;
        if (verbose) {
            preview = ONode.serialize(args);
        } else {
            preview = pickPrimaryValue(toolName, args);
        }

        preview = normalize(preview);
        if (preview.length() <= maxLen) {
            return preview;
        }
        if (verbose) {
            return buildJsonSafePreview(args, maxLen);
        }
        return preview.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private static String buildJsonSafePreview(Map<String, Object> args, int maxLen) {
        try {
            Object copy = ONode.deserialize(ONode.serialize(args), Object.class);
            shrinkJsonStrings(copy, Math.max(24, maxLen / 3));
            String serialized = normalize(ONode.serialize(copy));
            if (serialized.length() <= maxLen) {
                return serialized;
            }
        } catch (Exception ignored) {
            // fall through
        }
        Map<String, Object> fallback = new LinkedHashMap<String, Object>();
        fallback.put("truncated", Boolean.TRUE);
        fallback.put(
                "preview", normalize(ONode.serialize(args)).substring(0, Math.max(0, maxLen - 32)));
        return normalize(ONode.serialize(fallback));
    }

    @SuppressWarnings("unchecked")
    private static void shrinkJsonStrings(Object value, int maxStringLength) {
        if (value instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                Object item = entry.getValue();
                if (item instanceof String && ((String) item).length() > maxStringLength) {
                    entry.setValue(
                            ((String) item).substring(0, maxStringLength) + "...[truncated]");
                } else {
                    shrinkJsonStrings(item, maxStringLength);
                }
            }
        } else if (value instanceof List) {
            for (Object item : (List<?>) value) {
                shrinkJsonStrings(item, maxStringLength);
            }
        }
    }

    private static String pickPrimaryValue(String toolName, Map<String, Object> args) {
        String[] candidates = preferredKeys(toolName);
        for (String key : candidates) {
            if (!args.containsKey(key)) {
                continue;
            }
            Object value = args.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Iterable) {
                String text = normalize(ONode.serialize(value));
                if (StrUtil.isNotBlank(text)) {
                    return key + "=" + text;
                }
                continue;
            }
            String text = normalize(String.valueOf(value));
            if (StrUtil.isNotBlank(text)) {
                return key + "=" + text;
            }
        }
        return normalize(ONode.serialize(args));
    }

    private static String[] preferredKeys(String toolName) {
        if ("file_read".equals(toolName)
                || "file_write".equals(toolName)
                || "file_delete".equals(toolName)) {
            return new String[] {"fileName", "path", "filePath"};
        }
        if ("file_list".equals(toolName)) {
            return new String[] {"dirName", "path"};
        }
        if ("execute_shell".equals(toolName)
                || "execute_python".equals(toolName)
                || "execute_js".equals(toolName)) {
            return new String[] {"command", "code"};
        }
        if ("delegate_task".equals(toolName)) {
            return new String[] {"prompt", "goal", "context"};
        }
        if ("send_message".equals(toolName)) {
            return new String[] {"text", "chatId", "platform"};
        }
        if ("session_search".equals(toolName)
                || "websearch".equals(toolName)
                || "codesearch".equals(toolName)) {
            return new String[] {"query", "q", "keyword"};
        }
        if ("webfetch".equals(toolName)) {
            return new String[] {"url", "urls"};
        }
        if ("cronjob".equals(toolName)) {
            return new String[] {"action", "name"};
        }
        if ("skill_view".equals(toolName) || "skill_manage".equals(toolName)) {
            return new String[] {"name", "skillName"};
        }
        return new String[] {"path", "command", "code", "query", "text", "name"};
    }

    private static String normalize(String text) {
        return StrUtil.nullToEmpty(text).replace('\r', ' ').replace('\n', ' ').trim();
    }
}
