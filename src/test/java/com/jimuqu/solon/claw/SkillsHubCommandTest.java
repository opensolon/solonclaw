package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.service.ContextService;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.SkillHubService;
import com.jimuqu.solon.claw.gateway.command.DefaultCommandService;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.ScanResult;
import com.jimuqu.solon.claw.skillhub.model.SkillBrowseResult;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.model.TapRecord;
import com.jimuqu.solon.claw.support.DisplaySettingsService;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.support.update.AppUpdateService;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import com.jimuqu.solon.claw.web.DashboardConfigService;
import com.jimuqu.solon.claw.web.DashboardProviderService;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SkillsHubCommandTest {
    @Test
    void shouldRouteSkillsSearchAndInstallCommandsToHubService() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        CapturingSkillHubService hub = new CapturingSkillHubService();
        DefaultCommandService commandService = commandService(env, hub);
        GatewayMessage message = env.message("room", "user", "/skills search kubernetes");

        GatewayReply searchReply =
                commandService.handle(
                        message, "/skills search kubernetes --source github --limit 5");
        GatewayReply installReply =
                commandService.handle(
                        message,
                        "/skills install github/openai/skills/demo --category ops --force");

        assertThat(hub.lastSearchQuery).isEqualTo("kubernetes");
        assertThat(hub.lastSearchSource).isEqualTo("github");
        assertThat(hub.lastSearchLimit).isEqualTo(5);
        assertThat(searchReply.getContent())
                .contains("demo-skill")
                .contains("github/openai/skills/demo");

        assertThat(hub.lastInstallIdentifier).isEqualTo("github/openai/skills/demo");
        assertThat(hub.lastInstallCategory).isEqualTo("ops");
        assertThat(hub.lastInstallForce).isTrue();
        assertThat(installReply.getContent()).contains("ops/demo-skill");
    }

    @Test
    void shouldRouteSkillsTapCommandToHubService() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        CapturingSkillHubService hub = new CapturingSkillHubService();
        DefaultCommandService commandService = commandService(env, hub);
        GatewayMessage message = env.message("room", "user", "/skills tap");

        GatewayReply addReply =
                commandService.handle(message, "/skills tap add myorg/skills-repo skills/");
        GatewayReply listReply = commandService.handle(message, "/skills tap list");

        assertThat(hub.lastTapRepo).isEqualTo("myorg/skills-repo");
        assertThat(hub.lastTapPath).isEqualTo("skills/");
        assertThat(addReply.getContent()).contains("Added tap");
        assertThat(listReply.getContent()).contains("openai/skills");
    }

    private DefaultCommandService commandService(
            TestEnvironment env, SkillHubService skillHubService) {
        GatewayRuntimeRefreshService refreshService =
                new GatewayRuntimeRefreshService(
                        env.appConfig,
                        new com.jimuqu.solon.claw.gateway.service.ChannelConnectionManager(
                                new java.util.LinkedHashMap<
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
        AppUpdateService appUpdateService =
                new AppUpdateService(env.appConfig, new AppVersionService(env.appConfig));
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
                skillHubService,
                env.appConfig,
                env.globalSettingRepository,
                env.processRegistry,
                runtimeSettingsService,
                displaySettingsService,
                appUpdateService,
                env.dangerousCommandApprovalService,
                env.agentRunControlService,
                env.agentProfileService);
    }

    private static class CapturingSkillHubService implements SkillHubService {
        private String lastSearchQuery;
        private String lastSearchSource;
        private int lastSearchLimit;
        private String lastInstallIdentifier;
        private String lastInstallCategory;
        private boolean lastInstallForce;
        private String lastTapRepo;
        private String lastTapPath;

        @Override
        public SkillBrowseResult browse(String sourceFilter, int page, int pageSize) {
            return new SkillBrowseResult();
        }

        @Override
        public SkillBrowseResult search(String query, String sourceFilter, int limit) {
            this.lastSearchQuery = query;
            this.lastSearchSource = sourceFilter;
            this.lastSearchLimit = limit;
            SkillMeta meta = new SkillMeta();
            meta.setName("demo-skill");
            meta.setSource("github");
            meta.setTrustLevel("trusted");
            meta.setIdentifier("github/openai/skills/demo");
            SkillBrowseResult result = new SkillBrowseResult();
            result.setItems(Collections.singletonList(meta));
            result.setTotal(1);
            result.setPage(1);
            result.setPageSize(limit);
            return result;
        }

        @Override
        public SkillMeta inspect(String identifier) {
            return null;
        }

        @Override
        public HubInstallRecord install(String identifier, String category, boolean force) {
            this.lastInstallIdentifier = identifier;
            this.lastInstallCategory = category;
            this.lastInstallForce = force;
            HubInstallRecord record = new HubInstallRecord();
            record.setName("demo-skill");
            record.setInstallPath("ops/demo-skill");
            record.setSource("github");
            record.setTrustLevel("trusted");
            return record;
        }

        @Override
        public List<HubInstallRecord> listInstalled() {
            return Collections.emptyList();
        }

        @Override
        public List<HubInstallRecord> check(String name) {
            return Collections.emptyList();
        }

        @Override
        public List<HubInstallRecord> update(String name, boolean force) {
            return Collections.emptyList();
        }

        @Override
        public List<ScanResult> audit(String name) {
            return Collections.emptyList();
        }

        @Override
        public String uninstall(String name) {
            return "removed";
        }

        @Override
        public List<TapRecord> listTaps() {
            TapRecord tap = new TapRecord();
            tap.setRepo("openai/skills");
            tap.setPath("skills/");
            return Collections.singletonList(tap);
        }

        @Override
        public String addTap(String repo, String path) {
            this.lastTapRepo = repo;
            this.lastTapPath = path;
            return "Added tap: " + repo;
        }

        @Override
        public String removeTap(String repo) {
            return "Removed tap: " + repo;
        }
    }
}
