package com.jimuqu.solonclaw.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TempTokenService 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("TempTokenService 单元测试")
class TempTokenServiceTest {

    private TempTokenService tempTokenService;

    @BeforeEach
    void setUp() {
        tempTokenService = new TempTokenService();
    }

    @Nested
    @DisplayName("Token 生成测试")
    class TokenGenerationTests {

        @Test
        @DisplayName("应能生成有效的 token")
        void shouldGenerateValidToken() {
            String filePath = "/test/image.png";
            int seconds = 60;

            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, seconds);

            assertNotNull(result, "结果不应为空");
            assertNotNull(result.getToken(), "token 不应为空");
            assertNotNull(result.getRandomFileName(), "随机文件名不应为空");
            assertFalse(result.getToken().isEmpty(), "token 不应为空");
            assertFalse(result.getRandomFileName().isEmpty(), "随机文件名不应为空");
        }

        @Test
        @DisplayName("使用 Duration 生成 token")
        void shouldGenerateTokenWithDuration() {
            String filePath = "/test/image.jpg";
            Duration duration = Duration.ofMinutes(5);

            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, duration);

            assertNotNull(result, "结果不应为空");
            assertNotNull(result.getToken(), "token 不应为空");
            assertNotNull(result.getRandomFileName(), "随机文件名不应为空");
        }

        @Test
        @DisplayName("随机文件名应保持原文件扩展名")
        void randomFileNameShouldKeepOriginalExtension() {
            String filePath = "/test/image.png";

            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 60);

            assertTrue(result.getRandomFileName().endsWith(".png"), "应保持原文件扩展名");
        }

        @Test
        @DisplayName("应处理没有扩展名的文件")
        void shouldHandleFileWithoutExtension() {
            String filePath = "/test/file";

            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 60);

            assertNotNull(result, "结果不应为空");
            assertFalse(result.getRandomFileName().endsWith("."), "不应包含后缀点");
        }

        @Test
        @DisplayName("应处理多个扩展名的文件")
        void shouldHandleFileWithMultipleDots() {
            String filePath = "/test/file.name.jpg";

            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 60);

            assertTrue(result.getRandomFileName().endsWith(".jpg"), "应使用最后一个扩展名");
        }

        @Test
        @DisplayName("多次生成的 token 应不相同")
        void multipleTokensShouldBeDifferent() {
            String filePath = "/test/image.png";

            TempTokenService.TokenResult result1 = tempTokenService.generateToken(filePath, 60);
            TempTokenService.TokenResult result2 = tempTokenService.generateToken(filePath, 60);

            assertNotEquals(result1.getToken(), result2.getToken(), "token 应不相同");
            assertNotEquals(result1.getRandomFileName(), result2.getRandomFileName(), "随机文件名应不相同");
        }

        @Test
        @DisplayName("应处理空路径")
        void shouldHandleEmptyPath() {
            String filePath = "";

            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 60);

            assertNotNull(result, "结果不应为空");
        }
    }

    @Nested
    @DisplayName("Token 验证测试")
    class TokenVerificationTests {

        @Test
        @DisplayName("有效的 token 应能通过验证")
        void validTokenShouldPassVerification() {
            String filePath = "/test/image.png";
            int seconds = 60;
            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, seconds);

            String verifiedPath = tempTokenService.verifyAndGetFilePath(
                    result.getToken(), result.getRandomFileName());

            assertEquals(filePath, verifiedPath, "应返回正确的文件路径");
        }

        @Test
        @DisplayName("null token 应验证失败")
        void nullTokenShouldFailVerification() {
            String verifiedPath = tempTokenService.verifyAndGetFilePath(null, "filename.png");

            assertNull(verifiedPath, "null token 应返回 null");
        }

        @Test
        @DisplayName("空 token 应验证失败")
        void emptyTokenShouldFailVerification() {
            String verifiedPath = tempTokenService.verifyAndGetFilePath("", "filename.png");

            assertNull(verifiedPath, "空 token 应返回 null");
        }

        @Test
        @DisplayName("null 文件名应验证失败")
        void nullFileNameShouldFailVerification() {
            String filePath = "/test/image.png";
            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 60);

            String verifiedPath = tempTokenService.verifyAndGetFilePath(result.getToken(), null);

            assertNull(verifiedPath, "null 文件名应返回 null");
        }

        @Test
        @DisplayName("错误的文件名应验证失败")
        void wrongFileNameShouldFailVerification() {
            String filePath = "/test/image.png";
            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 60);

            String verifiedPath = tempTokenService.verifyAndGetFilePath(result.getToken(), "wrong.png");

            assertNull(verifiedPath, "错误的文件名应返回 null");
        }

        @Test
        @DisplayName("错误的 token 应验证失败")
        void wrongTokenShouldFailVerification() {
            String filePath = "/test/image.png";
            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 60);

            String verifiedPath = tempTokenService.verifyAndGetFilePath("wrongtoken", result.getRandomFileName());

            assertNull(verifiedPath, "错误的 token 应返回 null");
        }

        @Test
        @DisplayName("过期的 token 应验证失败")
        void expiredTokenShouldFailVerification() throws InterruptedException {
            String filePath = "/test/image.png";
            // 使用非常短的有效期（100 毫秒）
            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 0);

            // 等待过期
            Thread.sleep(150);

            String verifiedPath = tempTokenService.verifyAndGetFilePath(
                    result.getToken(), result.getRandomFileName());

            assertNull(verifiedPath, "过期的 token 应返回 null");
        }

        @Test
        @DisplayName("验证过期的 token 后应自动清理")
        void expiredTokenShouldBeAutoCleaned() throws InterruptedException {
            String filePath = "/test/image.png";
            TempTokenService.TokenResult result = tempTokenService.generateToken(filePath, 0);

            int countBefore = tempTokenService.getTokenCount();

            // 等待过期
            Thread.sleep(150);

            // 触发验证，会清理过期 token
            tempTokenService.verifyAndGetFilePath(result.getToken(), result.getRandomFileName());

            int countAfter = tempTokenService.getTokenCount();

            assertTrue(countAfter < countBefore, "过期 token 应被清理");
        }
    }

    @Nested
    @DisplayName("Token 清理测试")
    class TokenCleanupTests {

        @Test
        @DisplayName("应能清理所有过期的 token")
        void shouldCleanAllExpiredTokens() throws InterruptedException {
            // 生成多个 token，部分过期
            tempTokenService.generateToken("/test1.png", 0);  // 立即过期
            tempTokenService.generateToken("/test2.png", 0);  // 立即过期
            tempTokenService.generateToken("/test3.png", 60); // 60 秒后过期

            Thread.sleep(150);

            int countBefore = tempTokenService.getTokenCount();
            tempTokenService.cleanupExpiredTokens();
            int countAfter = tempTokenService.getTokenCount();

            assertTrue(countAfter < countBefore, "应清理过期 token");
            assertEquals(1, countAfter, "应只剩一个未过期的 token");
        }

        @Test
        @DisplayName("没有过期 token 时清理不应报错")
        void cleanupWithoutExpiredTokensShouldNotThrow() {
            tempTokenService.generateToken("/test.png", 60);

            assertDoesNotThrow(() -> tempTokenService.cleanupExpiredTokens(),
                    "没有过期 token 时清理不应报错");
        }

        @Test
        @DisplayName("空列表时清理不应报错")
        void cleanupOnEmptyListShouldNotThrow() {
            assertDoesNotThrow(() -> tempTokenService.cleanupExpiredTokens(),
                    "空列表时清理不应报错");
        }
    }

    @Nested
    @DisplayName("Token 计数测试")
    class TokenCountTests {

        @Test
        @DisplayName("初始 token 数量应为 0")
        void initialTokenCountShouldBeZero() {
            assertEquals(0, tempTokenService.getTokenCount());
        }

        @Test
        @DisplayName("生成 token 后数量应增加")
        void tokenCountShouldIncreaseAfterGeneration() {
            int countBefore = tempTokenService.getTokenCount();

            tempTokenService.generateToken("/test.png", 60);

            int countAfter = tempTokenService.getTokenCount();
            assertEquals(countBefore + 1, countAfter, "token 数量应增加 1");
        }

        @Test
        @DisplayName("生成多个 token 后数量应正确")
        void tokenCountShouldBeCorrectForMultipleTokens() {
            tempTokenService.generateToken("/test1.png", 60);
            tempTokenService.generateToken("/test2.png", 60);
            tempTokenService.generateToken("/test3.png", 60);

            assertEquals(3, tempTokenService.getTokenCount());
        }
    }

    @Nested
    @DisplayName("TokenResult 测试")
    class TokenResultTests {

        @Test
        @DisplayName("TokenResult getter 应返回正确值")
        void tokenResultGettersShouldReturnCorrectValues() {
            String token = "test-token-123";
            String fileName = "random-name.png";
            TempTokenService.TokenResult result = new TempTokenService.TokenResult(token, fileName);

            assertEquals(token, result.getToken());
            assertEquals(fileName, result.getRandomFileName());
        }

        @Test
        @DisplayName("TokenResult 应处理空值")
        void tokenResultShouldHandleNullValues() {
            TempTokenService.TokenResult result = new TempTokenService.TokenResult(null, null);

            assertNull(result.getToken());
            assertNull(result.getRandomFileName());
        }

        @Test
        @DisplayName("TokenResult 应处理空字符串")
        void tokenResultShouldHandleEmptyStrings() {
            TempTokenService.TokenResult result = new TempTokenService.TokenResult("", "");

            assertEquals("", result.getToken());
            assertEquals("", result.getRandomFileName());
        }
    }
}