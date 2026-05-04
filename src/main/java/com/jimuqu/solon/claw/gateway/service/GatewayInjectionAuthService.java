package com.jimuqu.solon.claw.gateway.service;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.noear.solon.core.handle.Context;

/** Validates signed HTTP gateway injection requests. */
public class GatewayInjectionAuthService {
    private static final String HEADER_SIGNATURE = "X-SolonClaw-Signature";
    private static final String HEADER_TIMESTAMP = "X-SolonClaw-Timestamp";
    private static final String HEADER_NONCE = "X-SolonClaw-Nonce";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_NONCES = 2048;

    private final AppConfig appConfig;
    private final Map<String, Long> seenNonces =
            Collections.synchronizedMap(new LinkedHashMap<String, Long>());

    public GatewayInjectionAuthService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public void verify(Context context, String body) {
        if (!"POST".equalsIgnoreCase(context.method())) {
            context.status(405);
            throw new IllegalStateException("Gateway injection requires POST");
        }
        String secret = appConfig.getGateway().getInjectionSecret();
        if (StrUtil.isBlank(secret)) {
            context.status(403);
            throw new IllegalStateException("Gateway injection secret is not configured");
        }
        int maxBodyBytes = Math.max(1024, appConfig.getGateway().getInjectionMaxBodyBytes());
        int bodyBytes = body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
        if (bodyBytes <= 0 || bodyBytes > maxBodyBytes) {
            context.status(413);
            throw new IllegalStateException("Gateway injection body size is invalid");
        }

        String timestampText = context.header(HEADER_TIMESTAMP);
        String nonce = context.header(HEADER_NONCE);
        String signature = stripPrefix(context.header(HEADER_SIGNATURE));
        if (StrUtil.hasBlank(timestampText, nonce, signature)) {
            context.status(401);
            throw new IllegalStateException("Gateway injection signature headers are required");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText.trim());
        } catch (Exception e) {
            context.status(401);
            throw new IllegalStateException("Gateway injection timestamp is invalid");
        }
        long now = System.currentTimeMillis() / 1000L;
        long window = Math.max(30, appConfig.getGateway().getInjectionReplayWindowSeconds());
        if (Math.abs(now - timestamp) > window) {
            context.status(401);
            throw new IllegalStateException("Gateway injection timestamp is outside replay window");
        }
        String payload =
                timestampText.trim() + "." + nonce.trim() + "." + StrUtil.nullToEmpty(body);
        String expected = hmacSha256Hex(secret, payload);
        if (!constantTimeEquals(expected, signature)) {
            context.status(401);
            throw new IllegalStateException("Gateway injection signature is invalid");
        }
        if (!markNonce(nonce, now, window)) {
            context.status(409);
            throw new IllegalStateException("Gateway injection nonce has already been used");
        }
    }

    private boolean markNonce(String nonce, long now, long window) {
        String key = nonce == null ? "" : nonce.trim();
        if (key.length() == 0) {
            return false;
        }
        synchronized (seenNonces) {
            Iterator<Map.Entry<String, Long>> iterator = seenNonces.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (now - entry.getValue() > window || seenNonces.size() > MAX_NONCES) {
                    iterator.remove();
                }
            }
            if (seenNonces.containsKey(key)) {
                return false;
            }
            seenNonces.put(key, now);
            return true;
        }
    }

    private String stripPrefix(String value) {
        String text = StrUtil.nullToEmpty(value).trim();
        if (text.regionMatches(true, 0, "sha256=", 0, "sha256=".length())) {
            return text.substring("sha256=".length()).trim();
        }
        return text;
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign gateway injection payload", e);
        }
    }

    private boolean constantTimeEquals(String expectedHex, String actualHex) {
        byte[] expected =
                StrUtil.nullToEmpty(expectedHex).toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] actual =
                StrUtil.nullToEmpty(actualHex).toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            String hex = Integer.toHexString(value & 0xff);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }
}
