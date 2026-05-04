package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.ChannelStatus;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.service.ChannelAdapter;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import com.jimuqu.solon.claw.web.DashboardConfigService;
import com.jimuqu.solon.claw.web.DashboardProviderService;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RuntimeRefreshBehaviorTest {
    @Test
    void shouldUpdateLlmConfigWithoutReconnectingChannels() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter(PlatformType.WEIXIN);
        RuntimeSettingsService runtimeSettingsService = runtimeSettingsService(env, adapter);

        runtimeSettingsService.setGlobalModel("default", "gpt-5.2");

        assertThat(env.appConfig.getLlm().getModel()).isEqualTo("gpt-5.2");
        assertThat(adapter.disconnectCount).isZero();
        assertThat(adapter.connectCount).isZero();
    }

    @Test
    void shouldUpdateConfigBackedLlmModelEffectively() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter(PlatformType.WEIXIN);
        FileUtil.writeUtf8String(
                "model:\n  providerKey: default\n  default: gpt-5.4\n",
                env.appConfig.getRuntime().getConfigFile());
        RuntimeSettingsService runtimeSettingsService = runtimeSettingsService(env, adapter);

        runtimeSettingsService.setGlobalModel("default", "gpt-5.2");

        assertThat(env.appConfig.getLlm().getModel()).isEqualTo("gpt-5.2");
        assertThat(env.appConfig.getModel().getDefault()).isEqualTo("gpt-5.2");
        assertThat(FileUtil.readUtf8String(env.appConfig.getRuntime().getConfigFile()))
                .contains("default: gpt-5.2");
        assertThat(adapter.disconnectCount).isZero();
        assertThat(adapter.connectCount).isZero();
    }

    @Test
    void shouldReconnectChannelsWhenUpdatingChannelConfig() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter(PlatformType.WEIXIN);
        RuntimeSettingsService runtimeSettingsService = runtimeSettingsService(env, adapter);

        runtimeSettingsService.setConfigValue("channels.weixin.enabled", "true");

        assertThat(env.appConfig.getChannels().getWeixin().isEnabled()).isTrue();
        assertThat(adapter.disconnectCount).isEqualTo(1);
        assertThat(adapter.connectCount).isEqualTo(1);
    }

    @Test
    void shouldRefreshDirectConfigFileChangesAfterValidation() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FileUtil.writeUtf8String(
                "solonclaw:\n  react:\n    maxSteps: 50\n",
                env.appConfig.getRuntime().getConfigFile());

        GatewayRuntimeRefreshService.RefreshResult result =
                env.gatewayRuntimeRefreshService.refreshConfigOnly();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRefreshed()).isTrue();
        assertThat(env.appConfig.getReact().getMaxSteps()).isEqualTo(50);
    }

    @Test
    void shouldRejectInvalidConfigBeforeRefreshing() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        int previousMaxSteps = env.appConfig.getReact().getMaxSteps();
        FileUtil.writeUtf8String(
                "solonclaw:\n  react:\n    maxSteps: wrong\n",
                env.appConfig.getRuntime().getConfigFile());

        GatewayRuntimeRefreshService.RefreshResult result =
                env.gatewayRuntimeRefreshService.refreshConfigOnly();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("solonclaw.react.maxSteps");
        assertThat(env.appConfig.getReact().getMaxSteps()).isEqualTo(previousMaxSteps);
    }

    @Test
    void shouldRejectDecimalIntegerConfigBeforeRefreshing() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        int previousMaxSteps = env.appConfig.getReact().getMaxSteps();
        FileUtil.writeUtf8String(
                "solonclaw:\n  react:\n    maxSteps: 50.0\n",
                env.appConfig.getRuntime().getConfigFile());

        GatewayRuntimeRefreshService.RefreshResult result =
                env.gatewayRuntimeRefreshService.refreshConfigOnly();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("solonclaw.react.maxSteps");
        assertThat(env.appConfig.getReact().getMaxSteps()).isEqualTo(previousMaxSteps);
    }

    @Test
    void shouldRejectInvalidProviderShapeBeforeRefreshing() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        String previousModel = env.appConfig.getLlm().getModel();
        FileUtil.writeUtf8String(
                "providers:\n  default: wrong\nmodel:\n  providerKey: default\n",
                env.appConfig.getRuntime().getConfigFile());

        GatewayRuntimeRefreshService.RefreshResult result =
                env.gatewayRuntimeRefreshService.refreshConfigOnly();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("providers.default");
        assertThat(env.appConfig.getLlm().getModel()).isEqualTo(previousModel);
    }

    private RuntimeSettingsService runtimeSettingsService(
            TestEnvironment env, RecordingChannelAdapter adapter) {
        Map<PlatformType, ChannelAdapter> adapters =
                new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(adapter.platform(), adapter);
        GatewayRuntimeRefreshService refreshService =
                new GatewayRuntimeRefreshService(
                        env.appConfig,
                        new com.jimuqu.solon.claw.gateway.service.ChannelConnectionManager(
                                adapters));
        DashboardConfigService configService =
                new DashboardConfigService(env.appConfig, refreshService);
        DashboardRuntimeConfigService runtimeConfigService =
                new DashboardRuntimeConfigService(env.appConfig, refreshService);
        LlmProviderService llmProviderService = new LlmProviderService(env.appConfig);
        DashboardProviderService providerService =
                new DashboardProviderService(env.appConfig, refreshService, llmProviderService);
        return new RuntimeSettingsService(
                env.appConfig,
                env.globalSettingRepository,
                env.deliveryService,
                configService,
                runtimeConfigService,
                new AppVersionService(env.appConfig),
                llmProviderService,
                providerService);
    }

    private static class RecordingChannelAdapter implements ChannelAdapter {
        private final PlatformType platformType;
        private int connectCount;
        private int disconnectCount;

        private RecordingChannelAdapter(PlatformType platformType) {
            this.platformType = platformType;
        }

        @Override
        public PlatformType platform() {
            return platformType;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean connect() {
            connectCount++;
            return true;
        }

        @Override
        public void disconnect() {
            disconnectCount++;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String detail() {
            return "recording";
        }

        @Override
        public void send(DeliveryRequest request) {}

        @Override
        public ChannelStatus statusSnapshot() {
            ChannelStatus status = new ChannelStatus(platformType, true, true, "recording");
            status.setMissingConfig(Collections.<String>emptyList());
            return status;
        }
    }
}
