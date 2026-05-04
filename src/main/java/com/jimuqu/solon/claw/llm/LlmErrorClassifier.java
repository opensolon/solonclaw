package com.jimuqu.solon.claw.llm;

import cn.hutool.core.util.StrUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.Getter;

/** Hermes 对齐的模型错误分类器。 */
public final class LlmErrorClassifier {
    private static final List<String> BILLING_PATTERNS =
            Arrays.asList(
                    "insufficient credits",
                    "insufficient_quota",
                    "insufficient balance",
                    "credit balance",
                    "credits have been exhausted",
                    "top up your credits",
                    "payment required",
                    "billing hard limit",
                    "exceeded your current quota",
                    "account is deactivated",
                    "plan does not include",
                    "billing_not_active");
    private static final List<String> RATE_LIMIT_PATTERNS =
            Arrays.asList(
                    "rate limit",
                    "rate_limit",
                    "too many requests",
                    "throttled",
                    "requests per minute",
                    "tokens per minute",
                    "requests per day",
                    "try again in",
                    "please retry after",
                    "resource_exhausted",
                    "rate increased too quickly",
                    "throttlingexception",
                    "too many concurrent requests",
                    "servicequotaexceededexception");
    private static final List<String> USAGE_LIMIT_PATTERNS =
            Arrays.asList("usage limit", "quota", "limit exceeded", "key limit exceeded");
    private static final List<String> USAGE_LIMIT_TRANSIENT_SIGNALS =
            Arrays.asList(
                    "try again",
                    "retry",
                    "resets at",
                    "reset in",
                    "wait",
                    "requests remaining",
                    "periodic",
                    "window");
    private static final List<String> CONTEXT_OVERFLOW_PATTERNS =
            Arrays.asList(
                    "context length",
                    "context size",
                    "maximum context",
                    "token limit",
                    "too many tokens",
                    "reduce the length",
                    "exceeds the limit",
                    "context window",
                    "prompt is too long",
                    "prompt exceeds max length",
                    "max_model_len",
                    "prompt length",
                    "input is too long",
                    "maximum model length",
                    "context length exceeded",
                    "上下文长度",
                    "超过最大长度",
                    "exceeds the maximum number of input tokens");
    private static final List<String> PAYLOAD_TOO_LARGE_PATTERNS =
            Arrays.asList("request entity too large", "payload too large", "error code: 413");
    private static final List<String> MODEL_NOT_FOUND_PATTERNS =
            Arrays.asList(
                    "is not a valid model",
                    "invalid model",
                    "model not found",
                    "model_not_found",
                    "does not exist",
                    "no such model",
                    "unknown model",
                    "unsupported model",
                    "model_not_available",
                    "invalid_model");
    private static final List<String> AUTH_PATTERNS =
            Arrays.asList(
                    "invalid api key",
                    "invalid_api_key",
                    "authentication",
                    "unauthorized",
                    "forbidden",
                    "invalid token",
                    "token expired",
                    "token revoked",
                    "access denied");
    private static final List<String> TRANSPORT_PATTERNS =
            Arrays.asList(
                    "timeout",
                    "timed out",
                    "connection reset",
                    "connection refused",
                    "connection aborted",
                    "broken pipe",
                    "eof",
                    "unreachable",
                    "network",
                    "remote protocol",
                    "server disconnected",
                    "ssl",
                    "tls");

    private LlmErrorClassifier() {}

