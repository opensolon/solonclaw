package com.jimuqu.solonclaw.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthException 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("AuthException 单元测试")
class AuthExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应能通过 message 创建异常")
        void shouldCreateExceptionWithMessage() {
            AuthException e = new AuthException("认证失败");

            assertEquals("认证失败", e.getMessage());
            assertEquals(AuthException.ErrorCode.UNKNOWN_ERROR, e.getErrorCode());
        }

        @Test
        @DisplayName("应能通过 message 和 errorCode 创建异常")
        void shouldCreateExceptionWithMessageAndErrorCode() {
            AuthException e = new AuthException("用户不存在", AuthException.ErrorCode.USER_NOT_FOUND);

            assertEquals("用户不存在", e.getMessage());
            assertEquals(AuthException.ErrorCode.USER_NOT_FOUND, e.getErrorCode());
        }

        @Test
        @DisplayName("异常应继承 RuntimeException")
        void exceptionShouldExtendRuntimeException() {
            AuthException e = new AuthException("错误");
            assertTrue(e instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("ErrorCode 枚举测试")
    class ErrorCodeEnumTests {

        @Test
        @DisplayName("应包含所有定义的错误码")
        void shouldContainAllDefinedErrorCodes() {
            AuthException.ErrorCode[] codes = AuthException.ErrorCode.values();
            assertEquals(10, codes.length, "应包含 10 个错误码");
        }

        @Test
        @DisplayName("UNKNOWN_ERROR 应返回正确的值")
        void unknownErrorShouldReturnCorrectValues() {
            assertEquals(4000, AuthException.ErrorCode.UNKNOWN_ERROR.getCode());
            assertEquals("未知错误", AuthException.ErrorCode.UNKNOWN_ERROR.getMessage());
        }

        @Test
        @DisplayName("USER_NOT_FOUND 应返回正确的值")
        void userNotFoundShouldReturnCorrectValues() {
            assertEquals(4001, AuthException.ErrorCode.USER_NOT_FOUND.getCode());
            assertEquals("用户不存在", AuthException.ErrorCode.USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("USERNAME_EXISTS 应返回正确的值")
        void usernameExistsShouldReturnCorrectValues() {
            assertEquals(4002, AuthException.ErrorCode.USERNAME_EXISTS.getCode());
            assertEquals("用户名已存在", AuthException.ErrorCode.USERNAME_EXISTS.getMessage());
        }

        @Test
        @DisplayName("INVALID_PASSWORD 应返回正确的值")
        void invalidPasswordShouldReturnCorrectValues() {
            assertEquals(4003, AuthException.ErrorCode.INVALID_PASSWORD.getCode());
            assertEquals("密码错误", AuthException.ErrorCode.INVALID_PASSWORD.getMessage());
        }

        @Test
        @DisplayName("INVALID_TOKEN 应返回正确的值")
        void invalidTokenShouldReturnCorrectValues() {
            assertEquals(4004, AuthException.ErrorCode.INVALID_TOKEN.getCode());
            assertEquals("Token 无效", AuthException.ErrorCode.INVALID_TOKEN.getMessage());
        }

        @Test
        @DisplayName("TOKEN_EXPIRED 应返回正确的值")
        void tokenExpiredShouldReturnCorrectValues() {
            assertEquals(4005, AuthException.ErrorCode.TOKEN_EXPIRED.getCode());
            assertEquals("Token 已过期", AuthException.ErrorCode.TOKEN_EXPIRED.getMessage());
        }

        @Test
        @DisplayName("INVALID_API_KEY 应返回正确的值")
        void invalidApiKeyShouldReturnCorrectValues() {
            assertEquals(4006, AuthException.ErrorCode.INVALID_API_KEY.getCode());
            assertEquals("API 密钥无效", AuthException.ErrorCode.INVALID_API_KEY.getMessage());
        }

        @Test
        @DisplayName("INSUFFICIENT_PERMISSION 应返回正确的值")
        void insufficientPermissionShouldReturnCorrectValues() {
            assertEquals(4007, AuthException.ErrorCode.INSUFFICIENT_PERMISSION.getCode());
            assertEquals("权限不足", AuthException.ErrorCode.INSUFFICIENT_PERMISSION.getMessage());
        }

        @Test
        @DisplayName("ACCOUNT_DISABLED 应返回正确的值")
        void accountDisabledShouldReturnCorrectValues() {
            assertEquals(4008, AuthException.ErrorCode.ACCOUNT_DISABLED.getCode());
            assertEquals("账户已被禁用", AuthException.ErrorCode.ACCOUNT_DISABLED.getMessage());
        }

        @Test
        @DisplayName("INVALID_OLD_PASSWORD 应返回正确的值")
        void invalidOldPasswordShouldReturnCorrectValues() {
            assertEquals(4009, AuthException.ErrorCode.INVALID_OLD_PASSWORD.getCode());
            assertEquals("原密码错误", AuthException.ErrorCode.INVALID_OLD_PASSWORD.getMessage());
        }

        @Test
        @DisplayName("所有错误码应唯一")
        void allErrorCodesShouldBeUnique() {
            AuthException.ErrorCode[] codes = AuthException.ErrorCode.values();
            long uniqueCodes = java.util.Arrays.stream(codes)
                    .map(AuthException.ErrorCode::getCode)
                    .distinct()
                    .count();
            assertEquals(codes.length, uniqueCodes, "所有错误码应唯一");
        }
    }

    @Nested
    @DisplayName("枚举valueOf测试")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf 应返回正确的枚举值")
        void valueOfShouldReturnCorrectEnum() {
            assertEquals(AuthException.ErrorCode.USER_NOT_FOUND, AuthException.ErrorCode.valueOf("USER_NOT_FOUND"));
            assertEquals(AuthException.ErrorCode.INVALID_TOKEN, AuthException.ErrorCode.valueOf("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("valueOf 对于无效值应抛出异常")
        void valueOfShouldThrowExceptionForInvalidValue() {
            assertThrows(IllegalArgumentException.class, () -> AuthException.ErrorCode.valueOf("INVALID"));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能处理空消息")
        void shouldHandleEmptyMessage() {
            AuthException e = new AuthException("");
            assertEquals("", e.getMessage());
        }

        @Test
        @DisplayName("应能处理 null 消息")
        void shouldHandleNullMessage() {
            AuthException e = new AuthException(null);
            assertNull(e.getMessage());
        }

        @Test
        @DisplayName("应能处理 null ErrorCode")
        void shouldHandleNullErrorCode() {
            assertDoesNotThrow(() -> new AuthException("消息", null),
                    "传入 null ErrorCode 不应抛出异常");
        }
    }
}