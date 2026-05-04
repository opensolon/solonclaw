package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.noear.snack4.ONode;
import org.noear.solon.core.handle.Context;

/** Dashboard 访问控制与 token 注入服务。 */
public class DashboardAuthService {
    private static final List<String> PUBLIC_API_PATHS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "/api/status",
                            "/api/config/defaults",
                            "/api/config/schema",
                            "/api/model/info"));

    private final AppConfig appConfig;
    private final List<Long> revealTimestamps = new ArrayList<Long>();

    public DashboardAuthService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public boolean isPublicApiPath(String path) {
        return PUBLIC_API_PATHS.contains(path);
    }

    public String sessionToken() {
        return accessToken();
    }

    public boolean isAuthorized(Context context) {
        String auth = context.header("Authorization");
        String token = accessToken();
        return StrUtil.isNotBlank(token) && ("Bearer " + token).equals(auth);
    }

    public boolean canRevealToken(Context context) {
        return isAuthorized(context) || isLocalRequest(context);
    }

    public boolean isLocalRequest(Context context) {
        String ip = context.remoteIp();
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        try {
            return InetAddress.getByName(ip).isLoopbackAddress();
        } catch (Exception e) {
            return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
        }
    }

    public String injectToken(String html) {
        String token = accessToken();
        if (StrUtil.isBlank(token)) {
            return html;
        }
        String script =
                "<script>window.__APP_SESSION_TOKEN__=\"" + escapeJs(token) + "\";</script>";
        if (html.contains("</head>")) {
            return html.replace("</head>", script + "</head>");
        }
        return script + html;
    }

    public void writeUnauthorized(Context context) {
        context.status(401);
        context.contentType("application/json;charset=UTF-8");
        context.output(ONode.serialize(Collections.singletonMap("detail", "Unauthorized")));
    }

    public void applyCors(Context context) {
        String origin = context.header("Origin");
        if (StrUtil.isBlank(origin)) {
            return;
        }

        if (!isLocalOrigin(origin)) {
            return;
        }

        context.headerSet("Access-Control-Allow-Origin", origin);
        context.headerSet("Vary", "Origin");
        context.headerSet("Access-Control-Allow-Headers", "Authorization, Content-Type");
        context.headerSet("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
    }

    public boolean allowReveal() {
        synchronized (revealTimestamps) {
            long now = System.currentTimeMillis();
            long windowStart = now - 30_000L;
            for (int i = revealTimestamps.size() - 1; i >= 0; i--) {
                if (revealTimestamps.get(i) < windowStart) {
                    revealTimestamps.remove(i);
                }
            }
            if (revealTimestamps.size() >= 5) {
                return false;
            }
            revealTimestamps.add(now);
            return true;
        }
    }

    private boolean isLocalOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || StrUtil.isBlank(host)) {
                return false;
            }
            if ("localhost".equalsIgnoreCase(host)) {
                return true;
            }
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private String accessToken() {
        String configured =
                appConfig == null || appConfig.getDashboard() == null
                        ? ""
                        : StrUtil.nullToEmpty(appConfig.getDashboard().getAccessToken());
        return StrUtil.isBlank(configured) ? "admin" : configured;
    }

    private String escapeJs(String value) {
        return StrUtil.nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("<", "\\u003c");
    }
}
