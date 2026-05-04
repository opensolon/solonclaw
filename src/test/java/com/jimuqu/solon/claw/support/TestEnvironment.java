package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentProfileRepository;
import com.jimuqu.solon.claw.agent.AgentProfileService;
import com.jimuqu.solon.claw.agent.AgentRuntimeService;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import com.jimuqu.solon.claw.context.AsyncSkillLearningService;
import com.jimuqu.solon.claw.context.BuiltinMemoryProvider;
import com.jimuqu.solon.claw.context.DefaultMemoryManager;
import com.jimuqu.solon.claw.context.FileContextService;
import com.jimuqu.solon.claw.context.FileMemoryService;
import com.jimuqu.solon.claw.context.LocalSkillService;
import com.jimuqu.solon.claw.context.PersonaWorkspaceService;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.core.repository.GatewayPolicyRepository;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.ChannelAdapter;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.core.service.CommandService;
import com.jimuqu.solon.claw.core.service.ContextBudgetService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.DelegationService;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.core.service.MemoryManager;
import com.jimuqu.solon.claw.core.service.MemoryProvider;
import com.jimuqu.solon.claw.core.service.MemoryService;
import com.jimuqu.solon.claw.core.service.SessionSearchService;
import com.jimuqu.solon.claw.core.service.SkillGuardService;
import com.jimuqu.solon.claw.core.service.SkillHubService;
import com.jimuqu.solon.claw.core.service.SkillImportService;
import com.jimuqu.solon.claw.core.service.SkillLearningService;
import com.jimuqu.solon.claw.core.service.ToolRegistry;
import com.jimuqu.solon.claw.engine.AgentRunSupervisor;
import com.jimuqu.solon.claw.engine.DefaultContextBudgetService;
import com.jimuqu.solon.claw.engine.DefaultContextCompressionService;
import com.jimuqu.solon.claw.engine.DefaultConversationOrchestrator;
import com.jimuqu.solon.claw.engine.DefaultDelegationService;
import com.jimuqu.solon.claw.engine.DefaultSessionSearchService;
import com.jimuqu.solon.claw.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.solon.claw.gateway.command.DefaultCommandService;
import com.jimuqu.solon.claw.gateway.delivery.AdapterBackedDeliveryService;
import com.jimuqu.solon.claw.gateway.service.DefaultGatewayService;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.llm.LlmProviderSupport;
import com.jimuqu.solon.claw.llm.SolonAiLlmGateway;
import com.jimuqu.solon.claw.skillhub.service.DefaultSkillGuardService;
import com.jimuqu.solon.claw.skillhub.service.DefaultSkillHubService;
import com.jimuqu.solon.claw.skillhub.service.DefaultSkillImportService;
import com.jimuqu.solon.claw.skillhub.source.GitHubSkillSource;
import com.jimuqu.solon.claw.skillhub.support.DefaultSkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.GitHubAuth;
import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import com.jimuqu.solon.claw.storage.repository.SqliteAgentProfileRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteAgentRunRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteCronJobRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.storage.repository.SqliteGatewayPolicyRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteGlobalSettingRepository;
import com.jimuqu.solon.claw.storage.repository.SqlitePreferenceStore;
import com.jimuqu.solon.claw.storage.repository.SqliteSessionRepository;
import com.jimuqu.solon.claw.support.constants.RuntimePathConstants;
import com.jimuqu.solon.claw.support.update.AppUpdateService;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import com.jimuqu.solon.claw.tool.runtime.DangerousCommandApprovalService;
import com.jimuqu.solon.claw.tool.runtime.DefaultToolRegistry;
import com.jimuqu.solon.claw.tool.runtime.ProcessRegistry;
import com.jimuqu.solon.claw.web.DashboardConfigService;
import com.jimuqu.solon.claw.web.DashboardProviderService;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigService;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TestEnvironment {
    public final AppConfig appConfig;
    public final MemoryChannelAdapter memoryChannelAdapter;
    public final SessionRepository sessionRepository;
    public final AgentRunRepository agentRunRepository;
    public final CronJobRepository cronJobRepository;
    public final LocalSkillService localSkillService;
    public final DefaultGatewayService gatewayService;
    public final ConversationOrchestrator conversationOrchestrator;
    public final ToolRegistry toolRegistry;
    public final GatewayPolicyRepository gatewayPolicyRepository;
    public final GatewayAuthorizationService gatewayAuthorizationService;
    public final DeliveryService deliveryService;
    public final LlmGateway llmGateway;
    public final MemoryService memoryService;
    public final MemoryManager memoryManager;
    public final CheckpointService checkpointService;
    public final DelegationService delegationService;
    public final ContextCompressionService contextCompressionService;
    public final GlobalSettingRepository globalSettingRepository;
    public final SessionSearchService sessionSearchService;
    public final ProcessRegistry processRegistry;
    public final SkillHubService skillHubService;
    public final DangerousCommandApprovalService dangerousCommandApprovalService;
    public final AgentRunControlService agentRunControlService;
    public final AgentProfileService agentProfileService;
    public final AgentRuntimeService agentRuntimeService;
    public final GatewayRuntimeRefreshService gatewayRuntimeRefreshService;

    public static TestEnvironment withFakeLlm() throws Exception {
        return create(new FakeLlmGateway());
    }

    public static TestEnvironment withLlm(LlmGateway llmGateway) throws Exception {
        return create(llmGateway);
    }

    public static TestEnvironment withLiveLlm() throws Exception {
        AppConfig config = newConfig();
        String dialect = runtimeConfigValue("providers.default.dialect", "openai");
        String baseUrl =
                runtimeConfigValue("providers.default.baseUrl", "https://api.openai.com");
        AppConfig.ProviderConfig provider = config.getProviders().get("default");
        provider.setDialect(dialect);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(runtimeConfigValue("providers.default.apiKey", ""));
        provider.setDefaultModel(runtimeConfigValue("providers.default.defaultModel", "gpt-5.4"));
        config.getModel().setProviderKey("default");
        config.getModel().setDefault(provider.getDefaultModel());
        config.getLlm().setProvider("default");
        config.getLlm().setDialect(provider.getDialect());
        config.getLlm().setApiUrl(LlmProviderSupport.buildApiUrl(baseUrl, dialect));
        config.getLlm().setApiKey(provider.getApiKey());
        config.getLlm().setModel(provider.getDefaultModel());
        config.getLlm().setStream(true);
        return create(config, new SolonAiLlmGateway(config));
    }

    public static String runtimeConfigValue(String key) {
        return runtimeConfigValue(key, "");
    }

    public static String runtimeConfigValue(String key, String defaultValue) {
        String value = RuntimeConfigResolver.initialize(RuntimePathConstants.RUNTIME_HOME).get(key);
        return StrUtil.blankToDefault(value, defaultValue).trim();
    }

    private static TestEnvironment create(LlmGateway llmGateway) throws Exception {
        return create(newConfig(), llmGateway);
    }

    private static TestEnvironment create(AppConfig config, LlmGateway llmGateway)
            throws Exception {
        SqliteDatabase database = new SqliteDatabase(config);
        SqlitePreferenceStore preferenceStore = new SqlitePreferenceStore(database);
        GlobalSettingRepository globalSettingRepository =
                new SqliteGlobalSettingRepository(database);
        SessionRepository sessionRepository = new SqliteSessionRepository(database);
        AgentRunRepository agentRunRepository = new SqliteAgentRunRepository(database);
        CronJobRepository cronJobRepository = new SqliteCronJobRepository(database);
        GatewayPolicyRepository gatewayPolicyRepository =
                new SqliteGatewayPolicyRepository(database);
        ChannelStateRepository channelStateRepository = new SqliteChannelStateRepository(database);
        AgentProfileRepository agentProfileRepository = new SqliteAgentProfileRepository(database);
        AgentRuntimeService agentRuntimeService =
                new AgentRuntimeService(config, agentProfileRepository);
        AgentProfileService agentProfileService =
                new AgentProfileService(agentProfileRepository, agentRuntimeService);
        ConversationOrchestratorHolder holder = new ConversationOrchestratorHolder();
        SkillHubStateStore skillHubStateStore =
                new SkillHubStateStore(new File(config.getRuntime().getSkillsDir()));
        SkillGuardService skillGuardService = new DefaultSkillGuardService();
        SkillHubHttpClient skillHubHttpClient = new DefaultSkillHubHttpClient();
        GitHubAuth gitHubAuth = new GitHubAuth(skillHubHttpClient);
        SkillImportService skillImportService =
                new DefaultSkillImportService(
                        new File(config.getRuntime().getSkillsDir()),
                        skillGuardService,
                        skillHubStateStore);
        LocalSkillService localSkillService =
                new LocalSkillService(
                        config, preferenceStore, skillImportService, skillHubStateStore);
        MemoryService memoryService = new FileMemoryService(config);
        MemoryProvider builtinMemoryProvider = new BuiltinMemoryProvider(memoryService);
        MemoryManager memoryManager =
                new DefaultMemoryManager(
                        java.util.Collections.singletonList(builtinMemoryProvider));
        PersonaWorkspaceService personaWorkspaceService = new PersonaWorkspaceService(config);
        FileContextService contextService =
                new FileContextService(
                        config,
                        localSkillService,
                        memoryManager,
                        globalSettingRepository,
                        personaWorkspaceService);
        ContextCompressionService contextCompressionService =
                new DefaultContextCompressionService(config);
        MemoryChannelAdapter memoryAdapter = new MemoryChannelAdapter();
        Map<PlatformType, ChannelAdapter> adapters =
                new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(PlatformType.MEMORY, memoryAdapter);
        DeliveryService deliveryService =
                new AdapterBackedDeliveryService(adapters, gatewayPolicyRepository);
        GatewayAuthorizationService gatewayAuthorizationService =
                new GatewayAuthorizationService(gatewayPolicyRepository, config);
        CheckpointService checkpointService = new DefaultCheckpointService(config, database);
        ProcessRegistry processRegistry = new ProcessRegistry();
        DangerousCommandApprovalService dangerousCommandApprovalService =
                new DangerousCommandApprovalService(globalSettingRepository);
        AttachmentCacheService attachmentCacheService = new AttachmentCacheService(config);
        GatewayRuntimeRefreshService refreshService =
                new GatewayRuntimeRefreshService(
                        config,
                        new com.jimuqu.solon.claw.gateway.service.ChannelConnectionManager(
                                adapters));
        DashboardConfigService dashboardConfigService =
                new DashboardConfigService(config, refreshService);
        DashboardRuntimeConfigService dashboardRuntimeConfigService =
                new DashboardRuntimeConfigService(config, refreshService);
        AppVersionService appVersionService = new AppVersionService(config);
        LlmProviderService llmProviderService = new LlmProviderService(config);
        DashboardProviderService dashboardProviderService =
                new DashboardProviderService(config, refreshService, llmProviderService);
        RuntimeSettingsService runtimeSettingsService =
                new RuntimeSettingsService(
                        config,
                        globalSettingRepository,
                        deliveryService,
                        dashboardConfigService,
                        dashboardRuntimeConfigService,
                        appVersionService,
                        llmProviderService,
                        dashboardProviderService);
        DisplaySettingsService displaySettingsService =
                new DisplaySettingsService(config, globalSettingRepository);
        RuntimeFooterService runtimeFooterService = new RuntimeFooterService(config);
        AppUpdateService appUpdateService = new AppUpdateService(config, appVersionService);
        DelegationService delegationService =
                new DefaultDelegationService(holder, preferenceStore, sessionRepository);
        SessionSearchService sessionSearchService =
                new DefaultSessionSearchService(sessionRepository, llmGateway, agentRunRepository);
        GitHubSkillSource gitHubSkillSource =
                new GitHubSkillSource(gitHubAuth, skillHubHttpClient, skillHubStateStore);
        SkillHubService skillHubService =
                new DefaultSkillHubService(
                        new File(System.getProperty("user.dir")),
                        new File(config.getRuntime().getSkillsDir()),
                        skillImportService,
                        skillGuardService,
                        skillHubStateStore,
                        skillHubHttpClient,
                        gitHubAuth,
                        gitHubSkillSource);
        ToolRegistry toolRegistry =
                new DefaultToolRegistry(
                        config,
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
                        refreshService);
        ContextBudgetService contextBudgetService = new DefaultContextBudgetService(config);
        AgentRunSupervisor agentRunSupervisor =
                new AgentRunSupervisor(
                        config,
                        sessionRepository,
                        agentRunRepository,
                        contextCompressionService,
                        contextBudgetService,
                        llmGateway,
                        llmProviderService);
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
        SkillLearningService skillLearningService =
                new AsyncSkillLearningService(
                        config,
                        sessionRepository,
                        memoryService,
                        localSkillService,
                        checkpointService,
                        llmGateway);
        CommandService commandService =
                new DefaultCommandService(
                        sessionRepository,
                        toolRegistry,
                        localSkillService,
                        cronJobRepository,
                        orchestrator,
                        contextService,
                        contextCompressionService,
                        deliveryService,
                        gatewayAuthorizationService,
                        checkpointService,
                        skillHubService,
                        config,
                        globalSettingRepository,
                        processRegistry,
                        runtimeSettingsService,
                        displaySettingsService,
                        appUpdateService,
                        dangerousCommandApprovalService,
                        agentRunSupervisor,
                        agentProfileService);
        DefaultGatewayService gatewayService =
                new DefaultGatewayService(
                        commandService,
                        orchestrator,
                        deliveryService,
                        sessionRepository,
                        gatewayAuthorizationService,
                        skillLearningService);
        return new TestEnvironment(
                config,
                memoryAdapter,
                sessionRepository,
                agentRunRepository,
                cronJobRepository,
                localSkillService,
                gatewayService,
                orchestrator,
                toolRegistry,
                gatewayPolicyRepository,
                gatewayAuthorizationService,
                deliveryService,
                llmGateway,
                memoryService,
                memoryManager,
                checkpointService,
                delegationService,
                contextCompressionService,
                globalSettingRepository,
                sessionSearchService,
                processRegistry,
                skillHubService,
                dangerousCommandApprovalService,
                agentRunSupervisor,
                agentProfileService,
                agentRuntimeService,
                refreshService);
    }

    public GatewayMessage message(String chatId, String userId, String text) {
        return message(chatId, userId, "dm", chatId, userId, text);
    }

    public GatewayMessage message(
            String chatId,
            String userId,
            String chatType,
            String chatName,
            String userName,
            String text) {
        GatewayMessage message = new GatewayMessage(PlatformType.MEMORY, chatId, userId, text);
        message.setChatType(chatType);
        message.setChatName(chatName);
        message.setUserName(userName);
        return message;
    }

    public GatewayReply send(String chatId, String userId, String text) throws Exception {
        return gatewayService.handle(message(chatId, userId, text));
    }

    private static AppConfig newConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("solon-claw-test").toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.getRuntime().setContextDir(new File(runtimeHome, "context").getAbsolutePath());
        config.getRuntime().setSkillsDir(new File(runtimeHome, "skills").getAbsolutePath());
        config.getRuntime().setCacheDir(new File(runtimeHome, "cache").getAbsolutePath());
        config.getRuntime()
                .setStateDb(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        config.getRuntime().setConfigFile(new File(runtimeHome, "config.yml").getAbsolutePath());
        config.getRuntime().setLogsDir(new File(runtimeHome, "logs").getAbsolutePath());
        AppConfig.ProviderConfig provider = new AppConfig.ProviderConfig();
        provider.setName("DefaultProvider");
        provider.setBaseUrl("https://api.openai.com");
        provider.setApiKey("");
        provider.setDefaultModel("gpt-5.4");
        provider.setDialect("openai");
        config.getProviders().put("default", provider);
        config.getModel().setProviderKey("default");
        config.getModel().setDefault("");
        config.getLlm().setProvider("default");
        config.getLlm().setDialect("openai");
        config.getLlm().setApiUrl("https://api.openai.com/v1/chat/completions");
        config.getLlm().setModel("gpt-5.4");
        config.getLlm().setReasoningEffort("medium");
        config.getLlm().setTemperature(0.2D);
        config.getLlm().setMaxTokens(4096);
        config.getScheduler().setEnabled(false);
        AppConfig.PersonalityConfig helpful = new AppConfig.PersonalityConfig();
        helpful.setDescription("friendly default");
        helpful.setSystemPrompt("You are a helpful assistant.");
        config.getAgent().getPersonalities().put("helpful", helpful);
        AppConfig.PersonalityConfig concise = new AppConfig.PersonalityConfig();
        concise.setDescription("brief answers");
        concise.setSystemPrompt("Be concise.");
        config.getAgent().getPersonalities().put("concise", concise);
        return config;
    }
}
