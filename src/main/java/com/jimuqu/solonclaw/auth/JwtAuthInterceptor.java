package com.jimuqu.solonclaw.auth;

import org.noear.solon.annotation.*;
import org.noear.solon.core.aspect.Interceptor;
import org.noear.solon.core.aspect.Invocation;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * JWT 认证拦截器
 * <p>
 * 处理 JWT Token 验证和角色权限检查
 *
 * @author SolonClaw
 */
@Component
public class JwtAuthInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    public JwtAuthInterceptor() {
        this.userService = null;
        this.jwtTokenService = null;
        log.info("JWT 认证拦截器初始化完成");
    }

    @Override
    public Object doIntercept(Invocation inv) throws Throwable {
        Context ctx = Context.current();

        // 检查是否有 RequireRole 注解
        RequireRole requireRole = inv.method().getAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = inv.target().getClass().getAnnotation(RequireRole.class);
        }

        // 如果没有角色要求，直接放行
        if (requireRole == null) {
            return inv.invoke();
        }

        // 获取用户信息
        User user = getCurrentUser(ctx);
        if (user == null) {
            return unauthorizedResult("未登录或登录已过期");
        }

        // 检查角色权限
        UserRole requiredRole = requireRole.value();
        RequireRole.MatchMode mode = requireRole.mode();

        boolean hasPermission;
        if (mode == RequireRole.MatchMode.EXACT) {
            hasPermission = user.getRole() == requiredRole;
        } else {
            hasPermission = user.hasPermission(requiredRole);
        }

        if (!hasPermission) {
            log.warn("权限不足: userId={}, userRole={}, requiredRole={}, mode={}",
                    user.getId(), user.getRole(), requiredRole, mode);
            return forbiddenResult("权限不足");
        }

        // 将用户信息放入上下文 - Solon Context 不支持多参数 attr 方法
        // 使用 ThreadLocal 或其他方式存储用户信息
        // 这里暂时跳过，实际应该使用 ThreadLocal 等方式
        // ctx.attr("currentUser", user);
        return inv.invoke();
    }

    /**
     * 获取当前登录用户
     */
    public User getCurrentUser(Context ctx) {
        try {
            String authHeader = ctx.header(HEADER_AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                return null;
            }

            String token = authHeader.substring(BEARER_PREFIX.length());
            String userId = jwtTokenService.getUserIdFromToken(token);

            // 通过 ID 获取用户
            User user = userService.findById(userId);
            if (user == null) {
                log.debug("用户不存在: userId={}", userId);
                return null;
            }

            // 检查用户是否被禁用
            if (!user.isEnabled()) {
                log.warn("账户已被禁用: userId={}", user.getId());
                return null;
            }

            return user;
        } catch (AuthException e) {
            log.debug("Token 验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 返回未授权结果
     */
    private Result unauthorizedResult(String message) {
        Context ctx = Context.current();
        ctx.status(401);
        ctx.contentType("application/json;charset=UTF-8");
        ctx.output("{\"code\":401,\"message\":\"" + escapeJson(message) + "\",\"data\":null}");
        return null;
    }

    /**
     * 返回禁止访问结果
     */
    private Result forbiddenResult(String message) {
        Context ctx = Context.current();
        ctx.status(403);
        ctx.contentType("application/json;charset=UTF-8");
        ctx.output("{\"code\":403,\"message\":\"" + escapeJson(message) + "\",\"data\":null}");
        return null;
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}