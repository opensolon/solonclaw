package com.jimuqu.solon.claw.bootstrap;

import com.jimuqu.solon.claw.agent.AgentRuntimeService;
import com.jimuqu.solon.claw.agent.AgentProfileService;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.context.FileContextService;
import com.jimuqu.solon.claw.context.LocalSkillService;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.ContextBudgetService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.DelegationService;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.core.service.MemoryManager;
import com.jimuqu.solon.claw.core.service.MemoryService;
import com.jimuqu.solon.claw.core.service.SessionSearchService;
import com.jimuqu.solon.claw.core.service.SkillHubService;
import com.jimuqu.solon.claw.core.service.ToolRegistry;
import com.jimuqu.solon.claw.engine.AgentRunSupervisor;
import com.jimuqu.solon.claw.engine.DefaultConversationOrchestrator;
import com.jimuqu.solon.claw.engine.DefaultDelegationService;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.llm.SolonAiLlmGateway;
import com.jimuqu.solon.claw.storage.repository.SqlitePreferenceStore;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.ConversationOrchestratorHolder;
import com.jimuqu.solon.claw.support.DisplaySettingsService;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.RuntimeFooterService;
import com.jimuqu.solon.claw.support.RuntimePathGuard;
import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import com.jimuqu.solon.claw.support.update.AppUpdateService;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import com.jimuqu.solon.claw.tool.runtime.DangerousCommandApprovalService;
import com.jimuqu.solon.claw.tool.runtime.DefaultToolRegistry;
import com.jimuqu.solon.claw.tool.runtime.ProcessRegistry;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/** tool bean configuration. */
@Configuration
public class ToolConfiguration {
    @Bean
    public ProcessRegistry processRegistry() {
        return new ProcessRegistry();
    }

    @Bean
    public DangerousCommandApprovalService dangerousCommandApprovalService(
            GlobalSettingRepository globalSettingRepository) {
        return new DangerousCommandApprovalService(globalSettingRepository);
    }

    @Bean
    public AttachmentCacheService attachmentCacheService(AppConfig appConfig) {
        return new AttachmentCacheService(appConfig);
    }

    @Bean
    public RuntimePathGuard runtimePathGuard(AppConfig appConfig) {
        return new RuntimePathGuard(appConfig);
    }

    @Bean
    public ToolRegistry toolRegistry(
            AppConfig appConfig,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository,
            AgentProfileService agentProfileService,
            CronJobRepository cronJobRepository,
            DeliveryService deliveryService,
            MemoryService memoryService,
            SessionSearchService sessionSearchService,
            LocalSkillService localSkillService,
            SkillHubService skillHubService,
            CheckpointService checkpointService,
            DelegationService delegationService,
            AttachmentCacheService attachmentCacheService,
            RuntimeSettingsService runtimeSettingsService,
            GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        return new DefaultToolRegistry(
                appConfig,
                preferenceStore,
                sessionRepository,
                agentProfileService,
                cronJobRepository,
                deliveryService,
                memoryService,
                sessionSearchService,
                localSkillService,
                skillHubService,
                checkpointService,
                delegationService,
                attachmentCacheService,
                runtimeSettingsService,
                gatewayRuntimeRefreshService);
    }

    @Bean
    public DelegationService delegationService(
            AppConfig appConfig,
            ConversationOrchestratorHolder holder,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository,
            AgentRunRepository agentRunRepository,
            AgentRunControlService agentRunControlService) {
        return new DefaultDelegationService(
                holder,
                preferenceStore,
                sessionRepository,
                agentRunRepository,
                appConfig,
                agentRunControlService);
    }

    @Bean
    public DisplaySettingsService displaySettingsService(
            AppConfig appConfig, GlobalSettingRepository globalSettingRepository) {
        return new DisplaySettingsService(appConfig, globalSettingRepository);
    }

    @Bean
    public RuntimeFooterService runtimeFooterService(AppConfig appConfig) {
        return new RuntimeFooterService(appConfig);
    }

    @Bean
    public AppVersionService appVersionService(AppConfig appConfig) {
        return new AppVersionService(appConfig);
    }

    @Bean(destroyMethod = "shutdown")
    public AppUpdateService appUpdateService(
            AppConfig appConfig, AppVersionService appVersionService) {
        return new AppUpdateService(appConfig, appVersionService);
    }

    @Bean
    public LlmProviderService llmProviderService(AppConfig appConfig) {
        return new LlmProviderService(appConfig);
    }

    @Bean
    public ConversationOrchestratorHolder conversationOrchestratorHolder() {
        return new ConversationOrchestratorHolder();
    }

    @Bean
    public LlmGateway llmGateway(
            AppConfig appConfig,
            SessionRepository sessionRepository,
            DangerousCommandApprovalService dangerousCommandApprovalService,
            LlmProviderService llmProviderService) {
        return new SolonAiLlmGateway(
                appConfig, sessionRepository, dangerousCommandApprovalService, llmProviderService);
    }

    @Bean
    public AgentRunSupervisor agentRunSupervisor(
            AppConfig appConfig,
            SessionRepository sessionRepository,
            AgentRunRepository agentRunRepository,
            ContextCompressionService contextCompressionService,
            ContextBudgetService contextBudgetService,
            LlmGateway llmGateway,
            LlmProviderService llmProviderService) {
        return new AgentRunSupervisor(
                appConfig,
                sessionRepository,
                agentRunRepository,
                contextCompressionService,
                contextBudgetService,
                llmGateway,
                llmProviderService);
    }

    @Bean
    public ConversationOrchestrator conversationOrchestrator(
            SessionRepository sessionRepository,
            FileContextService contextService,
            ContextCompressionService contextCompressionService,
            LlmGateway llmGateway,
            ToolRegistry toolRegistry,
            DeliveryService deliveryService,
            DisplaySettingsService displaySettingsService,
            ConversationOrchestratorHolder holder,
            RuntimeSettingsService runtimeSettingsService,
            DangerousCommandApprovalService dangerousCommandApprovalService,
            AgentRunSupervisor agentRunSupervisor,
            RuntimeFooterService runtimeFooterService,
            AgentRuntimeService agentRuntimeService,
            MemoryManager memoryManager) {
        ConversationOrchestrator orchestrator =
                new DefaultConversationOrchestrator(
                        sessionRepository,
                        contextService,
                        contextCompressionService,
                        llmGateway,
                        toolRegistry,
                        deliveryService,
                        displaySettingsService,
                        runtimeSettingsService,
                        dangerousCommandApprovalService,
                        agentRunSupervisor,
                        runtimeFooterService,
                        agentRuntimeService,
                        memoryManager);
        holder.set(orchestrator);
        return orchestrator;
    }
}
