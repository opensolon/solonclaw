package com.jimuqu.solonclaw.auth;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户实体类
 * <p>
 * 表示系统中的用户信息
 *
 * @author SolonClaw
 */
public class User {
    private String id;
    private String username;
    private String password;  // 加密后的密码
    private String email;
    private UserRole role;
    private String apiKey;    // API 密钥（用于 API 认证）
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public User() {
        this.id = UUID.randomUUID().toString();
        this.role = UserRole.USER;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public User(String username, String password, String email) {
        this();
        this.username = username;
        this.password = password;
        this.email = email;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * 检查用户是否具有指定权限
     */
    public boolean hasPermission(UserRole required) {
        return this.role.hasPermission(required);
    }
}