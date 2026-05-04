package com.jimuqu.solon.claw.bootstrap;

import com.jimuqu.solon.claw.agent.AgentProfileService;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.context.FileContextService;
import com.jimuqu.solon.claw.context.LocalSkillService;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.GatewayPolicyRepository;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.ChannelAdapter;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.core.service.CommandService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.core.service.InboundMessageHandler;
import com.jimuqu.solon.claw.core.service.MemoryManager;
import com.jimuqu.solon.claw.core.service.SkillHubService;
import com.jimuqu.solon.claw.core.service.SkillLearningService;
import com.jimuqu.solon.claw.core.service.ToolRegistry;
import com.jimuqu.solon.claw.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.solon.claw.gateway.command.DefaultCommandService;
import com.jimuqu.solon.claw.gateway.delivery.AdapterBackedDeliveryService;
import com.jimuqu.solon.claw.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.solon.claw.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.solon.claw.gateway.platform.qqbot.QQBotChannelAdapter;
import com.jimuqu.solon.claw.gateway.platform.wecom.WeComChannelAdapter;
import com.jimuqu.solon.claw.gateway.platform.weixin.WeiXinChannelAdapter;
import com.jimuqu.solon.claw.gateway.platform.yuanbao.YuanbaoChannelAdapter;
import com.jimuqu.solon.claw.gateway.service.ChannelConnectionManager;
import com.jimuqu.solon.claw.gateway.service.DefaultGatewayService;
import com.jimuqu.solon.claw.gateway.service.GatewayInjectionAuthService;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.DisplaySettingsService;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import com.jimuqu.solon.claw.support.update.AppUpdateService;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import com.jimuqu.solon.claw.tool.runtime.DangerousCommandApprovalService;
import com.jimuqu.solon.claw.tool.runtime.ProcessRegistry;
import com.jimuqu.solon.claw.web.DashboardConfigService;
import com.jimuqu.solon.claw.web.DashboardProviderService;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/** gateway bean configuration. */
@Configuration
public class GatewayConfiguration {
    @Bean
    public Map<PlatformType, ChannelAdapter> channelAdapters(
            AppConfig appConfig,
            ChannelStateRepository channelStateRepository,
            AttachmentCacheService attachmentCacheService) {
        Map<PlatformType, ChannelAdapter> adapters =
                new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(
                PlatformType.FEISHU,
                new FeishuChannelAdapter(
                        appConfig.getChannels().getFeishu(), attachmentCacheService));
        adapters.put(
                PlatformType.DINGTALK,
                new DingTalkChannelAdapter(
                        appConfig.getChannels().getDingtalk(),
                        channelStateRepository,
                        attachmentCacheService));
        adapters.put(
                PlatformType.WECOM,
                new WeComChannelAdapter(
                        appConfig.getChannels().getWecom(), attachmentCacheService));
        adapters.put(
                PlatformType.WEIXIN,
                new WeiXinChannelAdapter(
                        appConfig.getChannels().getWeixin(),
                        channelStateRepository,
                        attachmentCacheService));
        adapters.put(
                PlatformType.QQBOT,
                new QQBotChannelAdapter(
                        appConfig.getChannels().getQqbot(), attachmentCacheService));
        adapters.put(
                PlatformType.YUANBAO,
                new YuanbaoChannelAdapter(appConfig.getChannels().getYuanbao()));
        return adapters;
    }

    @Bean
    public DeliveryService deliveryService(
            Map<PlatformType, ChannelAdapter> channelAdapters,
            GatewayPolicyRepository gatewayPolicyRepository) {
        return new AdapterBackedDeliveryService(channelAdapters, gatewayPolicyRepository);
    }

    @Bean
    public RuntimeSettingsService runtimeSettingsService(
            AppConfig appConfig,
            GlobalSettingRepository globalSettingRepository,
            DeliveryService deliveryService,
            DashboardConfigService dashboardConfigService,
            DashboardRuntimeConfigService dashboardRuntimeConfigService,
            AppVersionService appVersionService,
            LlmProviderService llmProviderService,
            DashboardProviderService dashboardProviderService) {
        return new RuntimeSettingsService(
                appConfig,
                globalSettingRepository,
                deliveryService,
                dashboardConfigService,
                dashboardRuntimeConfigService,
                appVersionService,
                llmProviderService,
                dashboardProviderService);
    }

    @Bean(destroyMethod = "shutdown")
    public ChannelConnectionManager channelConnectionManager(
            Map<PlatformType, ChannelAdapter> channelAdapters) {
        return new ChannelConnectionManager(channelAdapters);
    }

    @Bean
    public GatewayRuntimeRefreshService gatewayRuntimeRefreshService(
            AppConfig appConfig, ChannelConnectionManager channelConnectionManager) {
        return new GatewayRuntimeRefreshService(appConfig, channelConnectionManager);
    }

    @Bean
    public GatewayAuthorizationService gatewayAuthorizationService(
            GatewayPolicyRepository gatewayPolicyRepository, AppConfig appConfig) {
        return new GatewayAuthorizationService(gatewayPolicyRepository, appConfig);
    }

    @Bean
    public CommandService commandService(
            SessionRepository sessionRepository,
            ToolRegistry toolRegistry,
            LocalSkillService localSkillService,
            CronJobRepository cronJobRepository,
            ConversationOrchestrator conversationOrchestrator,
            FileContextService contextService,
            ContextCompressionService contextCompressionService,
            DeliveryService deliveryService,
            GatewayAuthorizationService gatewayAuthorizationService,
            CheckpointService checkpointService,
            SkillHubService skillHubService,
            AppConfig appConfig,
            GlobalSettingRepository globalSettingRepository,
            ProcessRegistry processRegistry,
            RuntimeSettingsService runtimeSettingsService,
            DisplaySettingsService displaySettingsService,
            AppUpdateService appUpdateService,
            DangerousCommandApprovalService dangerousCommandApprovalService,
            AgentRunControlService agentRunControlService,
            AgentProfileService agentProfileService,
            AgentRunRepository agentRunRepository) {
        return new DefaultCommandService(
                sessionRepository,
                toolRegistry,
                localSkillService,
                cronJobRepository,
                conversationOrchestrator,
                contextService,
                contextCompressionService,
                deliveryService,
                gatewayAuthorizationService,
                checkpointService,
                skillHubService,
                appConfig,
                globalSettingRepository,
                processRegistry,
                runtimeSettingsService,
                displaySettingsService,
                appUpdateService,
                dangerousCommandApprovalService,
                agentRunControlService,
                agentProfileService,
                agentRunRepository);
    }

    @Bean
    public DefaultGatewayService gatewayService(
            CommandService commandService,
            ConversationOrchestrator conversationOrchestrator,
            DeliveryService deliveryService,
            SessionRepository sessionRepository,
            GatewayAuthorizationService gatewayAuthorizationService,
            SkillLearningService skillLearningService,
            ChannelConnectionManager channelConnectionManager) {
        final DefaultGatewayService service =
                new DefaultGatewayService(
                        commandService,
                        conversationOrchestrator,
                        deliveryService,
                        sessionRepository,
                        gatewayAuthorizationService,
                        skillLearningService);

        channelConnectionManager.bindInboundHandler(
                new InboundMessageHandler() {
                    @Override
                    public void handle(GatewayMessage message) throws Exception {
                        service.handle(message);
                    }
                });
        channelConnectionManager.startAll();
        return service;
    }

    @Bean
    public GatewayInjectionAuthService gatewayInjectionAuthService(AppConfig appConfig) {
        return new GatewayInjectionAuthService(appConfig);
    }
}
