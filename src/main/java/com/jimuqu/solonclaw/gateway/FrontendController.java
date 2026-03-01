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
    @Mapping("/frontend/index.html")
    @Produces("text/html;charset=utf-8")
    public String index() {
        // 从 classpath 读取前端文件
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("frontend/index.html")) {
            if (is == null) {
                return "<html><body>前端页面未找到</body></html>";
            }

            // 读取全部内容
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<html><body>加载前端页面出错: " + e.getMessage() + "</body></html>";
        }
    }

    /**
     * 返回前端 JavaScript 文件
     */
    @Mapping("/frontend/app.js")
    @Produces("application/javascript;charset=utf-8")
    public String appJs() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("frontend/app.js")) {
            if (is == null) {
                return "// app.js 未找到";
            }

            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "// 加载出错: " + e.getMessage();
        }
    }

    /**
     * 前端根路径重定向
     */
    @Mapping("/frontend")
    public void frontendRoot(Context ctx) {
        ctx.redirect("/frontend/index.html");
    }
}
