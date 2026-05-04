package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.jimuqu.solon.claw.support.RuntimePathGuard;
import java.io.File;
import java.io.InputStream;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.DownloadedFile;

/** Dashboard 静态资源兜底输出，绕过当前打包后静态文件处理器返回空内容的问题。 */
@Controller
public class DashboardStaticController {
    private final RuntimePathGuard pathGuard;

    public DashboardStaticController(RuntimePathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    @Mapping("/assets/**")
    public Object assets(Context context) {
        return renderResource(context, "static" + context.path());
    }

    @Mapping("/favicon.ico")
    public Object faviconIco(Context context) {
        return renderResource(context, "static/favicon.ico");
    }

    @Mapping("/favicon.svg")
    public Object faviconSvg(Context context) {
        return renderResource(context, "static/favicon.svg");
    }

    @Mapping("/icons.svg")
    public Object iconsSvg(Context context) {
        return renderResource(context, "static/icons.svg");
    }

    @Mapping("/logo.png")
    public Object logo(Context context) {
        return renderResource(context, "static/logo.png");
    }

    private Object renderResource(Context context, String resourcePath) {
        File devFile = loadDevFile(resourcePath);
        if (devFile != null) {
            return new DownloadedFile(
                            contentType(resourcePath),
                            FileUtil.readBytes(devFile),
                            devFile.getName())
                    .asAttachment(false);
        }

        byte[] bytes = loadClasspathBytes(resourcePath);
        if (bytes == null) {
            context.status(404);
            return new DownloadedFile("text/plain;charset=UTF-8", new byte[0], "404.txt")
                    .asAttachment(false);
        }

        return new DownloadedFile(contentType(resourcePath), bytes, fileName(resourcePath))
                .asAttachment(false);
    }

    private File loadDevFile(String resourcePath) {
        String relative =
                resourcePath.startsWith("static/")
                        ? resourcePath.substring("static/".length())
                        : resourcePath;
        if (relative.contains("..") || relative.startsWith("/") || relative.startsWith("\\")) {
            return null;
        }
        File devFile =
                pathGuard.requireUnderWebDist(
                        new File(
                                System.getProperty("user.dir"),
                                "web/dist/" + relative.replace('/', File.separatorChar)));
        if (devFile.exists() && devFile.isFile()) {
            return devFile;
        }
        return null;
    }

    private byte[] loadClasspathBytes(String resourcePath) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            return null;
        }

        return IoUtil.readBytes(stream);
    }

    private String fileName(String resourcePath) {
        int idx = resourcePath.lastIndexOf('/');
        return idx >= 0 ? resourcePath.substring(idx + 1) : resourcePath;
    }

    private String contentType(String resourcePath) {
        String lower = resourcePath.toLowerCase();
        if (lower.endsWith(".js")) {
            return "application/javascript;charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".html")) {
            return "text/html;charset=UTF-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json;charset=UTF-8";
        }
        return "application/octet-stream";
    }
}
