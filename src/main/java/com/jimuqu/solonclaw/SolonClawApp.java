package com.jimuqu.solonclaw;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.core.event.AppLoadEndEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SolonClaw 主入口
 * <p>
 * 基于 Solon 框架的轻量级 AI 助手服务
 *
 * @author SolonClaw
 */
@SolonMain
public class SolonClawApp {

    private static final Logger log = LoggerFactory.getLogger(SolonClawApp.class);

    public static void main(String[] args) {
        Solon.start(SolonClawApp.class, args, app -> {
            app.onEvent(AppLoadEndEvent.class, e -> {
                String port = Solon.cfg().get("server.port", "12345");
                String contextPath = Solon.cfg().get("server.contextPath", "");

                log.info("\n" + "=".repeat(60));
                log.info("  SolonClaw AI Agent 服务启动成功！");
                log.info("=".repeat(60));
                log.info("  主页: http://localhost:{}{}", port, contextPath);
                log.info("  前端: http://localhost:{}{}", port, contextPath + (contextPath.isEmpty() ? "/" : "") + "index.html");
                log.info("  自主智能体: http://localhost:{}{}", port, contextPath + (contextPath.isEmpty() ? "/" : "") + "autonomous.html");
                log.info("  健康检查: http://localhost:{}{}", port, contextPath + (contextPath.isEmpty() ? "/" : "") + "health");
                log.info("=".repeat(60));
            });
        });
    }
}
