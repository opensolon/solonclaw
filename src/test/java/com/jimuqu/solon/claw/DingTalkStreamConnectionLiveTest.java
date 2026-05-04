package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.solon.claw.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.nio.file.Files;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class DingTalkStreamConnectionLiveTest {
    @Test
    void shouldConnectToDingTalkStreamMode() throws Exception {
        String clientId =
                TestEnvironment.runtimeConfigValue("solonclaw.channels.dingtalk.clientId");
        String clientSecret =
                TestEnvironment.runtimeConfigValue("solonclaw.channels.dingtalk.clientSecret");
        String robotCode =
                TestEnvironment.runtimeConfigValue("solonclaw.channels.dingtalk.robotCode");

        Assumptions.assumeTrue(StrUtil.isNotBlank(clientId));
        Assumptions.assumeTrue(StrUtil.isNotBlank(clientSecret));
        Assumptions.assumeTrue(StrUtil.isNotBlank(robotCode));

        AppConfig.ChannelConfig config = new AppConfig.ChannelConfig();
        config.setEnabled(true);
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRobotCode(robotCode);
        AppConfig appConfig = new AppConfig();
        java.io.File runtimeHome = Files.createTempDirectory("solon-claw-dingtalk-stream").toFile();
        appConfig.getChannels().setDingtalk(config);
        appConfig.getRuntime().setHome(runtimeHome.getAbsolutePath());
        appConfig
                .getRuntime()
                .setContextDir(new java.io.File(runtimeHome, "context").getAbsolutePath());
        appConfig
                .getRuntime()
                .setSkillsDir(new java.io.File(runtimeHome, "skills").getAbsolutePath());
        appConfig
                .getRuntime()
                .setCacheDir(new java.io.File(runtimeHome, "cache").getAbsolutePath());
        appConfig
                .getRuntime()
                .setStateDb(
                        new java.io.File(new java.io.File(runtimeHome, "data"), "state.db")
                                .getAbsolutePath());
        appConfig.normalizePaths();
        ChannelStateRepository channelStateRepository =
                new SqliteChannelStateRepository(new SqliteDatabase(appConfig));

        DingTalkChannelAdapter adapter =
                new DingTalkChannelAdapter(
                        config, channelStateRepository, new AttachmentCacheService(appConfig));
        boolean connected = adapter.connect();
        try {
            assertThat(connected).isTrue();
            assertThat(adapter.isConnected()).isTrue();
            assertThat(adapter.detail()).contains("connected");
        } finally {
            adapter.disconnect();
        }
    }
}
