package com.jimuqu.solonclaw.ratelimit;

/**
 * 限流异常
 * <p>
 * 当请求被限流时抛出此异常
 *
 * @author SolonClaw
 */
public class RateLimitException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitException(String message) {
        this(message, 1);
    }

    /**
     * 获取建议的重试时间（秒）
     */
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}