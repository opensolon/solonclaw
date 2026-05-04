package com.jimuqu.solon.claw.web;

import java.util.Collections;
import org.noear.snack4.ONode;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.FilterChain;

/** Dashboard API token 校验与 localhost CORS 过滤器。 */
public class DashboardAuthFilter implements Filter {
    private final DashboardAuthService authService;

    public DashboardAuthFilter(DashboardAuthService authService) {
        this.authService = authService;
    }

    @Override
    public void doFilter(Context ctx, FilterChain chain) throws Throwable {
        authService.applyCors(ctx);

        if ("OPTIONS".equalsIgnoreCase(ctx.method()) && ctx.path().startsWith("/api/")) {
            ctx.status(204);
            return;
        }

        String path = ctx.path();
        boolean signedGatewayInjection =
                "/api/gateway/message".equals(path)
                        && "POST".equalsIgnoreCase(ctx.method())
                        && ctx.header("X-SolonClaw-Signature") != null;
        if (path.startsWith("/api/")
                && !signedGatewayInjection
                && !authService.isPublicApiPath(path)
                && !authService.isAuthorized(ctx)) {
            ctx.status(401);
            ctx.contentType("application/json;charset=UTF-8");
            ctx.output(ONode.serialize(Collections.singletonMap("detail", "Unauthorized")));
            return;
        }

        chain.doFilter(ctx);
    }
}
