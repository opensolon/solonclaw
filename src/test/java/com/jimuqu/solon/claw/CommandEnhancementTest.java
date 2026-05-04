package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.FakeLlmGateway;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class CommandEnhancementTest {
    @Test
    void shouldSupportResetAndPersonalityCommands() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FakeLlmGateway fake = (FakeLlmGateway) env.llmGateway;
        bootstrapAdmin(env);

        GatewayReply listReply = env.send("admin-chat", "admin-user", "/personality");
        assertThat(listReply.getContent()).contains("helpful").contains("concise");

        GatewayReply setReply = env.send("admin-chat", "admin-user", "/personality helpful");
        assertThat(setReply.getContent()).contains("helpful");

        GatewayReply statusReply = env.send("admin-chat", "admin-user", "/status");
        assertThat(statusReply.getContent()).contains("personality=helpful");

        GatewayReply conversationReply = env.send("admin-chat", "admin-user", "人格测试");
        assertThat(conversationReply.getContent()).contains("echo:人格测试");
        assertThat(fake.lastSystemPrompt).contains("[Personality: helpful]");
        assertThat(fake.lastSystemPrompt).contains("You are a helpful assistant.");

        SessionRecord beforeReset =
                env.sessionRepository.getBoundSession("MEMORY:admin-chat:admin-user");
        GatewayReply resetReply = env.send("admin-chat", "admin-user", "/reset");
        SessionRecord afterReset =
                env.sessionRepository.getBoundSession("MEMORY:admin-chat:admin-user");

        assertThat(resetReply.getSessionId()).isNotEqualTo(beforeReset.getSessionId());
        assertThat(afterReset.getSessionId()).isEqualTo(resetReply.getSessionId());
    }

    @Test
    void shouldStopTrackedProcessesAndSupportRollbackListingAndIndexRestore() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        bootstrapAdmin(env);

        Process process = newSleepProcess();
        env.processRegistry.add(process);
        GatewayReply stopReply = env.send("admin-chat", "admin-user", "/stop");
        assertThat(stopReply.getContent()).contains("1");
        assertThat(env.processRegistry.runningCount()).isZero();

        String sourceKey = "MEMORY:admin-chat:admin-user";
        File file = FileUtil.file(env.appConfig.getRuntime().getCacheDir(), "rollback-command.txt");
        SessionRecord session = env.sessionRepository.bindNewSession(sourceKey);
        FileUtil.writeUtf8String("v1", file);
        env.checkpointService.createCheckpoint(
                sourceKey, session.getSessionId(), Collections.singletonList(file));
        FileUtil.writeUtf8String("v2", file);

        GatewayReply listReply = env.send("admin-chat", "admin-user", "/rollback");
        assertThat(listReply.getContent()).contains("1.").contains("created=");

        GatewayReply rollbackReply = env.send("admin-chat", "admin-user", "/rollback 1");
        assertThat(rollbackReply.getContent()).contains("checkpoint");
        assertThat(FileUtil.readUtf8String(file)).isEqualTo("v1");
    }

    private void bootstrapAdmin(TestEnvironment env) throws Exception {
        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
    }

    private Process newSleepProcess() throws Exception {
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
}
