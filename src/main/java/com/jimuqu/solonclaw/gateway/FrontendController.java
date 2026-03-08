package com.jimuqu.solonclaw.gateway;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Produces;
import org.noear.solon.core.handle.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 前端页面控制器
 * <p>
 * 负责返回前端页面和静态资源
 *
 * @author SolonClaw
 */
@Controller
public class FrontendController {

    /**
     * 返回前端首页
     */
    @Mapping("/index.html")
    @Produces("text/html;charset=utf-8")
    public String index() {
        return readFile("frontend/index.html", "<html><body>前端页面未找到</body></html>");
    }

    /**
     * 返回前端首页
     */
    @Mapping("/frontend/index.html")
    @Produces("text/html;charset=utf-8")
    public String frontendIndex() {
        return readFile("frontend/index.html", "<html><body>前端页面未找到</body></html>");
    }

    /**
     * 返回 app.js 文件
     */
    @Mapping("/frontend/app.js")
    @Produces("application/javascript;charset=utf-8")
    public String appJs() {
        return readFile("frontend/app.js", "// app.js 未找到");
    }

    /**
     * 返回 RequestUtil.js 文件
     */
    @Mapping("/frontend/RequestUtil.js")
    @Produces("application/javascript;charset=utf-8")
    public String requestUtilJs() {
        return readFile("frontend/RequestUtil.js", "// RequestUtil.js 未找到");
    }

    /**
     * 返回 main.js 文件
     */
    @Mapping("/frontend/main.js")
    @Produces("application/javascript;charset=utf-8")
    public String mainJs() {
        return readFile("frontend/main.js", "// main.js 未找到");
    }

    /**
     * 返回 favicon.svg
     */
    @Mapping("/frontend/favicon.svg")
    @Produces("image/svg+xml;charset=utf-8")
    public String favicon() {
        return readFile("frontend/favicon.svg", "");
    }

    /**
     * 返回 pages 目录下的 HTML 文件
     */
    @Mapping("/frontend/pages/*")
    @Produces("text/html;charset=utf-8")
    public String pageFiles(Context ctx) {
        String path = ctx.pathNew();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return readFile("frontend/pages/" + fileName, "<html><body>页面未找到: " + fileName + "</body></html>");
    }

    /**
     * 读取文件内容的通用方法
     */
    private String readFile(String resourcePath, String notFoundMessage) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return notFoundMessage;
            }
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return notFoundMessage + " - 错误: " + e.getMessage();
        }
    }

    /**
     * 前端根路径重定向
     */
    @Mapping("/frontend")
    public void frontendRoot(Context ctx) {
        ctx.redirect("/frontend/index.html");
    }

    /**
     * 根路径重定向到前端
     */
    @Mapping("/")
    public void rootPath(Context ctx) {
        ctx.redirect("/index.html");
    }
}
