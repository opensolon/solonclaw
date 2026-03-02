package com.jimuqu.solonclaw.callback;

import okhttp3.*;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回调服务
 * <p>
 * 处理 HTTP 回调发送和签名验证
 *
 * @author SolonClaw
 */
@Component
public class CallbackService {

    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);

    @Inject
    private OkHttpClient httpClient;

    @Inject("${solonclaw.callback.enabled}")
    private boolean enabled;

    @Inject("${solonclaw.callback.url}")
    private String callbackUrl;

    @Inject("${solonclaw.callback.secret}")
    private String callbackSecret;

    /**
     * 发送回调
     *
     * @param event 事件类型
     * @param data  数据
     */
    public void sendCallback(String event, Map<String, Object> data) {
        if (!enabled || callbackUrl == null || callbackUrl.isEmpty()) {
            log.debug("回调未启用或 URL 未配置");
            return;
        }

        try {
            // 构建回调数据
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", event);
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("data", data);

            // 生成签名
            String signature = generateSignature(payload);
            payload.put("signature", signature);

            // 序列化为 JSON
            String jsonBody = serializeToJson(payload);

            // 发送 HTTP 请求
            Request request = new Request.Builder()
                .url(callbackUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Event-Type", event)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("回调发送成功: event={}, status={}", event, response.code());
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "无响应";
                    log.warn("回调发送失败: event={}, status={}, body={}",
                        event, response.code(), errorBody);
                }
            }
        } catch (Exception e) {
            log.error("发送回调异常: event={}", event, e);
        }
    }

    /**
     * 发送任务完成回调
     *
     * @param taskName 任务名称
     * @param success  是否成功
     * @param duration 执行时长
     */
    public void sendTaskCompleteCallback(String taskName, boolean success, long duration) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskName", taskName);
        data.put("success", success);
        data.put("duration", duration);

        sendCallback("task.complete", data);
    }

    /**
     * 发送消息回调
     *
     * @param sessionId 会话 ID
     * @param role      角色
     * @param content   内容
     */
    public void sendMessageCallback(String sessionId, String role, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("role", role);
        data.put("content", content);

        sendCallback("message.new", data);
    }

    /**
     * 发送错误回调
     *
     * @param errorType 错误类型
     * @param message   错误消息
     */
    public void sendErrorCallback(String errorType, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("errorType", errorType);
        data.put("message", message);
        data.put("timestamp", System.currentTimeMillis());

        sendCallback("error", data);
    }

    /**
     * 生成签名
     *
     * @param payload 数据
     * @return 签名
     */
    private String generateSignature(Map<String, Object> payload) {
        if (callbackSecret == null || callbackSecret.isEmpty()) {
            return "";
        }

        try {
            // 移除 signature 字段（如果存在）
            Map<String, Object> toSign = new HashMap<>(payload);
            toSign.remove("signature");

            // 按键排序
            String payloadJson = serializeToJson(toSign);

            // 计算签名
            String signData = payloadJson + callbackSecret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(signData.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            log.error("生成签名失败", e);
            return "";
        }
    }

    /**
     * 验证签名
     *
     * @param payload    数据
     * @param signature 签名
     * @return 是否有效
     */
    public boolean verifySignature(Map<String, Object> payload, String signature) {
        if (callbackSecret == null || callbackSecret.isEmpty()) {
            // 如果没有配置 secret，不验证签名
            return true;
        }

        String expectedSignature = generateSignature(payload);
        return expectedSignature.equals(signature);
    }

    /**
     * 序列化对象为 JSON
     */
    private String serializeToJson(Object obj) {
        if (obj instanceof Map) {
            return serializeMap((Map<?, ?>) obj);
        } else if (obj instanceof List) {
            return serializeList((List<?>) obj);
        } else {
            return "\"" + obj.toString() + "\"";
        }
    }

    private String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeValue(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeValue(Object value) {
        if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value);
        } else if (value instanceof List) {
            return serializeList((List<?>) value);
        } else if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (value == null) {
            return "null";
        } else {
            return "\"" + value.toString() + "\"";
        }
    }

    /**
     * 检查回调是否启用
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取回调 URL
     *
     * @return 回调 URL
     */
    public String getCallbackUrl() {
        return callbackUrl;
    }
}