package com.jimuqu.claw.web;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticIndexPageTest {
    @Test
    void indexPageExists() {
        InputStream inputStream = StaticIndexPageTest.class.getResourceAsStream("/static/index.html");
        assertNotNull(inputStream, "index.html 静态资源不应该缺失");

        String html;
        try {
            Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            html = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
        } finally {
            try {
                inputStream.close();
            } catch (Exception ignored) {
            }
        }

        assertTrue(html.contains("/api/admin/channels/weixin/accounts"));
        assertTrue(html.contains("/api/admin/channels/weixin/login/start"));
        assertTrue(html.contains("/api/admin/channels/weixin/login/wait"));
        assertTrue(html.contains("/api/admin/channels/weixin/login/qr-image?content="));
        assertTrue(html.contains("已登录账号"));
        assertTrue(html.contains("boot()"));
    }
}
