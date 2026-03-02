package com.jimuqu.solonclaw.auth;

/**
 * 认证异常类
 * <p>
 * 处理认证和授权过程中出现的业务异常
 *
 * @author SolonClaw
 */
public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;

    public AuthException(String message) {
        this(message, ErrorCode.UNKNOWN_ERROR);
    }

    public AuthException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 认证错误码枚举
     */
    public enum ErrorCode {
        /**
         * 未知错误
         */
        UNKNOWN_ERROR(4000, "未知错误"),

        /**
         * 用户不存在
         */
        USER_NOT_FOUND(4001, "用户不存在"),

        /**
         * 用户名已存在
         */
        USERNAME_EXISTS(4002, "用户名已存在"),

        /**
         * 密码错误
         */
        INVALID_PASSWORD(4003, "密码错误"),

        /**
         * Token 无效
         */
        INVALID_TOKEN(4004, "Token 无效"),

        /**
         * Token 已过期
         */
        TOKEN_EXPIRED(4005, "Token 已过期"),

        /**
         * API 密钥无效
         */
        INVALID_API_KEY(4006, "API 密钥无效"),

        /**
         * 权限不足
         */
        INSUFFICIENT_PERMISSION(4007, "权限不足"),

        /**
         * 账户已被禁用
         */
        ACCOUNT_DISABLED(4008, "账户已被禁用"),

        /**
         * 原密码错误
         */
        INVALID_OLD_PASSWORD(4009, "原密码错误");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
