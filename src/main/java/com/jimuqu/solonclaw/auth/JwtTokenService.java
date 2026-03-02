package com.jimuqu.solonclaw.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT Token 服务
 * <p>
 * 负责生成和验证 JWT Token
 *
 * @author SolonClaw
 */
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    /**
     * Token 默认有效期（毫秒）- 7 天
     */
    private static final long DEFAULT_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;

    /**
     * Token 密钥，从环境变量读取
     */
    private final String secret;

    /**
     * Token 有效期
     */
    private final long expireMs;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtTokenService(String secret) {
        this(secret, DEFAULT_EXPIRE_MS);
    }

    public JwtTokenService(String secret, long expireMs) {
        if (secret == null || secret.isEmpty()) {
            secret = "solonclaw-default-secret-key-change-in-production";
            log.warn("JWT 密钥未配置，使用默认密钥（不安全！）");
        }

        this.secret = secret;
        this.expireMs = expireMs;
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();

        log.info("JWT Token 服务初始化完成, expireMs={}", expireMs);
    }

    /**
     * 生成 Token
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole().name());
        claims.put("email", user.getEmail());

        String token = JWT.create()
                .withSubject(user.getId())
                .withIssuedAt(now)
                .withExpiresAt(expireAt)
                .withClaim("userId", user.getId())
                .withClaim("username", user.getUsername())
                .withClaim("role", user.getRole().name())
                .withClaim("email", user.getEmail())
                .sign(algorithm);

        log.debug("生成 Token: userId={}", user.getId());
        return token;
    }

    /**
     * 验证 Token
     */
    public DecodedJWT verifyToken(String token) throws AuthException {
        try {
            DecodedJWT decoded = verifier.verify(token);
            log.debug("验证 Token 成功: userId={}", decoded.getClaim("userId").asString());
            return decoded;
        } catch (JWTVerificationException e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            throw new AuthException("Token 验证失败", AuthException.ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 从 Token 中获取用户 ID
     */
    public String getUserIdFromToken(String token) throws AuthException {
        DecodedJWT decoded = verifyToken(token);
        return decoded.getClaim("userId").asString();
    }

    /**
     * 从 Token 中获取用户角色
     */
    public String getRoleFromToken(String token) throws AuthException {
        DecodedJWT decoded = verifyToken(token);
        return decoded.getClaim("role").asString();
    }

    /**
     * 检查 Token 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);
            return decoded.getExpiresAt().before(new Date());
        } catch (JWTVerificationException e) {
            return true;
        }
    }

    /**
     * 刷新 Token
     */
    public String refreshToken(String token, User user) throws AuthException {
        // 验证原 Token
        try {
            verifier.verify(token);
        } catch (JWTVerificationException e) {
            // 如果 Token 已过期，仍然允许刷新
            log.debug("Token 已过期，生成新 Token");
        }

        return generateToken(user);
    }
}