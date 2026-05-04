package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import java.util.regex.Pattern;

/** Redacts common secrets before returning logs/session details to dashboard clients. */
public final class SecretRedactor {
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern KEY_VALUE =
            Pattern.compile(
                    "(?i)(api[_-]?key|token|secret|password|authorization|client[_-]?secret)(\\s*[:=]\\s*)([^\\s,;\"'}]+)");
    private static final int DEFAULT_MAX_LENGTH = 8000;

    private SecretRedactor() {}

    public static String redact(String text) {
        return redact(text, DEFAULT_MAX_LENGTH);
    }

    public static String redact(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String result = BEARER.matcher(text).replaceAll("Bearer ***");
        result = KEY_VALUE.matcher(result).replaceAll("$1$2***");
        int limit = Math.max(128, maxLength);
        if (result.length() > limit) {
            return result.substring(0, limit)
                    + "\n...[truncated, totalLength="
                    + result.length()
                    + "]";
        }
        return result;
    }

    public static Object redactObject(Object value) {
        if (value instanceof String) {
            return redact((String) value);
        }
        return value;
    }

    public static String maskUrl(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        return value.replaceAll("(?i)([?&](?:token|key|secret|password)=)[^&]+", "$1***");
    }
}
