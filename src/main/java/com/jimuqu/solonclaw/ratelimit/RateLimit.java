package com.jimuqu.solonclaw.ratelimit;

import java.lang.annotation.*;

/**
 * 限流注解
 * <p>
 * 用于标记需要进行限流的方法或类
 *
 * @author SolonClaw
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 每秒最大请求数
     */
    int value() default 10;

    /**
     * 时间窗口大小（秒）
     */
    int window() default 1;

    /**
     * 限流键生成器
     */
    String key() default "";

    /**
     * 限流类型
     */
    LimitType type() default LimitType.DEFAULT;

    /**
     * 是否在限流时记录警告日志
     */
    boolean log() default true;

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /** 默认（基于方法） */
        DEFAULT,
        /** 基于 IP */
        IP,
        /** 基于用户 */
        USER,
        /** 基于 API 路径 */
        PATH,
        /** 自定义键 */
        CUSTOM
    }
}