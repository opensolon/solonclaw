package com.jimuqu.solon.claw.core.model;

import cn.hutool.core.util.StrUtil;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.snack4.ONode;

/** 统一工具返回 envelope，兼容旧版 success 字段。 */
public class ToolResultEnvelope {
    private final Map<String, Object> data = new LinkedHashMap<String, Object>();
    private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    private String status;
    private String summary;
    private String preview;
    private String resultRef;
    private String error;
    private long size;
    private boolean truncated;

    public static ToolResultEnvelope ok(String summary) {
        ToolResultEnvelope envelope = new ToolResultEnvelope();
        envelope.status = "success";
        envelope.summary = summary;
        return envelope;
    }

    public static ToolResultEnvelope error(String message) {
        ToolResultEnvelope envelope = new ToolResultEnvelope();
        envelope.status = "error";
        envelope.error = StrUtil.blankToDefault(message, "Tool execution failed");
        envelope.summary = envelope.error;
        return envelope;
    }

    public ToolResultEnvelope data(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public ToolResultEnvelope metadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    public ToolResultEnvelope preview(String preview) {
        this.preview = preview;
        this.size = StrUtil.nullToEmpty(preview).getBytes(StandardCharsets.UTF_8).length;
        return this;
    }

    public ToolResultEnvelope resultRef(String resultRef) {
        this.resultRef = resultRef;
        return this;
    }

    public ToolResultEnvelope size(long size) {
        this.size = size;
        return this;
    }

    public ToolResultEnvelope truncated(boolean truncated) {
        this.truncated = truncated;
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("status", status);
        map.put("success", Boolean.valueOf("success".equals(status)));
        map.put("summary", summary);
        if (StrUtil.isNotBlank(preview)) {
            map.put("preview", preview);
        }
        if (StrUtil.isNotBlank(resultRef)) {
            map.put("result_ref", resultRef);
        }
        if (StrUtil.isNotBlank(error)) {
            map.put("error", error);
        }
        map.put("size", Long.valueOf(size));
        map.put("truncated", Boolean.valueOf(truncated));
        if (!metadata.isEmpty()) {
            map.put("metadata", metadata);
        }
        map.putAll(data);
        return map;
    }

    public String toJson() {
        return ONode.serialize(toMap());
    }
}
