package com.jimuqu.solonclaw.config;

import com.jimuqu.solonclaw.auth.*;
import org.noear.solon.annotation.*;

/**
 * 认证配置
 * <p>
 * 配置 JWT 认证服务和用户服务
 *
 * @author SolonClaw
 */
@Configuration
public class JwtAuthConfig {

    /**
     * JWT 密钥，从环境变量读取
     */
    @Inject("${solonclaw.jwt.secret:${JWT_SECRET:solonclaw-default-secret-key-change-in-production}}")
    private String jwtSecret;

    /**
     * Token 有效期（毫秒），默认 7 天
     */
    @Inject("${solonclaw.jwt.expireMs:604800000}")
    private long jwtExpireMs;

    /**
     * 创建 JWT Token 服务
     */
    @Bean
    public JwtTokenService jwtTokenService() {
        return new JwtTokenService(jwtSecret, jwtExpireMs);
    }

    /**
     * 创建用户服务
     */
    @Bean
    public UserService userService() {
        return UserService.getInstance();
    }
}