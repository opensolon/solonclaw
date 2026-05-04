package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.constants.AgentSettingConstants;
import com.jimuqu.solon.claw.tool.runtime.ProcessRegistry;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.Solon;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.core.AppContext;

public class GatewayControllerHttpTest {
    private static final String GATEWAY_SECRET = "gateway-test-secret";
    private static int port;
    private static File runtimeHome;

    @BeforeAll
    static void startApp() throws Exception {
        port = findFreePort();
        runtimeHome = Files.createTempDirectory("solon-claw-http-test").toFile();

        Solon.start(
                SolonClawApp.class,
                new String[] {
                    "--server.port=" + port,
                    "--solonclaw.runtime.home=" + runtimeHome.getAbsolutePath(),
                    "--solonclaw.gateway.injectionSecret=" + GATEWAY_SECRET,
                    "--solonclaw.scheduler.enabled=false"
                });

        waitForHealth();
        bootstrapAdmin();
    }

    @AfterAll
    static void stopApp() {
        try {
            Solon.stopBlock(false, 0);
        } finally {
            if (runtimeHome != null) {
                try {
                    FileUtil.del(runtimeHome);
                } catch (IORuntimeException ignored) {
                    // Windows may keep logback's agent.log handle briefly after Solon stops.
                }
            }
        }
    }

    @Test
    void shouldHandlePersonalityStatusAndResetThroughHttpController() throws Exception {
        postMessage("http-admin-chat", "http-admin", "/personality none");

        GatewayReply listReply = postMessage("http-admin-chat", "http-admin", "/personality");
        assertThat(listReply.getContent()).contains("helpful").contains("concise");

        GatewayReply setReply =
                postMessage("http-admin-chat", "http-admin", "/personality concise");
        assertThat(setReply.getContent()).contains("concise");

        GlobalSettingRepository globalSettingRepository = bean(GlobalSettingRepository.class);
        assertThat(globalSettingRepository.get(AgentSettingConstants.ACTIVE_PERSONALITY))
                .isEqualTo("concise");

        GatewayReply statusReply = postMessage("http-admin-chat", "http-admin", "/status");
        assertThat(statusReply.getContent()).contains("personality=concise");

        GatewayReply firstNew = postMessage("http-admin-chat", "http-admin", "/new");
        GatewayReply resetReply = postMessage("http-admin-chat", "http-admin", "/reset");
        assertThat(resetReply.getSessionId()).isNotEqualTo(firstNew.getSessionId());
    }

    @Test
    void shouldHandleStopRollbackAndCompressThroughHttpController() throws Exception {
        ProcessRegistry processRegistry = bean(ProcessRegistry.class);
        Process process = newSleepProcess();
        processRegistry.add(process);

        GatewayReply stopReply = postMessage("http-admin-chat", "http-admin", "/stop");
        assertThat(stopReply.getContent()).contains("1");
        assertThat(processRegistry.runningCount()).isZero();

        SessionRepository sessionRepository = bean(SessionRepository.class);
        CheckpointService checkpointService = bean(CheckpointService.class);
        String sourceKey = "MEMORY:http-admin-chat:http-admin";
        sessionRepository.bindNewSession(sourceKey);

        File file = FileUtil.file(runtimeHome, "cache", "http-rollback.txt");
        SessionRecord boundSession = sessionRepository.getBoundSession(sourceKey);
        FileUtil.writeUtf8String("v1", file);
        checkpointService.createCheckpoint(
                sourceKey, boundSession.getSessionId(), Collections.singletonList(file));
        FileUtil.writeUtf8String("v2", file);

        GatewayReply listReply = postMessage("http-admin-chat", "http-admin", "/rollback");
        assertThat(listReply.getContent()).contains("1.").contains("created=");

        GatewayReply rollbackReply = postMessage("http-admin-chat", "http-admin", "/rollback 1");
        assertThat(rollbackReply.getContent()).contains("checkpoint");
        assertThat(FileUtil.readUtf8String(file)).isEqualTo("v1");

        SessionRecord session = sessionRepository.getBoundSession(sourceKey);
        session.setNdjson(
                MessageSupport.toNdjson(
                        Arrays.asList(
                                ChatMessage.ofUser("分析当前问题"),
                                ChatMessage.ofAssistant("已经处理第一部分"),
                                ChatMessage.ofTool("tool output " + repeat("Z", 500), "tool", "1"),
                                ChatMessage.ofUser("继续推进"),
                                ChatMessage.ofAssistant("准备发布"),
                                ChatMessage.ofUser(repeat("B", 5000)))));
        sessionRepository.save(session);

        GatewayReply compressReply = postMessage("http-admin-chat", "http-admin", "/compress 发布流程");
        SessionRecord updated = sessionRepository.findById(session.getSessionId());

        assertThat(compressReply.getContent()).contains("关注主题");
        assertThat(updated.getCompressedSummary()).contains("Focus");
        assertThat(updated.getCompressedSummary()).contains("发布流程");
    }

    private static void bootstrapAdmin() throws Exception {
        GatewayReply claimPrompt = postMessage("http-admin-chat", "http-admin", "hello");
        assertThat(claimPrompt.getContent()).contains("/pairing claim-admin");
        GatewayReply claimReply =
                postMessage("http-admin-chat", "http-admin", "/pairing claim-admin");
        assertThat(claimReply.getContent()).contains("唯一管理员");
    }

    private static <T> T bean(Class<T> type) {
        AppContext context = Solon.context();
        return context.getBean(type);
    }

    private static Process newSleepProcess() throws Exception {
        return new ProcessBuilder(
                        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                        "-cp",
                        System.getProperty("java.class.path"),
                        SleepProcess.class.getName())
                .start();
    }

    public static class SleepProcess {
        public static void main(String[] args) throws Exception {
            Thread.sleep(30000L);
        }
    }

    private static GatewayReply postMessage(String chatId, String userId, String text)
            throws Exception {
        GatewayMessage message =
                new GatewayMessage(
                        com.jimuqu.solon.claw.core.enums.PlatformType.MEMORY, chatId, userId, text);
        message.setChatType("dm");
        message.setChatName(chatId);
        message.setUserName(userId);

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL("http://127.0.0.1:" + port + "/api/gateway/message")
                                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        String bodyText = ONode.serialize(message);
        byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = chatId + "-" + System.nanoTime();
        connection.setRequestProperty("X-SolonClaw-Timestamp", timestamp);
        connection.setRequestProperty("X-SolonClaw-Nonce", nonce);
        connection.setRequestProperty(
                "X-SolonClaw-Signature",
                "sha256=" + hmac(timestamp + "." + nonce + "." + bodyText));
        connection.setFixedLengthStreamingMode(body.length);
        OutputStream outputStream = connection.getOutputStream();
        try {
            outputStream.write(body);
        } finally {
            outputStream.close();
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        try {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            return ONode.deserialize(buffer.toString(), GatewayReply.class);
        } finally {
            reader.close();
            connection.disconnect();
        }
    }

    private static String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(GATEWAY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            String hex = Integer.toHexString(value & 0xff);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private static void waitForHealth() throws Exception {
        long deadline = System.currentTimeMillis() + 15000L;
        Exception lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection connection =
                        (HttpURLConnection)
                                new URL("http://127.0.0.1:" + port + "/health").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                if (connection.getResponseCode() == 200) {
                    connection.disconnect();
                    return;
                }
                connection.disconnect();
            } catch (Exception e) {
                lastError = e;
            }
            Thread.sleep(100L);
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("health endpoint did not become ready");
    }

    private static int findFreePort() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        try {
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(value);
        }
        return buffer.toString();
    }
}
