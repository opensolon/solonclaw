package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.web.DashboardConfigService;
import com.jimuqu.solon.claw.web.WeixinQrSetupService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class WeixinQrSetupServiceTest {
    private HttpServer server;

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldCompleteQrLoginAndPersistCredentials() throws Exception {
        AtomicInteger statusPollCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/ilink/bot/get_bot_qrcode",
                exchange ->
                        writeJson(
                                exchange,
                                "{\"qrcode\":\"qr-123\",\"qrcode_img_content\":\"http://127.0.0.1/qr.png\"}"));
        server.createContext(
                "/ilink/bot/get_qrcode_status",
                exchange -> {
                    if (statusPollCount.incrementAndGet() < 2) {
                        writeJson(exchange, "{\"status\":\"wait\"}");
                    } else {
                        writeJson(
                                exchange,
                                "{\"status\":\"confirmed\",\"ilink_bot_id\":\"wx-bot\",\"bot_token\":\"wx-token\",\"baseurl\":\"http://127.0.0.1:"
                                        + server.getAddress().getPort()
                                        + "\",\"ilink_user_id\":\"wx-user\"}");
                    }
                });
        server.start();

        File runtimeHome = Files.createTempDirectory("solon-claw-weixin-qr").toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.getRuntime().setContextDir(new File(runtimeHome, "context").getAbsolutePath());
        config.getRuntime().setSkillsDir(new File(runtimeHome, "skills").getAbsolutePath());
        config.getRuntime().setCacheDir(new File(runtimeHome, "cache").getAbsolutePath());
        config.getRuntime()
                .setStateDb(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        config.getRuntime().setConfigFile(new File(runtimeHome, "config.yml").getAbsolutePath());
        config.getRuntime().setLogsDir(new File(runtimeHome, "logs").getAbsolutePath());
        config.getChannels()
                .getWeixin()
                .setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

        GatewayRuntimeRefreshService refreshService =
                new GatewayRuntimeRefreshService(
                        config,
                        new com.jimuqu.solon.claw.gateway.service.ChannelConnectionManager(
                                new LinkedHashMap<
                                        com.jimuqu.solon.claw.core.enums.PlatformType,
                                        com.jimuqu.solon.claw.core.service.ChannelAdapter>()));
        WeixinQrSetupService service =
                new WeixinQrSetupService(
                        config, new DashboardConfigService(config, refreshService), refreshService);

        Map<String, Object> start = service.start();
        assertThat(start.get("ticket")).isNotNull();

        String ticket = String.valueOf(start.get("ticket"));
        Map<String, Object> current = Collections.emptyMap();
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline) {
            current = service.get(ticket);
            if ("confirmed".equals(current.get("status"))) {
                break;
            }
            Thread.sleep(200L);
        }

        assertThat(current.get("status")).isEqualTo("confirmed");
        assertThat(current.get("account_id")).isEqualTo("wx-bot");
        assertThat(FileUtil.readUtf8String(new File(runtimeHome, "config.yml")))
                .contains("accountId: wx-bot")
                .contains("token: wx-token")
                .contains("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void writeJson(HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        try {
            outputStream.write(bytes);
        } finally {
            outputStream.close();
        }
    }
}
