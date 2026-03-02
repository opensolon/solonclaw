package com.jimuqu.solonclaw.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 速率限制器
 * <p>
 * 基于令牌桶算法实现限流功能
 *
 * @author SolonClaw
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * 令牌桶缓存
     */
    private final Cache<String, TokenBucket> bucketCache;

    /**
     * 令牌桶
     */
    private static class TokenBucket {
        private final long capacity;
        private final long refillRate; // 每秒补充的令牌数
        private final AtomicLong tokens;
        private final AtomicLong lastRefillTime;

        TokenBucket(long capacity, long refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }

        /**
         * 尝试获取令牌
         */
        boolean tryAcquire(long permits) {
            refill();

            while (true) {
                long current = tokens.get();
                if (current < permits) {
                    return false;
                }

                if (tokens.compareAndSet(current, current - permits)) {
                    return true;
                }
            }
        }

        /**
         * 补充令牌
         */
        private void refill() {
            long now = System.currentTimeMillis();
            long lastRefill = lastRefillTime.get();
            long elapsedMs = now - lastRefill;

            if (elapsedMs < 100) {
                return; // 不足 100ms 不补充
            }

            if (lastRefillTime.compareAndSet(lastRefill, now)) {
                // 计算应该补充的令牌数
                long tokensToAdd = (elapsedMs * refillRate) / 1000;
                if (tokensToAdd > 0) {
                    while (true) {
                        long current = tokens.get();
                        long newTokens = Math.min(capacity, current + tokensToAdd);
                        if (tokens.compareAndSet(current, newTokens)) {
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 默认构造器
     */
    public RateLimiter() {
        // 创建专用的限流缓存
        this.bucketCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
        log.info("限流器已初始化");
    }

    /**
     * 尝试获取许可
     *
     * @param key         限流键
     * @param maxRequests 最大请求数（即桶容量）
     * @param windowSizeMs 时间窗口大小（毫秒）
     * @return 是否获取成功
     */
    public boolean tryAcquire(String key, int maxRequests, long windowSizeMs) {
        // 计算补充速率
        long refillRate = (maxRequests * 1000L) / windowSizeMs;

        TokenBucket bucket = bucketCache.get(key,
                k -> new TokenBucket(maxRequests, refillRate));

        return bucket.tryAcquire(1);
    }

    /**
     * 尝试获取许可（默认 1 秒窗口）
     *
     * @param key         限流键
     * @param maxRequests 最大请求数
     * @return 是否获取成功
     */
    public boolean tryAcquire(String key, int maxRequests) {
        return tryAcquire(key, maxRequests, 1000);
    }

    /**
     * 重置指定键的限流
     *
     * @param key 限流键
     */
    public void reset(String key) {
        bucketCache.invalidate(key);
    }

    /**
     * 清空所有限流记录
     */
    public void clear() {
        bucketCache.invalidateAll();
    }

    /**
     * 获取缓存统计信息
     */
    public String getStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = bucketCache.stats();
        long size = bucketCache.estimatedSize();
        return String.format("RateLimiterStats{size=%d, hits=%d, misses=%d, hitRate=%.2f%%}",
                size, stats.hitCount(), stats.missCount(), stats.hitRate() * 100);
    }
}