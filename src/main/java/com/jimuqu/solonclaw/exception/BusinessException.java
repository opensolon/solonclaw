package com.jimuqu.solonclaw.exception;

/**
 * 业务异常类
 * <p>
 * 用于处理业务逻辑中的异常情况
 *
 * @author SolonClaw
 */
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int errorCode;

    /**
     * HTTP 状态码
     */
    private final int httpStatus;

    public BusinessException(String message, int errorCode) {
        this(message, errorCode, 400);
    }

    public BusinessException(String message, int errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * 创建参数错误异常
     */
    public static BusinessException paramError(String message) {
        return new BusinessException(message, 3000, 400);
    }

    /**
     * 创建资源不存在异常
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(message, 4004, 404);
    }

    /**
     * 创建冲突异常
     */
    public static BusinessException conflict(String message) {
        return new BusinessException(message, 4009, 409);
    }

    /**
     * 创建未授权异常
     */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(message, 4001, 401);
    }

    /**
     * 创建禁止访问异常
     */
    public static BusinessException forbidden(String message) {
        return new BusinessException(message, 4003, 403);
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}