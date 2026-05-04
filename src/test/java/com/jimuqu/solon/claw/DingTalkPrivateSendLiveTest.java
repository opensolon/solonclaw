package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.solon.claw.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class DingTalkPrivateSendLiveTest {
    @Test
    void shouldTryPrivateChatSend() throws Exception {
        String clientId =
                TestEnvironment.runtimeConfigValue("solonclaw.channels.dingtalk.clientId");
        String clientSecret =
                TestEnvironment.runtimeConfigValue("solonclaw.channels.dingtalk.clientSecret");
        String robotCode =
                TestEnvironment.runtimeConfigValue("solonclaw.channels.dingtalk.robotCode");
        String openConversationId =
                TestEnvironment.runtimeConfigValue(
                        "solonclaw.tests.dingtalk.privateOpenConversationId");
        String userId =
                TestEnvironment.runtimeConfigValue("solonclaw.tests.dingtalk.privateUserId");

        Assumptions.assumeTrue(StrUtil.isNotBlank(clientId));
        Assumptions.assumeTrue(StrUtil.isNotBlank(clientSecret));
        Assumptions.assumeTrue(StrUtil.isNotBlank(robotCode));
        Assumptions.assumeTrue(StrUtil.isNotBlank(openConversationId));
        Assumptions.assumeTrue(StrUtil.isNotBlank(userId));

        AppConfig.ChannelConfig config = new AppConfig.ChannelConfig();
        config.setEnabled(true);
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRobotCode(robotCode);
        AppConfig appConfig =
                buildAppConfig(
                        config, Files.createTempDirectory("solon-claw-dingtalk-live").toFile());
        ChannelStateRepository channelStateRepository =
                new SqliteChannelStateRepository(new SqliteDatabase(appConfig));
        DingTalkChannelAdapter adapter =
                new DingTalkChannelAdapter(
                        config, channelStateRepository, new AttachmentCacheService(appConfig));
        adapter.connect();
        try {
            Field field = DingTalkChannelAdapter.class.getDeclaredField("conversationGroupFlags");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Boolean> flags = (Map<String, Boolean>) field.get(adapter);
            flags.put(openConversationId, Boolean.FALSE);
            DeliveryRequest request = new DeliveryRequest();
            request.setPlatform(PlatformType.DINGTALK);
            request.setChatId(openConversationId);
            request.setUserId(userId);
            request.setChatType("dm");
            request.setText("私聊发送测试");
            adapter.send(request);
            assertThat(adapter.isConnected()).isTrue();
        } finally {
            adapter.disconnect();
        }
    }

    private static AppConfig buildAppConfig(
            AppConfig.ChannelConfig channelConfig, java.io.File runtimeHome) {
        AppConfig appConfig = new AppConfig();
        appConfig.getChannels().setDingtalk(channelConfig);
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
        return appConfig;
    }
}
