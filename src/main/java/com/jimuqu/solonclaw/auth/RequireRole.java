package com.jimuqu.solonclaw.auth;

import java.lang.annotation.*;

/**
 * 需要指定角色的注解
 * <p>
 * 用于标记需要特定角色才能访问的接口或方法
 *
 * @author SolonClaw
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 需要的角色
     */
    UserRole value();

    /**
     * 角色匹配模式
     * <p>
     * EXACT: 精确匹配
     * MINIMUM: 权限等级不低于指定角色
     */
    MatchMode mode() default MatchMode.MINIMUM;

    /**
     * 匹配模式
     */
    enum MatchMode {
        /**
         * 精确匹配
         */
        EXACT,

        /**
         * 权限等级不低于指定角色（推荐）
         */
        MINIMUM
    }
}