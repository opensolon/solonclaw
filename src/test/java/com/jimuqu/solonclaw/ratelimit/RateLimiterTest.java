package com.jimuqu.solonclaw.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimiter 单元测试
 * 测试限流器的基本功能和并发性能
 *
 * @author SolonClaw
 */
@DisplayName("RateLimiter 单元测试")
class RateLimiterTest {

    @Nested
    @DisplayName("基本限流功能测试")
    class BasicRateLimitTests {

        @Test
        @DisplayName("应能允许在限制内的请求")
        void testAllowWithinLimit() {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-1";

            // 允许 10 次/秒
            for (int i = 0; i < 10; i++) {
                assertTrue(limiter.tryAcquire(key, 10));
            }
        }

        @Test
        @DisplayName("应拒绝超过限制的请求")
        void testRejectExceedLimit() {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-2";

            // 允许 5 次/秒
            for (int i = 0; i < 5; i++) {
                assertTrue(limiter.tryAcquire(key, 5));
            }

            // 第 6 次应该被拒绝
            assertFalse(limiter.tryAcquire(key, 5));
        }

        @Test
        @DisplayName("不同的限流键应该独立计数")
        void testIndependentKeys() {
            RateLimiter limiter = new RateLimiter();
            String key1 = "user-1";
            String key2 = "user-2";

            // 两个键各允许 3 次/秒
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.tryAcquire(key1, 3));
                assertTrue(limiter.tryAcquire(key2, 3));
            }

            // 两个键的第 4 次都应该被拒绝
            assertFalse(limiter.tryAcquire(key1, 3));
            assertFalse(limiter.tryAcquire(key2, 3));
        }

        @Test
        @DisplayName("重置限流键后应允许新的请求")
        void testReset() {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-3";

            // 允许 3 次/秒
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.tryAcquire(key, 3));
            }

            // 第 4 次应该被拒绝
            assertFalse(limiter.tryAcquire(key, 3));

            // 重置后应该允许新的请求
            limiter.reset(key);
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.tryAcquire(key, 3));
            }
        }
    }

    @Nested
    @DisplayName("时间窗口测试")
    class TimeWindowTests {

        @Test
        @DisplayName("应能在窗口过期后恢复请求")
        void testWindowExpiration() throws InterruptedException {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-4";

            // 允许 2 次/100 毫秒
            assertTrue(limiter.tryAcquire(key, 2, 100));
            assertTrue(limiter.tryAcquire(key, 2, 100));
            assertFalse(limiter.tryAcquire(key, 2, 100));

            // 等待窗口过期
            Thread.sleep(150);

            // 窗口过期后应该允许新的请求
            assertTrue(limiter.tryAcquire(key, 2, 100));
            assertTrue(limiter.tryAcquire(key, 2, 100));
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("应能安全处理并发请求")
        void testConcurrentRequests() throws InterruptedException {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-5";

            int threadCount = 10;
            int requestsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger allowedCount = new AtomicInteger(0);
            AtomicInteger deniedCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < requestsPerThread; j++) {
                            if (limiter.tryAcquire(key, 50)) {
                                allowedCount.incrementAndGet();
                            } else {
                                deniedCount.incrementAndGet();
                            }
                            // 小延迟避免全部请求在同一瞬间到达
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // 由于有延迟，应该有部分请求被允许，部分被拒绝
            int totalRequests = allowedCount.get() + deniedCount.get();
            assertEquals(threadCount * requestsPerThread, totalRequests);

            // 验证至少有请求被处理
            assertTrue(allowedCount.get() > 0);
            assertTrue(deniedCount.get() > 0);
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能处理零限制（拒绝所有请求）")
        void testZeroLimit() {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-6";

            assertFalse(limiter.tryAcquire(key, 0));
        }

        @Test
        @DisplayName("应能处理负限制（视为零）")
        void testNegativeLimit() {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-7";

            assertFalse(limiter.tryAcquire(key, -5));
        }

        @Test
        @DisplayName("应能处理非常大的限制（几乎不限制）")
        void testLargeLimit() {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-8";

            // 允许 10000 次/秒
            for (int i = 0; i < 1000; i++) {
                assertTrue(limiter.tryAcquire(key, 10000));
            }
        }

        @Test
        @DisplayName("应能处理空键名")
        void testEmptyKey() {
            RateLimiter limiter = new RateLimiter();
            String key = "";

            assertTrue(limiter.tryAcquire(key, 1));
            assertFalse(limiter.tryAcquire(key, 1));
        }

        @Test
        @DisplayName("清空所有限流记录后应允许所有请求")
        void testClearAll() {
            RateLimiter limiter = new RateLimiter();

            // 创建多个限流键并达到限制
            for (int i = 0; i < 10; i++) {
                String key = "key-" + i;
                for (int j = 0; j < 5; j++) {
                    assertTrue(limiter.tryAcquire(key, 5));
                }
            }

            // 清空所有限流记录
            limiter.clear();

            // 清空后应该允许新的请求
            for (int i = 0; i < 10; i++) {
                String key = "key-" + i;
                assertTrue(limiter.tryAcquire(key, 5));
            }
        }
    }

    @Nested
    @DisplayName("统计信息测试")
    class StatsTests {

        @Test
        @DisplayName("应能获取统计信息")
        void testGetStats() {
            RateLimiter limiter = new RateLimiter();
            String key = "test-key-9";

            for (int i = 0; i < 10; i++) {
                limiter.tryAcquire(key, 5);
            }

            String stats = limiter.getStats();
            assertNotNull(stats);
            assertTrue(stats.contains("RateLimiterStats"));
        }
    }
}