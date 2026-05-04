package com.jimuqu.solon.claw.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.service.MemoryService;
import com.jimuqu.solon.claw.support.constants.MemoryConstants;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** 长期记忆工具。 */
@RequiredArgsConstructor
public class MemoryTools {
    /** 长期记忆服务。 */
    private final MemoryService memoryService;

    /** 管理 MEMORY.md 与 USER.md。 */
    @ToolMapping(
            name = "memory",
            description =
                    "Manage persistent memory. action supports add, replace, remove, read. target supports memory, user, or today.")
    public String memory(
            @Param(name = "action", description = "操作类型：add、replace、remove、read") String action,
            @Param(name = "target", description = "目标存储：memory、user 或 today") String target,
            @Param(name = "content", description = "新增或替换的内容", required = false) String content,
            @Param(name = "oldText", description = "replace/remove 时用于匹配旧条目的文本", required = false)
                    String oldText)
            throws Exception {
        String normalizedTarget = StrUtil.blankToDefault(target, MemoryConstants.TARGET_MEMORY);
        if (MemoryConstants.ACTION_READ.equalsIgnoreCase(action)) {
            return new ONode()
                    .set("success", true)
                    .set("action", MemoryConstants.ACTION_READ)
                    .set("target", normalizedTarget)
                    .set("content", memoryService.read(normalizedTarget))
                    .set("message", "ok")
                    .toJson();
        }
        String result;
        if (MemoryConstants.ACTION_ADD.equalsIgnoreCase(action)) {
            result = memoryService.add(normalizedTarget, content);
        } else if (MemoryConstants.ACTION_REPLACE.equalsIgnoreCase(action)) {
            result = memoryService.replace(normalizedTarget, oldText, content);
        } else if (MemoryConstants.ACTION_REMOVE.equalsIgnoreCase(action)) {
            result =
                    memoryService.remove(
                            normalizedTarget, StrUtil.blankToDefault(oldText, content));
        } else {
            result = "Unsupported memory action";
        }
        return new ONode()
                .set("success", isSuccess(result))
                .set("action", StrUtil.nullToEmpty(action))
                .set("target", normalizedTarget)
                .set("message", result)
                .toJson();
    }

    private boolean isSuccess(String message) {
        String normalized = StrUtil.nullToEmpty(message).trim();
        if (normalized.length() == 0) {
            return false;
        }
        return !normalized.startsWith("Unsupported")
                && !normalized.contains("不能为空")
                && !normalized.contains("不会写入")
                && !normalized.startsWith("未");
    }
}
