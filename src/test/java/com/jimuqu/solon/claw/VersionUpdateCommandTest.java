package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.service.ContextService;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.gateway.command.DefaultCommandService;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.support.DisplaySettingsService;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.support.update.AppUpdateService;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import com.jimuqu.solon.claw.web.DashboardConfigService;
import com.jimuqu.solon.claw.web.DashboardProviderService;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigService;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

public class VersionUpdateCommandTest {
    @Test
    void shouldHandleVersionSubcommandsOnly() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        CapturingAppUpdateService updateService = new CapturingAppUpdateService(env.appConfig);
        DefaultCommandService commandService = commandService(env, updateService);
        GatewayMessage message = env.message("room", "user", "/version");

        GatewayReply versionReply = commandService.handle(message, "/version");
        GatewayReply checkReply = commandService.handle(message, "/version check");
        GatewayReply runReply = commandService.handle(message, "/version update");

        assertThat(versionReply.getContent()).contains("搴旂敤鐗堟湰");
        assertThat(updateService.formatCalls).isEqualTo(2);
        assertThat(updateService.startCalls).isEqualTo(1);
        assertThat(checkReply.getContent()).contains("搴旂敤鐗堟湰");
        assertThat(runReply.getContent()).contains("started update");
    }

    private DefaultCommandService commandService(
            TestEnvironment env, AppUpdateService updateService) {
        GatewayRuntimeRefreshService refreshService =
                new GatewayRuntimeRefreshService(
                        env.appConfig,
                        new com.jimuqu.solon.claw.gateway.service.ChannelConnectionManager(
                                new LinkedHashMap<
                                        com.jimuqu.solon.claw.core.enums.PlatformType,
                                        com.jimuqu.solon.claw.core.service.ChannelAdapter>()));
        LlmProviderService llmProviderService = new LlmProviderService(env.appConfig);
        RuntimeSettingsService runtimeSettingsService =
                new RuntimeSettingsService(
                        env.appConfig,
                        env.globalSettingRepository,
                        env.deliveryService,
                        new DashboardConfigService(env.appConfig, refreshService),
                        new DashboardRuntimeConfigService(env.appConfig, refreshService),
                        new AppVersionService(env.appConfig),
                        llmProviderService,
                        new DashboardProviderService(
                                env.appConfig, refreshService, llmProviderService));
        DisplaySettingsService displaySettingsService =
                new DisplaySettingsService(env.appConfig, env.globalSettingRepository);
        return new DefaultCommandService(
                env.sessionRepository,
                env.toolRegistry,
                env.localSkillService,
                env.cronJobRepository,
                new ConversationOrchestrator() {
                    @Override
                    public GatewayReply handleIncoming(GatewayMessage message) {
                        return GatewayReply.ok("noop");
                    }

                    @Override
                    public GatewayReply runScheduled(GatewayMessage syntheticMessage) {
                        return GatewayReply.ok("noop");
                    }

                    @Override
                    public GatewayReply resumePending(String sourceKey) {
                        return GatewayReply.ok("noop");
                    }
                },
                new ContextService() {
                    @Override
                    public String buildSystemPrompt(String sourceKey) {
                        return "";
                    }
                },
                env.contextCompressionService,
                env.deliveryService,
                env.gatewayAuthorizationService,
                env.checkpointService,
                env.skillHubService,
                env.appConfig,
                env.globalSettingRepository,
                env.processRegistry,
                runtimeSettingsService,
                displaySettingsService,
                updateService,
                env.dangerousCommandApprovalService,
                env.agentRunControlService,
                env.agentProfileService);
    }

    private static class CapturingAppUpdateService extends AppUpdateService {
        private int formatCalls;
        private int startCalls;

        private CapturingAppUpdateService(com.jimuqu.solon.claw.config.AppConfig appConfig) {
            super(appConfig, new AppVersionService(appConfig));
        }

        @Override
        public String formatVersionReport(boolean forceRefresh) {
            formatCalls++;
            return "搴旂敤鐗堟湰: v0.0.1";
        }

        @Override
        public UpdateResult startUpdate() {
            startCalls++;
            return UpdateResult.ok("started update");
        }
    }
}
