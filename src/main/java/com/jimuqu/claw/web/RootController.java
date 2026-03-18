package com.jimuqu.claw.web;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

/**
 * 处理根路径访问并转发到静态调试页。
 */
@Controller
public class RootController {
    /**
     * 将根路径请求转发到首页文件。
     *
     * @param ctx 当前请求上下文
     * @throws Throwable 转发过程中的异常
     */
    @Mapping("/")
    public void index(Context ctx) throws Throwable {
        ctx.forward("/index.html");
    }
}