    public static ClassifiedError classify(Throwable error) {
        String message = collectErrorText(error).toLowerCase(Locale.ROOT);
        int status = extractStatusCode(message);

        if (status == 402) {
            if (containsAny(message, USAGE_LIMIT_PATTERNS)
                    && containsAny(message, USAGE_LIMIT_TRANSIENT_SIGNALS)) {
                return result(FailoverReason.RATE_LIMIT, status, true, true, false, message);
            }
            return result(FailoverReason.BILLING, status, false, true, false, message);
        }
        if (status == 413 || containsAny(message, PAYLOAD_TOO_LARGE_PATTERNS)) {
            return result(FailoverReason.PAYLOAD_TOO_LARGE, status, true, false, true, message);
        }
        if (status == 401 || status == 403 || containsAny(message, AUTH_PATTERNS)) {
            return result(FailoverReason.AUTH, status, false, true, false, message);
        }
        if (status == 404 || containsAny(message, MODEL_NOT_FOUND_PATTERNS)) {
            return result(FailoverReason.MODEL_NOT_FOUND, status, false, true, false, message);
        }
        if (status == 429 || containsAny(message, RATE_LIMIT_PATTERNS)) {
            return result(FailoverReason.RATE_LIMIT, status, true, true, false, message);
        }
        if (containsAny(message, BILLING_PATTERNS)) {
            return result(FailoverReason.BILLING, status, false, true, false, message);
        }
        if (containsAny(message, USAGE_LIMIT_PATTERNS)) {
            if (containsAny(message, USAGE_LIMIT_TRANSIENT_SIGNALS)) {
                return result(FailoverReason.RATE_LIMIT, status, true, true, false, message);
            }
            return result(FailoverReason.BILLING, status, false, true, false, message);
        }
        if (containsAny(message, CONTEXT_OVERFLOW_PATTERNS)) {
            return result(FailoverReason.CONTEXT_OVERFLOW, status, true, false, true, message);
        }
        if (status == 500 || status == 502) {
            return result(FailoverReason.SERVER_ERROR, status, true, true, false, message);
        }
        if (status == 503 || status == 529) {
            return result(FailoverReason.OVERLOADED, status, true, true, false, message);
        }
        if (containsAny(message, TRANSPORT_PATTERNS)) {
            return result(FailoverReason.TIMEOUT, status, true, true, false, message);
        }
        return result(FailoverReason.UNKNOWN, status, false, true, false, message);
    }

    private static ClassifiedError result(
            FailoverReason reason,
            int statusCode,
            boolean retryable,
            boolean fallback,
            boolean compress,
            String message) {
        return new ClassifiedError(reason, statusCode, retryable, fallback, compress, message);
    }

    private static boolean containsAny(String message, List<String> patterns) {
        if (StrUtil.isBlank(message)) {
            return false;
        }
        for (String pattern : patterns) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String collectErrorText(Throwable error) {
        StringBuilder buffer = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (StrUtil.isNotBlank(current.getMessage())) {
                if (buffer.length() > 0) {
                    buffer.append(" | ");
                }
                buffer.append(current.getMessage());
            }
            current = current.getCause();
        }
        return buffer.toString();
    }

    private static int extractStatusCode(String message) {
        for (int code : new int[] {400, 401, 402, 403, 404, 413, 429, 500, 502, 503, 529}) {
            String value = String.valueOf(code);
            if (message.contains(" " + value + " ")
                    || message.contains("http " + value)
                    || message.contains("status=" + value)
                    || message.contains("status_code=" + value)
                    || message.contains("code=" + value)
                    || message.contains("[" + value + "]")
                    || message.contains("\"" + value + "\"")
                    || message.endsWith(value)) {
                return code;
            }
        }
        return 0;
    }

    public enum FailoverReason {
        AUTH,
        BILLING,
        RATE_LIMIT,
        OVERLOADED,
        SERVER_ERROR,
        TIMEOUT,
        CONTEXT_OVERFLOW,
        PAYLOAD_TOO_LARGE,
        MODEL_NOT_FOUND,
        UNKNOWN
    }

    @Getter
    public static class ClassifiedError {
        private final FailoverReason reason;
        private final int statusCode;
        private final boolean retryable;
        private final boolean shouldFallback;
        private final boolean shouldCompress;
        private final String message;

        private ClassifiedError(
                FailoverReason reason,
                int statusCode,
                boolean retryable,
                boolean shouldFallback,
                boolean shouldCompress,
                String message) {
            this.reason = reason;
            this.statusCode = statusCode;
            this.retryable = retryable;
            this.shouldFallback = shouldFallback;
            this.shouldCompress = shouldCompress;
            this.message = message;
        }
    }
}
