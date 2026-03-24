package com.jimuqu.claw.web.controller;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

/**
 * 将根路径重定向到静态首页。
 */
@Controller
public class IndexPageController {
    @Get
    @Mapping("/")
    public void index(Context ctx) {
        ctx.redirect("/index.html");
    }
}
