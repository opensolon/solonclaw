package com.jimuqu.solonclaw.ratelimit;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.aspect.Interceptor;
import org.noear.solon.core.aspect.Invocation;
import org.noear.solon.core.handle.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流拦截器
 * <p>
 * 拦截带有 @RateLimit 注解的方法，执行限流逻辑
 *
 * @author SolonClaw
 */
@Component
public class RateLimitInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /**
     * 限流器实例
     */
    @Inject
    private RateLimiter rateLimiter;

    /**
     * IP 违规计数器（用于防刷）
     */
    private final ConcurrentMap<String, AtomicInteger> ipViolationCounters = new ConcurrentHashMap<>();

    /**
     * IP 封禁记录：IP -> 封禁到期时间戳
     */
    private final ConcurrentMap<String, Long> ipBlockList = new ConcurrentHashMap<>();

    /**
     * 封禁时长（毫秒）
     */
    private static final long BLOCK_DURATION_MS = Duration.ofHours(1).toMillis();

    /**
     * 触发封禁的违规次数阈值
     */
    private static final int VIOLATION_THRESHOLD = 10;

    @Override
    public Object doIntercept(Invocation inv) throws Throwable {
        RateLimit rateLimit = inv.method().getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            rateLimit = inv.target().getClass().getAnnotation(RateLimit.class);
        }

        if (rateLimit == null) {
            // 没有限流注解，直接执行
            return inv.invoke();
        }

        // 获取当前上下文
        Context ctx = Context.current();

        // 生成限流键
        String key = generateKey(ctx, inv, rateLimit);

        // 尝试获取许可
        int maxRequests = rateLimit.value();
        long windowSizeMs = (long) rateLimit.window() * 1000;

        // 检查 IP 是否被封禁（仅当限流类型是 IP 时）
        if (rateLimit.type() == RateLimit.LimitType.IP) {
            String ip = getClientIp(ctx);
            if (isIpBlocked(ip)) {
                log.warn("IP {} 已被封禁，拒绝访问", ip);
                throw new RateLimitException("IP已被封禁，请稍后再试", -1);
            }
        }

        boolean allowed = rateLimiter.tryAcquire(key, maxRequests, windowSizeMs);

        if (!allowed) {
            String message = String.format("请求过于频繁，请 %d 秒后再试", rateLimit.window());
            if (rateLimit.log()) {
                log.warn("限流触发: key={}, maxRequests={}, window={}s", key, maxRequests, rateLimit.window());
            }

            // 如果是 IP 限流，记录违规次数
            if (rateLimit.type() == RateLimit.LimitType.IP) {
                recordIpViolation(getClientIp(ctx));
            }

            throw new RateLimitException(message, rateLimit.window());
        }

        // 允许访问，执行原方法
        return inv.invoke();
    }

    /**
     * 生成限流键
     */
    private String generateKey(Context ctx, Invocation inv, RateLimit rateLimit) {
        String customKey = rateLimit.key();

        if (!customKey.isEmpty() && rateLimit.type() == RateLimit.LimitType.CUSTOM) {
            return "ratelimit:custom:" + customKey;
        }

        String basePath = "ratelimit:";

        return switch (rateLimit.type()) {
            case IP -> basePath + "ip:" + getClientIp(ctx);
            case USER -> basePath + "user:" + getUserId(ctx);
            case PATH -> basePath + "path:" + getPath(ctx);
            case DEFAULT -> basePath + "method:" + getMethodKey(inv);
            case CUSTOM -> basePath + "custom:" + customKey;
        };
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(Context ctx) {
        if (ctx == null) {
            return "unknown";
        }

        String xForwardedFor = ctx.header("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // 取第一个 IP
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = ctx.header("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        String remoteIp = ctx.realIp();
        if (remoteIp != null) {
            return remoteIp;
        }

        return "unknown";
    }

    /**
     * 获取用户 ID
     */
    private String getUserId(Context ctx) {
        if (ctx == null) {
            return "anonymous";
        }

        // 从请求头获取用户 ID（假设通过 JWT 传递）
        String userId = ctx.header("X-User-Id");
        if (userId != null) {
            return userId;
        }

        // 从 session 获取
        Object sessionUserId = ctx.session("userId");
        if (sessionUserId != null) {
            return sessionUserId.toString();
        }

        return "anonymous";
    }

    /**
     * 获取请求路径
     */
    private String getPath(Context ctx) {
        if (ctx == null) {
            return "unknown";
        }

        String path = ctx.path();
        if (path != null) {
            return path;
        }
        return "unknown";
    }

    /**
     * 获取方法键
     */
    private String getMethodKey(Invocation inv) {
        return inv.target().getClass().getSimpleName() + "." + inv.method().getMethod().getName();
    }

    /**
     * 记录 IP 违规（防刷机制）
     */
    private void recordIpViolation(String ip) {
        if ("unknown".equals(ip)) {
            return;
        }

        AtomicInteger counter = ipViolationCounters.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int violations = counter.incrementAndGet();

        // 检查是否达到封禁阈值
        if (violations >= VIOLATION_THRESHOLD) {
            long blockUntil = System.currentTimeMillis() + BLOCK_DURATION_MS;
            ipBlockList.put(ip, blockUntil);
            ipViolationCounters.remove(ip);
            log.warn("IP {} 触发防刷，已封禁 {} 小时", ip, BLOCK_DURATION_MS / 3600000);
        }
    }

    /**
     * 检查 IP 是否被封禁
     */
    private boolean isIpBlocked(String ip) {
        if (ipBlockList == null || "unknown".equals(ip)) {
            return false;
        }

        Long blockUntil = ipBlockList.get(ip);
        if (blockUntil != null) {
            // 检查封禁是否已过期
            if (System.currentTimeMillis() < blockUntil) {
                return true;
            }
            // 过期解封
            ipBlockList.remove(ip);
        }
        return false;
    }
}