package com.jimuqu.solonclaw.auth;

/**
 * 用户角色枚举
 * <p>
 * 定义系统中的用户角色及其权限等级
 *
 * @author SolonClaw
 */
public enum UserRole {
    /**
     * 超级管理员 - 拥有所有权限
     */
    ADMIN(100, "超级管理员"),

    /**
     * 普通用户 - 正常使用权限
     */
    USER(50, "普通用户"),

    /**
     * 访客 - 只读权限
     */
    GUEST(10, "访客");

    private final int level;
    private final String description;

    UserRole(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 检查当前角色是否具有指定角色的权限
     */
    public boolean hasPermission(UserRole required) {
        return this.level >= required.level;
    }
}