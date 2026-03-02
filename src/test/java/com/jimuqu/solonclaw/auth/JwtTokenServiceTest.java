package com.jimuqu.solonclaw.auth;

import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT Token 服务测试
 *
 * @author SolonClaw
 */
@DisplayName("JWT Token 服务测试")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService("test-secret-key-for-jwt-token-generation", 60000);
    }

    @Nested
    @DisplayName("Token 生成测试")
    class TokenGenerationTests {

        @Test
        @DisplayName("应能生成有效的 Token")
        void shouldGenerateValidToken() {
            User user = new User("testuser", "password", "test@example.com");
            user.setRole(UserRole.USER);

            String token = jwtTokenService.generateToken(user);

            assertNotNull(token, "Token 不应为空");
            assertFalse(token.isEmpty(), "Token 不应为空字符串");
        }

        @Test
        @DisplayName("生成的 Token 应能被验证")
        void generatedTokenShouldBeVerifiable() {
            User user = new User("testuser", "password", "test@example.com");
            user.setRole(UserRole.USER);
            String token = jwtTokenService.generateToken(user);

            assertDoesNotThrow(() -> jwtTokenService.verifyToken(token),
                    "生成的 Token 应能被成功验证");
        }

        @Test
        @DisplayName("使用错误密钥生成的 Token 应无法验证")
        void tokenWithWrongSecretShouldFailVerification() {
            User user = new User("testuser", "password", "test@example.com");
            JwtTokenService otherService = new JwtTokenService("different-secret-key");
            String token = otherService.generateToken(user);

            assertThrows(AuthException.class, () -> jwtTokenService.verifyToken(token),
                    "使用错误密钥生成的 Token 验证应失败");
        }
    }

    @Nested
    @DisplayName("Token 验证测试")
    class TokenVerificationTests {

        @Test
        @DisplayName("验证成功应返回 DecodedJWT")
        void successfulVerificationShouldReturnDecodedJWT() {
            User user = new User("testuser", "password", "test@example.com");
            user.setRole(UserRole.USER);
            String token = jwtTokenService.generateToken(user);

            var decoded = jwtTokenService.verifyToken(token);

            assertNotNull(decoded, "DecodedJWT 不应为空");
            assertEquals(user.getId(), decoded.getClaim("userId").asString(),
                    "Token 中的 userId 应匹配");
            assertEquals("testuser", decoded.getClaim("username").asString(),
                    "Token 中的 username 应匹配");
            assertEquals("USER", decoded.getClaim("role").asString(),
                    "Token 中的 role 应匹配");
        }

        @Test
        @DisplayName("空 Token 验证应失败")
        void emptyTokenShouldFailVerification() {
            assertThrows(AuthException.class, () -> jwtTokenService.verifyToken(""),
                    "空 Token 验证应失败");
        }

        @Test
        @DisplayName("无效 Token 验证应失败")
        void invalidTokenShouldFailVerification() {
            assertThrows(AuthException.class, () -> jwtTokenService.verifyToken("invalid.token.here"),
                    "无效 Token 验证应失败");
        }
    }

    @Nested
    @DisplayName("Token 信息提取测试")
    class TokenInfoExtractionTests {

        @Test
        @DisplayName("应能从 Token 中提取用户 ID")
        void shouldExtractUserIdFromToken() {
            User user = new User("testuser", "password", "test@example.com");
            user.setRole(UserRole.USER);
            String token = jwtTokenService.generateToken(user);

            String userId = jwtTokenService.getUserIdFromToken(token);

            assertEquals(user.getId(), userId, "提取的 userId 应匹配");
        }

        @Test
        @DisplayName("应能从 Token 中提取角色")
        void shouldExtractRoleFromToken() {
            User user = new User("testuser", "password", "test@example.com");
            user.setRole(UserRole.ADMIN);
            String token = jwtTokenService.generateToken(user);

            String role = jwtTokenService.getRoleFromToken(token);

            assertEquals("ADMIN", role, "提取的 role 应匹配");
        }

        @Test
        @DisplayName("从无效 Token 提取信息应失败")
        void extractingFromInvalidTokenShouldFail() {
            assertThrows(AuthException.class, () -> jwtTokenService.getUserIdFromToken("invalid"),
                    "从无效 Token 提取信息应失败");
        }
    }

    @Nested
    @DisplayName("Token 过期测试")
    class TokenExpirationTests {

        @Test
        @DisplayName("刚生成的 Token 不应过期")
        void freshTokenShouldNotBeExpired() {
            User user = new User("testuser", "password", "test@example.com");
            String token = jwtTokenService.generateToken(user);

            assertFalse(jwtTokenService.isTokenExpired(token), "刚生成的 Token 不应过期");
        }

        @Test
        @DisplayName("无效 Token 应被视为过期")
        void invalidTokenShouldBeConsideredExpired() {
            assertTrue(jwtTokenService.isTokenExpired("invalid.token"),
                    "无效 Token 应被视为过期");
        }
    }

    @Nested
    @DisplayName("Token 刷新测试")
    class TokenRefreshTests {

        @Test
        @DisplayName("应能刷新有效的 Token")
        void shouldRefreshValidToken() {
            User user = new User("testuser", "password", "test@example.com");
            String oldToken = jwtTokenService.generateToken(user);

            // 等待 1 秒确保时间戳不同（JWT 时间精度为秒）
            try {
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                // 忽略中断异常
            }

            String newToken = jwtTokenService.refreshToken(oldToken, user);

            assertNotNull(newToken, "新 Token 不应为空");
            assertNotEquals(oldToken, newToken, "新 Token 应与旧 Token 不同");
            assertDoesNotThrow(() -> jwtTokenService.verifyToken(newToken),
                    "新 Token 应能被验证");
        }

        @Test
        @DisplayName("应能刷新过期的 Token")
        void shouldRefreshExpiredToken() throws InterruptedException {
            User user = new User("testuser", "password", "test@example.com");
            // 使用较长的有效期（3 秒）以避免测试不稳定
            JwtTokenService shortLivedService = new JwtTokenService("test-secret", 3000);
            String oldToken = shortLivedService.generateToken(user);

            // 等待 Token 过期
            Thread.sleep(3100);

            // 验证旧 Token 已过期
            assertTrue(shortLivedService.isTokenExpired(oldToken), "旧 Token 应已过期");

            // 刷新 token，生成新的
            String newToken = shortLivedService.refreshToken(oldToken, user);

            assertNotNull(newToken, "新 Token 不应为空");
            assertFalse(shortLivedService.isTokenExpired(newToken), "新 Token 不应过期");
        }
    }

    @Nested
    @DisplayName("默认行为测试")
    class DefaultBehaviorTests {

        @Test
        @DisplayName("未提供密钥时应使用默认密钥")
        void shouldUseDefaultSecretWhenNotProvided() {
            assertDoesNotThrow(() -> new JwtTokenService(null),
                    "未提供密钥时应使用默认密钥");
            assertDoesNotThrow(() -> new JwtTokenService(""),
                    "空字符串密钥时应使用默认密钥");
        }

        @Test
        @DisplayName("未提供有效期时应使用默认有效期")
        void shouldUseDefaultExpirationWhenNotProvided() {
            JwtTokenService service = new JwtTokenService("test-secret");
            User user = new User("testuser", "password", "test@example.com");
            String token = service.generateToken(user);

            assertDoesNotThrow(() -> service.verifyToken(token),
                    "使用默认有效期的 Token 应能被验证");
        }
    }
}