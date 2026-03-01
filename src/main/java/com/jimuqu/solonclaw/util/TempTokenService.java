package com.jimuqu.solonclaw.util;

import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时 Token 服务
 * <p>
 * 生成临时访问 token，用于文件访问控制
 *
 * @author SolonClaw
 */
@Component
public class TempTokenService {

    private static final Logger log = LoggerFactory.getLogger(TempTokenService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * Token 信息
     */
    private static class TokenInfo {
        String filePath;
        String randomFileName;
        Instant expiresAt;

        TokenInfo(String filePath, String randomFileName, Instant expiresAt) {
            this.filePath = filePath;
            this.randomFileName = randomFileName;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Token 存储键：token -> TokenInfo
     * 文件名存储键：token:filename -> token（用于快速查找）
     */
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final Map<String, String> fileNameToToken = new ConcurrentHashMap<>();

    /**
     * 生成临时访问 token（秒数）
     *
     * @param filePath 文件路径
     * @param seconds  有效期秒数
     * @return TokenResult 对象，包含 token 和随机文件名
     */
    public TokenResult generateToken(String filePath, int seconds) {
        return generateToken(filePath, Duration.of(seconds, ChronoUnit.SECONDS));
    }

    /**
     * 生成临时访问 token
     *
     * @param filePath  文件路径
     * @param duration 有效期时长
     * @return TokenResult 对象，包含 token 和随机文件名
     */
    public TokenResult generateToken(String filePath, Duration duration) {
        // 生成随机 token
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        String token = BASE64_ENCODER.encodeToString(randomBytes);

        // 生成随机文件名（保持原文件扩展名）
        String randomFileName = generateRandomFileName(filePath);

        // 计算过期时间
        Instant expiresAt = Instant.now().plus(duration);

        // 存储 token 信息
        TokenInfo tokenInfo = new TokenInfo(filePath, randomFileName, expiresAt);
        tokens.put(token, tokenInfo);

        // 存储文件名映射（用于快速查找）
        String key = token + ":" + randomFileName;
        fileNameToToken.put(key, token);

        log.info("生成临时 token: filePath={}, randomFileName={}, duration={}, expiresAt={}",
                filePath, randomFileName, duration, expiresAt);

        return new TokenResult(token, randomFileName);
    }

    /**
     * 生成随机文件名（保持原文件扩展名）
     */
    private String generateRandomFileName(String filePath) {
        // 获取原文件扩展名
        String extension = "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            extension = filePath.substring(lastDot);
        }

        // 生成随机文件名（16 字节）
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        String randomName = BASE64_ENCODER.encodeToString(randomBytes);

        return randomName + extension;
    }

    /**
     * 验证并获取文件路径（通过 token 和文件名）
     *
     * @param token        访问 token
     * @param randomFileName 随机文件名
     * @return 文件路径，如果验证失败返回 null
     */
    public String verifyAndGetFilePath(String token, String randomFileName) {
        if (token == null || token.isEmpty() || randomFileName == null || randomFileName.isEmpty()) {
            return null;
        }

        // 检查文件名是否匹配
        String key = token + ":" + randomFileName;
        String matchedToken = fileNameToToken.get(key);
        if (matchedToken == null) {
            log.warn("文件名与 token 不匹配: token={}, fileName={}", token, randomFileName);
            return null;
        }

        TokenInfo tokenInfo = tokens.get(token);

        if (tokenInfo == null) {
            log.warn("Token 不存在: {}", token);
            return null;
        }

        if (!randomFileName.equals(tokenInfo.randomFileName)) {
            log.warn("文件名不匹配: expected={}, actual={}", tokenInfo.randomFileName, randomFileName);
            return null;
        }

        if (tokenInfo.isExpired()) {
            log.warn("Token 已过期: {}", token);
            tokens.remove(token);
            fileNameToToken.remove(key);
            return null;
        }

        return tokenInfo.filePath;
    }

    /**
     * Token 生成结果
     */
    public static class TokenResult {
        private final String token;
        private final String randomFileName;

        public TokenResult(String token, String randomFileName) {
            this.token = token;
            this.randomFileName = randomFileName;
        }

        public String getToken() {
            return token;
        }

        public String getRandomFileName() {
            return randomFileName;
        }
    }

    /**
     * 清理过期的 token
     */
    public void cleanupExpiredTokens() {
        int count = 0;
        Instant now = Instant.now();

        for (Map.Entry<String, TokenInfo> entry : tokens.entrySet()) {
            if (entry.getValue().isExpired()) {
                String token = entry.getKey();
                String randomFileName = entry.getValue().randomFileName;
                String key = token + ":" + randomFileName;

                tokens.remove(token);
                fileNameToToken.remove(key);
                count++;
            }
        }

        if (count > 0) {
            log.info("清理了 {} 个过期 token", count);
        }
    }

    /**
     * 获取当前 token 数量
     */
    public int getTokenCount() {
        return tokens.size();
    }
}