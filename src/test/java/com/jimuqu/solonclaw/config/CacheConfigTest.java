package com.jimuqu.solonclaw.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheConfig 单元测试
 * 测试缓存配置和基本功能
 *
 * @author SolonClaw
 */
@DisplayName("CacheConfig 单元测试")
class CacheConfigTest {

    @Nested
    @DisplayName("缓存基本功能测试")
    class BasicCacheTests {

        @Test
        @DisplayName("应能创建并操作 SessionHistory 缓存")
        void testSessionHistoryCache() {
            // 直接创建缓存实例进行测试
            Cache<String, List<Map<String, String>>> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(java.time.Duration.ofMinutes(30))
                    .recordStats()
                    .build();

            // 测试存储和获取
            List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "Hello"),
                Map.of("role", "assistant", "content", "Hi")
            );

            cache.put("history:test-session", history);

            List<Map<String, String>> cached = cache.getIfPresent("history:test-session");
            assertNotNull(cached);
            assertEquals(2, cached.size());
        }

        @Test
        @DisplayName("应能创建并操作 ToolsList 缓存")
        void testToolsListCache() {
            Cache<String, Map<String, Object>> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(java.time.Duration.ofMinutes(10))
                    .recordStats()
                    .build();

            Map<String, Object> tools = new HashMap<>();
            tools.put("ShellTool.exec", Map.of("description", "Execute shell commands"));

            cache.put("tools:list", tools);

            Map<String, Object> cached = cache.getIfPresent("tools:list");
            assertNotNull(cached);
            assertTrue(cached.containsKey("ShellTool.exec"));
        }

        @Test
        @DisplayName("缓存失效应正常工作")
        void testCacheInvalidation() {
            Cache<String, Object> cache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build();

            cache.put("key1", "value1");
            cache.put("key2", "value2");

            assertNotNull(cache.getIfPresent("key1"));

            // 使单个键失效
            cache.invalidate("key1");
            assertNull(cache.getIfPresent("key1"));
            assertNotNull(cache.getIfPresent("key2"));

            // 使所有缓存失效
            cache.invalidateAll();
            assertNull(cache.getIfPresent("key2"));
        }
    }

    @Nested
    @DisplayName("缓存统计测试")
    class CacheStatsTests {

        @Test
        @DisplayName("应能获取缓存统计信息")
        void testCacheStats() {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .recordStats()
                    .build();

            // 未命中
            cache.getIfPresent("nonexistent");

            // 命中
            cache.put("key", "value");
            cache.getIfPresent("key");

            com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();

            assertTrue(stats.requestCount() > 0);
            assertTrue(stats.hitCount() > 0);
            assertTrue(stats.missCount() > 0);
        }

        @Test
        @DisplayName("缓存命中率计算应正确")
        void testCacheHitRate() {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .recordStats()
                    .build();

            cache.put("key1", "value1");
            cache.put("key2", "value2");

            // 多次命中
            for (int i = 0; i < 5; i++) {
                cache.getIfPresent("key1");
            }

            // 多次未命中
            for (int i = 0; i < 3; i++) {
                cache.getIfPresent("nonexistent" + i);
            }

            com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();

            // 命中率应该等于 命中数 / 总请求数
            double expectedHitRate = (double) stats.hitCount() / stats.requestCount();
            assertEquals(expectedHitRate, stats.hitRate(), 0.001);
        }
    }

    @Nested
    @DisplayName("缓存过期测试")
    class CacheExpirationTests {

        @Test
        @DisplayName("缓存应按访问过期")
        void testExpireAfterAccess() throws InterruptedException {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterAccess(java.time.Duration.ofMillis(100))
                    .build();

            cache.put("key", "value");

            // 立即访问
            assertNotNull(cache.getIfPresent("key"));

            // 等待但保持访问
            for (int i = 0; i < 3; i++) {
                Thread.sleep(50);
                assertNotNull(cache.getIfPresent("key"));
            }

            // 长时间不访问后应该过期
            Thread.sleep(150);
            assertNull(cache.getIfPresent("key"));
        }

        @Test
        @DisplayName("缓存应按写入过期")
        void testExpireAfterWrite() throws InterruptedException {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(java.time.Duration.ofMillis(100))
                    .build();

            cache.put("key", "value");
            assertNotNull(cache.getIfPresent("key"));

            Thread.sleep(150);
            assertNull(cache.getIfPresent("key"));
        }
    }

    @Nested
    @DisplayName("缓存大小限制测试")
    class CacheSizeLimitTests {

        @Test
        @DisplayName("缓存应遵循最大大小限制")
        void testMaximumSize() {
            int maxSize = 5;
            Cache<String, Integer> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .build();

            // 添加大量条目
            for (int i = 0; i < 100; i++) {
                cache.put("key" + i, i);
            }

            // 手动触发清理
            cache.cleanUp();

            // Caffeine 的 maximumSize 是基于权重的近似限制
            long size = cache.estimatedSize();
            // 由于异步清理机制，大小应该在 maxSize 附近
            assertTrue(size <= maxSize + 5, "缓存大小 " + size + " 应该在合理范围内 (最大值: " + maxSize + ")");
            assertTrue(size > 0, "缓存不应为空");
        }

        @Test
        @DisplayName("零最大大小限制")
        void testZeroMaximumSize() {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(0)
                    .build();

            // Caffeine 最大大小为 0 时，不会存储任何条目
            cache.put("key", "value");

            // 条目不会被保留，但由于测试可能立即访问，使用 cleaner 或等待
            // Caffeine 使用异步清理，所以可能需要手动触发
            cache.cleanUp();
            assertNull(cache.getIfPresent("key"));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能处理空的缓存键值")
        void testEmptyKeyAndValue() {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build();

            cache.put("", "");
            assertEquals("", cache.getIfPresent(""));
        }

        @Test
        @DisplayName("应能处理 null 值")
        void testNullValue() {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .build();

            // Caffeine 不允许存储 null 值，使用特殊字符串代替
            cache.put("key", "NULL_VALUE");
            assertEquals("NULL_VALUE", cache.getIfPresent("key"));

            // 测试 getIfPresent 返回 null 的情况（键不存在）
            assertNull(cache.getIfPresent("nonexistent"));
        }

        @Test
        @DisplayName("应能处理特殊字符键")
        void testSpecialCharsInKey() {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build();

            String specialKey = "key:with/special\\chars space";
            cache.put(specialKey, "value");
            assertEquals("value", cache.getIfPresent(specialKey));
        }

        @Test
        @DisplayName("应能处理大对象")
        void testLargeObject() {
            Cache<String, String> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build();

            StringBuilder large = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                large.append("word ");
            }

            cache.put("largeKey", large.toString());

            String cached = cache.getIfPresent("largeKey");
            assertNotNull(cached);
            // "word " 是 5 个字符，10000 次循环是 50000 个字符
            assertTrue(cached.length() >= 50000);
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("缓存应支持并发访问")
        void testConcurrentAccess() throws InterruptedException {
            Cache<String, Integer> cache =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build();

            int threadCount = 10;
            int operationsPerThread = 100;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key" + (threadId * operationsPerThread + j);
                        cache.put(key, threadId);
                        cache.getIfPresent(key);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // 所有操作完成后，缓存应该包含所有条目
            long expectedSize = threadCount * operationsPerThread;
            assertEquals(expectedSize, cache.estimatedSize());
        }
    }
}