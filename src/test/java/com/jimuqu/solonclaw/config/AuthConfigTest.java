package com.jimuqu.solonclaw.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthConfig 测试类
 * 测试认证配置和 Token 生成功能
 *
 * @author SolonClaw
 */
class AuthConfigTest {

    @Test
    @DisplayName("生成 Token 应该返回非空字符串")
    void testGenerateToken_NotNull() {
        String token = AuthConfig.generateToken();
        assertNotNull(token, "生成的 Token 不应该为 null");
    }

    @Test
    @DisplayName("生成 Token 应该有足够的长度")
    void testGenerateToken_SufficientLength() {
        String token = AuthConfig.generateToken();
        // 32 字节 Base64 编码后应该是 43 个字符（无填充）
        assertTrue(token.length() >= 32, "Token 长度应该足够");
    }

    @Test
    @DisplayName("多次生成 Token 应该返回不同的值")
    void testGenerateToken_Uniqueness() {
        String token1 = AuthConfig.generateToken();
        String token2 = AuthConfig.generateToken();
        String token3 = AuthConfig.generateToken();

        assertNotEquals(token1, token2, "每次生成的 Token 应该不同");
        assertNotEquals(token2, token3, "每次生成的 Token 应该不同");
        assertNotEquals(token1, token3, "每次生成的 Token 应该不同");
    }

    @Test
    @DisplayName("Token 应该只包含 Base64 字符")
    void testGenerateToken_Base64Characters() {
        String token = AuthConfig.generateToken();
        // Base64 字符集（无填充）：A-Z, a-z, 0-9, +, /, - 或 _
        // 标准 Base64 使用 + 和 /，URL 安全版本使用 - 和 _
        assertTrue(token.matches("^[A-Za-z0-9+/=_-]+$"),
                "Token 应该只包含 Base64 字符");
    }
}
