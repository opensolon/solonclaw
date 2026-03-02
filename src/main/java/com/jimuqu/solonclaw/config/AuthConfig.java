package com.jimuqu.solonclaw.config;

import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.FilterChain;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 认证配置类
 * <p>
 * 提供 API 认证授权机制，支持 Token 认证和请求签名验证
 *
 * @author SolonClaw
 */
@Configuration
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    /**
     * 认证 Token，从环境变量读取
     */
    @Inject("${solonclaw.auth.token:${SOLONCLAW_AUTH_TOKEN:}}")
    private String authToken;

    /**
     * 是否启用认证
     */
    @Inject("${solonclaw.auth.enabled:false}")
    private boolean authEnabled;

    /**
     * 签名密钥，从环境变量读取
     */
    @Inject("${solonclaw.auth.signSecret:${SOLONCLAW_SIGN_SECRET:}}")
    private String signSecret;

    /**
     * 签名有效期（毫秒）
     */
    @Inject("${solonclaw.auth.signExpireMs:300000}")
    private long signExpireMs;

    /**
     * 非重放缓存，防止请求被重放
     */
    private final ConcurrentHashMap<String, Long> nonceCache = new ConcurrentHashMap<>();

    /**
     * 不需要认证的路径
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/health",
            "/",
            "/index.html",
            "/favicon.ico"
    };

    /**
     * 配置认证过滤器
     */
    @Bean
    public Filter authFilter() {
        log.info("配置认证过滤器, enabled={}", authEnabled);

        return new Filter() {
            @Override
            public void doFilter(Context ctx, FilterChain chain) throws Throwable {
                String path = ctx.path();

                // 检查是否是公开路径
                if (isPublicPath(path)) {
                    chain.doFilter(ctx);
                    return;
                }

                // 如果认证未启用，直接放行
                if (!authEnabled) {
                    chain.doFilter(ctx);
                    return;
                }

                // 静态资源放行
                if (isStaticResource(path)) {
                    chain.doFilter(ctx);
                    return;
                }

                // 尝试 Token 认证
                String token = ctx.header("Authorization");
                if (token != null && validateToken(token)) {
                    chain.doFilter(ctx);
                    return;
                }

                // 尝试签名认证
                if (validateSignature(ctx)) {
                    chain.doFilter(ctx);
                    return;
                }

                // 认证失败
                log.warn("认证失败: path={}, ip={}", path, ctx.realIp());
                ctx.status(401);
                ctx.contentType("application/json;charset=UTF-8");
                ctx.output("{\"code\":401,\"message\":\"Unauthorized - 认证失败\",\"data\":null}");
            }
        };
    }

    /**
     * 检查是否是公开路径
     */
    private boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath) || path.startsWith(publicPath + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是静态资源
     */
    private boolean isStaticResource(String path) {
        return path.startsWith("/static/") ||
               path.endsWith(".js") ||
               path.endsWith(".css") ||
               path.endsWith(".ico") ||
               path.endsWith(".png") ||
               path.endsWith(".jpg") ||
               path.endsWith(".svg") ||
               path.endsWith(".woff") ||
               path.endsWith(".woff2");
    }

    /**
     * 验证 Token
     */
    private boolean validateToken(String token) {
        if (authToken == null || authToken.isEmpty()) {
            log.debug("Token 未配置，跳过 Token 认证");
            return false;
        }

        // 移除 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 使用常量时间比较，防止时序攻击
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                authToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 验证请求签名
     * <p>
     * 签名规则：
     * 1. 客户端生成随机 nonce
     * 2. 获取当前时间戳 timestamp
     * 3. 拼接字符串: METHOD + PATH + TIMESTAMP + NONCE + BODY_HASH
     * 4. 使用 HMAC-SHA256 计算签名
     * 5. 将签名 Base64 编码
     * <p>
     * 请求头：
     * - X-Timestamp: 时间戳
     * - X-Nonce: 随机字符串
     * - X-Signature: 签名
     */
    private boolean validateSignature(Context ctx) {
        if (signSecret == null || signSecret.isEmpty()) {
            log.debug("签名密钥未配置，跳过签名认证");
            return false;
        }

        try {
            String timestampStr = ctx.header("X-Timestamp");
            String nonce = ctx.header("X-Nonce");
            String signature = ctx.header("X-Signature");

            if (timestampStr == null || nonce == null || signature == null) {
                log.debug("缺少签名认证头");
                return false;
            }

            // 验证时间戳（防止重放攻击）
            long timestamp = Long.parseLong(timestampStr);
            long currentTime = System.currentTimeMillis();
            if (Math.abs(currentTime - timestamp) > signExpireMs) {
                log.warn("签名已过期: timestamp={}, current={}, expireMs={}",
                        timestamp, currentTime, signExpireMs);
                return false;
            }

            // 验证 nonce（防止重放攻击）
            if (nonceCache.containsKey(nonce)) {
                log.warn("检测到重放攻击: nonce={}", nonce);
                return false;
            }

            // 清理过期的 nonce
            cleanExpiredNonces(currentTime);

            // 计算签名
            String method = ctx.method();
            String path = ctx.path();
            String bodyHash = sha256(ctx.body() != null ? ctx.body() : "");

            String signData = method + path + timestampStr + nonce + bodyHash;
            String expectedSignature = hmacSha256(signData, signSecret);

            // 使用常量时间比较
            boolean valid = MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );

            if (valid) {
                // 记录 nonce，防止重放
                nonceCache.put(nonce, currentTime);
                log.debug("签名验证成功");
            } else {
                log.warn("签名验证失败: expected={}, actual={}", expectedSignature, signature);
            }

            return valid;

        } catch (NumberFormatException e) {
            log.warn("时间戳格式错误");
            return false;
        } catch (Exception e) {
            log.error("签名验证异常", e);
            return false;
        }
    }

    /**
     * 清理过期的 nonce
     */
    private void cleanExpiredNonces(long currentTime) {
        nonceCache.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > signExpireMs * 2
        );
    }

    /**
     * SHA256 哈希
     */
    private String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA256 计算失败", e);
        }
    }

    /**
     * HMAC-SHA256 签名
     */
    private String hmacSha256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 计算失败", e);
        }
    }

    /**
     * 生成随机 Token
     * <p>
     * 用于生成安全的认证 Token
     */
    public static String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}
