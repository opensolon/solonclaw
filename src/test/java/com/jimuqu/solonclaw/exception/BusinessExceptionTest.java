package com.jimuqu.solonclaw.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusinessException 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("BusinessException 单元测试")
class BusinessExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应能通过 message 和 errorCode 创建异常")
        void shouldCreateExceptionWithMessageAndErrorCode() {
            BusinessException e = new BusinessException("业务错误", 1001);

            assertEquals("业务错误", e.getMessage());
            assertEquals(1001, e.getErrorCode());
            assertEquals(400, e.getHttpStatus(), "默认 HTTP 状态应为 400");
        }

        @Test
        @DisplayName("应能通过 message、errorCode 和 httpStatus 创建异常")
        void shouldCreateExceptionWithMessageErrorCodeAndHttpStatus() {
            BusinessException e = new BusinessException("资源不存在", 4004, 404);

            assertEquals("资源不存在", e.getMessage());
            assertEquals(4004, e.getErrorCode());
            assertEquals(404, e.getHttpStatus());
        }

        @Test
        @DisplayName("异常应继承 RuntimeException")
        void exceptionShouldExtendRuntimeException() {
            BusinessException e = new BusinessException("错误", 1000);
            assertTrue(e instanceof RuntimeException);
        }

        @Test
        @DisplayName("应能获取堆栈跟踪")
        void shouldHaveStackTrace() {
            try {
                throw new BusinessException("测试错误", 1000);
            } catch (BusinessException e) {
                assertNotNull(e.getStackTrace());
                assertTrue(e.getStackTrace().length > 0);
            }
        }
    }

    @Nested
    @DisplayName("静态工厂方法测试")
    class StaticFactoryMethodTests {

        @Test
        @DisplayName("paramError 应创建参数错误异常")
        void paramErrorShouldCreateParameterException() {
            BusinessException e = BusinessException.paramError("参数无效");

            assertEquals("参数无效", e.getMessage());
            assertEquals(3000, e.getErrorCode());
            assertEquals(400, e.getHttpStatus());
        }

        @Test
        @DisplayName("notFound 应创建资源不存在异常")
        void notFoundShouldCreateNotFoundException() {
            BusinessException e = BusinessException.notFound("用户不存在");

            assertEquals("用户不存在", e.getMessage());
            assertEquals(4004, e.getErrorCode());
            assertEquals(404, e.getHttpStatus());
        }

        @Test
        @DisplayName("conflict 应创建冲突异常")
        void conflictShouldCreateConflictException() {
            BusinessException e = BusinessException.conflict("资源已存在");

            assertEquals("资源已存在", e.getMessage());
            assertEquals(4009, e.getErrorCode());
            assertEquals(409, e.getHttpStatus());
        }

        @Test
        @DisplayName("unauthorized 应创建未授权异常")
        void unauthorizedShouldCreateUnauthorizedException() {
            BusinessException e = BusinessException.unauthorized("未登录");

            assertEquals("未登录", e.getMessage());
            assertEquals(4001, e.getErrorCode());
            assertEquals(401, e.getHttpStatus());
        }

        @Test
        @DisplayName("forbidden 应创建禁止访问异常")
        void forbiddenShouldCreateForbiddenException() {
            BusinessException e = BusinessException.forbidden("权限不足");

            assertEquals("权限不足", e.getMessage());
            assertEquals(4003, e.getErrorCode());
            assertEquals(403, e.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能处理空消息")
        void shouldHandleEmptyMessage() {
            BusinessException e = new BusinessException("", 1000);
            assertEquals("", e.getMessage());
        }

        @Test
        @DisplayName("应能处理 null 消息")
        void shouldHandleNullMessage() {
            BusinessException e = new BusinessException(null, 1000);
            assertNull(e.getMessage());
        }

        @Test
        @DisplayName("应能处理零错误码")
        void shouldHandleZeroErrorCode() {
            BusinessException e = new BusinessException("消息", 0);
            assertEquals(0, e.getErrorCode());
        }

        @Test
        @DisplayName("应能处理负数错误码")
        void shouldHandleNegativeErrorCode() {
            BusinessException e = new BusinessException("消息", -1);
            assertEquals(-1, e.getErrorCode());
        }

        @Test
        @DisplayName("应能处理各种 HTTP 状态码")
        void shouldHandleVariousHttpStatusCodes() {
            assertAll("各种 HTTP 状态码",
                    () -> {
                        BusinessException e1 = new BusinessException("", 0, 200);
                        assertEquals(200, e1.getHttpStatus());
                    },
                    () -> {
                        BusinessException e2 = new BusinessException("", 0, 500);
                        assertEquals(500, e2.getHttpStatus());
                    },
                    () -> {
                        BusinessException e3 = new BusinessException("", 0, 599);
                        assertEquals(599, e3.getHttpStatus());
                    }
            );
        }
    }
}
