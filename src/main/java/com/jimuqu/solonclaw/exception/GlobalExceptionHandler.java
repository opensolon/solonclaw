package com.jimuqu.solonclaw.exception;

import com.jimuqu.solonclaw.auth.AuthException;
import com.jimuqu.solonclaw.ratelimit.RateLimitException;
import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局异常处理器
 * <p>
 * 提供工具方法来处理应用中的异常，返回标准化的错误响应
 *
 * @author SolonClaw
 */
@Component
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理认证异常
     */
    public ErrorResult handleAuthException(AuthException e) {
        log.warn("认证异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ErrorResult.of(
                4000 + e.getErrorCode().getCode(),
                e.getMessage(),
                401
        );
    }

    /**
     * 处理限流异常
     */
    public ErrorResult handleRateLimitException(RateLimitException e) {
        log.warn("限流异常: message={}", e.getMessage());
        return ErrorResult.of(
                4300,
                e.getMessage(),
                429
        );
    }

    /**
     * 处理业务异常
     */
    public ErrorResult handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode(), e.getMessage());
        return ErrorResult.of(
                e.getErrorCode(),
                e.getMessage(),
                e.getHttpStatus()
        );
    }

    /**
     * 处理通用异常
     */
    public ErrorResult handleException(Exception e) {
        log.error("系统异常", e);
        return ErrorResult.of(
                5000,
                "系统内部错误，请稍后重试",
                500
        );
    }

    /**
     * 错误结果
     */
    public static class ErrorResult {
        private final int code;
        private final String message;
        private final int httpStatus;

        private ErrorResult(int code, String message, int httpStatus) {
            this.code = code;
            this.message = message;
            this.httpStatus = httpStatus;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        /**
         * 转换为 JSON 字符串
         */
        public String toJson() {
            return String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                    code, escapeJson(message));
        }

        public static ErrorResult of(int code, String message, int httpStatus) {
            return new ErrorResult(code, message, httpStatus);
        }

        /**
         * 转义 JSON 字符串
         */
        private static String escapeJson(String value) {
            if (value == null) return "";
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}