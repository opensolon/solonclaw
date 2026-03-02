package com.jimuqu.solonclaw.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务
 * <p>
 * 管理用户的创建、认证、授权等功能
 * 注意：这是内存存储实现，生产环境应使用数据库
 *
 * @author SolonClaw
 */
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /**
     * 用户存储（生产环境应使用数据库）
     */
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    /**
     * 用户名索引
     */
    private final ConcurrentHashMap<String, User> usersByUsername = new ConcurrentHashMap<>();

    /**
     * API 密钥索引
     */
    private final ConcurrentHashMap<String, User> usersByApiKey = new ConcurrentHashMap<>();

    private static UserService instance;

    private UserService() {
        // 初始化默认管理员账户
        initDefaultAdmin();
    }

    /**
     * 获取单例实例
     */
    public static synchronized UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }
        return instance;
    }

    /**
     * 初始化默认管理员账户
     */
    private void initDefaultAdmin() {
        String defaultAdminUsername = "admin";
        String defaultAdminPassword = "admin123";  // 默认密码，生产环境应修改

        try {
            String hashedPassword = hashPassword(defaultAdminPassword);
            User admin = new User(defaultAdminUsername, hashedPassword, "admin@solonclaw.com");
            admin.setRole(UserRole.ADMIN);
            admin.setApiKey(generateApiKey());

            users.put(admin.getId(), admin);
            usersByUsername.put(admin.getUsername().toLowerCase(), admin);
            usersByApiKey.put(admin.getApiKey(), admin);

            log.info("默认管理员账户已创建: username={}, apiKey={}",
                    defaultAdminUsername, admin.getApiKey());
        } catch (Exception e) {
            log.error("初始化默认管理员账户失败", e);
        }
    }

    /**
     * 创建新用户
     */
    public User createUser(String username, String password, String email, UserRole role) {
        if (usersByUsername.containsKey(username.toLowerCase())) {
            throw new AuthException("用户名已存在: " + username);
        }

        String hashedPassword = hashPassword(password);
        User user = new User(username, hashedPassword, email);
        user.setRole(role);
        user.setApiKey(generateApiKey());

        users.put(user.getId(), user);
        usersByUsername.put(user.getUsername().toLowerCase(), user);
        usersByApiKey.put(user.getApiKey(), user);

        log.info("创建用户成功: username={}, role={}", username, role);
        return user;
    }

    /**
     * 根据用户 ID 查找用户
     */
    public User findById(String userId) {
        return users.get(userId);
    }

    /**
     * 根据用户名查找用户
     */
    public User findByUsername(String username) {
        return usersByUsername.get(username.toLowerCase());
    }

    /**
     * 根据 API 密钥查找用户
     */
    public User findByApiKey(String apiKey) {
        return usersByApiKey.get(apiKey);
    }

    /**
     * 验证用户登录
     */
    public User authenticate(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            throw new AuthException("用户不存在");
        }

        if (!user.isEnabled()) {
            throw new AuthException("账户已被禁用");
        }

        String hashedPassword = hashPassword(password);
        if (!MessageDigest.isEqual(
                hashedPassword.getBytes(StandardCharsets.UTF_8),
                user.getPassword().getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginAt(java.time.LocalDateTime.now());

        log.info("用户登录成功: username={}", username);
        return user;
    }

    /**
     * 生成新的 API 密钥
     */
    public String regenerateApiKey(String userId) {
        User user = users.get(userId);
        if (user == null) {
            throw new AuthException("用户不存在");
        }

        // 移除旧的 API 密钥索引
        if (user.getApiKey() != null) {
            usersByApiKey.remove(user.getApiKey());
        }

        String newApiKey = generateApiKey();
        user.setApiKey(newApiKey);
        usersByApiKey.put(newApiKey, user);

        log.info("重新生成 API 密钥: username={}", user.getUsername());
        return newApiKey;
    }

    /**
     * 修改密码
     */
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = users.get(userId);
        if (user == null) {
            throw new AuthException("用户不存在");
        }

        String hashedOldPassword = hashPassword(oldPassword);
        if (!MessageDigest.isEqual(
                hashedOldPassword.getBytes(StandardCharsets.UTF_8),
                user.getPassword().getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("原密码错误");
        }

        user.setPassword(hashPassword(newPassword));
        log.info("修改密码成功: username={}", user.getUsername());
    }

    /**
     * 哈希密码（SHA-256 + Salt）
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("密码哈希失败", e);
        }
    }

    /**
     * 生成 API 密钥
     */
    private String generateApiKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "sk-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 检查用户权限
     */
    public boolean checkPermission(String userId, UserRole required) {
        User user = users.get(userId);
        return user != null && user.hasPermission(required);
    }
}