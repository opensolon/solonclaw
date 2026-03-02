package com.jimuqu.solonclaw.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * <p>
 * 配置 Caffeine 高性能本地缓存
 *
 * @author SolonClaw
 */
@Configuration
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    // 会话历史缓存配置
    private static final Duration SESSION_HISTORY_EXPIRE = Duration.ofMinutes(30);
    private static final int SESSION_HISTORY_MAX_SIZE = 500;

    // 工具列表缓存配置
    private static final Duration TOOLS_LIST_EXPIRE = Duration.ofMinutes(10);
    private static final int TOOLS_LIST_MAX_SIZE = 100;

    // 用户信息缓存配置
    private static final Duration USER_INFO_EXPIRE = Duration.ofHours(2);
    private static final int USER_INFO_MAX_SIZE = 1000;

    // MCP 服务器列表缓存配置
    private static final Duration MCP_SERVERS_EXPIRE = Duration.ofMinutes(5);
    private static final int MCP_SERVERS_MAX_SIZE = 50;

    /**
     * 会话历史缓存
     * <p>
     * 缓存会话历史记录，减少数据库查询
     */
    @Bean(name = "sessionHistoryCache")
    public Cache<String, java.util.List<java.util.Map<String, String>>> sessionHistoryCache() {
        log.info("初始化会话历史缓存: expire={}, maxSize={}",
                SESSION_HISTORY_EXPIRE, SESSION_HISTORY_MAX_SIZE);

        return Caffeine.newBuilder()
                .maximumSize(SESSION_HISTORY_MAX_SIZE)
                .expireAfterWrite(SESSION_HISTORY_EXPIRE)
                .recordStats() // 启用统计，用于监控
                .build();
    }

    /**
     * 工具列表缓存
     * <p>
     * 缓存工具列表，减少反射和扫描开销
     */
    @Bean(name = "toolsListCache")
    public Cache<String, java.util.Map<String, Object>> toolsListCache() {
        log.info("初始化工具列表缓存: expire={}, maxSize={}",
                TOOLS_LIST_EXPIRE, TOOLS_LIST_MAX_SIZE);

        return Caffeine.newBuilder()
                .maximumSize(TOOLS_LIST_MAX_SIZE)
                .expireAfterWrite(TOOLS_LIST_EXPIRE)
                .recordStats()
                .build();
    }

    /**
     * 用户信息缓存
     * <p>
     * 缓存用户信息，减少认证查询
     */
    @Bean(name = "userInfoCache")
    public Cache<String, Object> userInfoCache() {
        log.info("初始化用户信息缓存: expire={}, maxSize={}",
                USER_INFO_EXPIRE, USER_INFO_MAX_SIZE);

        return Caffeine.newBuilder()
                .maximumSize(USER_INFO_MAX_SIZE)
                .expireAfterWrite(USER_INFO_EXPIRE)
                .recordStats()
                .build();
    }

    /**
     * MCP 服务器列表缓存
     * <p>
     * 缓存 MCP 服务器状态，减少频繁状态检查
     */
    @Bean(name = "mcpServersCache")
    public Cache<String, Object> mcpServersCache() {
        log.info("初始化 MCP 服务器缓存: expire={}, maxSize={}",
                MCP_SERVERS_EXPIRE, MCP_SERVERS_MAX_SIZE);

        return Caffeine.newBuilder()
                .maximumSize(MCP_SERVERS_MAX_SIZE)
                .expireAfterWrite(MCP_SERVERS_EXPIRE)
                .recordStats()
                .build();
    }

    /**
     * 通用缓存（用于临时数据）
     */
    @Bean(name = "generalCache")
    public Cache<String, Object> generalCache() {
        log.info("初始化通用缓存");

        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(Duration.ofMinutes(5))
                .recordStats()
                .build();
    }

    /**
     * 获取缓存统计信息
     */
    @Bean
    public CacheStatsProvider cacheStatsProvider() {
        return new CacheStatsProvider();
    }

    /**
     * 缓存统计信息提供者
     */
    public static class CacheStatsProvider {
        private final java.util.Map<String, CacheStats> statsMap = new java.util.concurrent.ConcurrentHashMap<>();

        public void registerCache(String cacheName, Cache<?, ?> cache) {
            statsMap.put(cacheName, new CacheStats(cache));
        }

        public CacheStats getStats(String cacheName) {
            return statsMap.get(cacheName);
        }

        public java.util.Map<String, CacheStats> getAllStats() {
            return new java.util.HashMap<>(statsMap);
        }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final long loadSuccessCount;
        private final long loadFailureCount;
        private final long totalRequestCount;
        private final double hitRate;
        private final long evictionCount;
        private final long size;

        public CacheStats(Cache<?, ?> cache) {
            com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();
            this.hitCount = stats.hitCount();
            this.missCount = stats.missCount();
            this.loadSuccessCount = stats.loadSuccessCount();
            this.loadFailureCount = stats.loadFailureCount();
            this.totalRequestCount = stats.requestCount();
            this.hitRate = stats.hitRate();
            this.evictionCount = stats.evictionCount();
            this.size = cache.estimatedSize();
        }

        public long hitCount() { return hitCount; }
        public long missCount() { return missCount; }
        public long loadSuccessCount() { return loadSuccessCount; }
        public long loadFailureCount() { return loadFailureCount; }
        public long totalRequestCount() { return totalRequestCount; }
        public double hitRate() { return hitRate; }
        public long evictionCount() { return evictionCount; }
        public long size() { return size; }

        public String toSummaryString() {
            return String.format(
                    "CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, size=%d, evictions=%d}",
                    hitCount, missCount, hitRate * 100, size, evictionCount
            );
        }
    }
}