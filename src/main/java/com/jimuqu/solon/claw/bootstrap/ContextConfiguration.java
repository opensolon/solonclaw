package com.jimuqu.solon.claw.bootstrap;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.agent.AgentProfileRepository;
import com.jimuqu.solon.claw.agent.AgentProfileService;
import com.jimuqu.solon.claw.agent.AgentRuntimeService;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.context.AsyncSkillLearningService;
import com.jimuqu.solon.claw.context.BuiltinMemoryProvider;
import com.jimuqu.solon.claw.context.DefaultMemoryManager;
import com.jimuqu.solon.claw.context.FileContextService;
import com.jimuqu.solon.claw.context.FileMemoryService;
import com.jimuqu.solon.claw.context.LocalSkillService;
import com.jimuqu.solon.claw.context.PersonaWorkspaceService;
import com.jimuqu.solon.claw.context.SkillCuratorService;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.core.service.ContextBudgetService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.core.service.MemoryManager;
import com.jimuqu.solon.claw.core.service.MemoryProvider;
import com.jimuqu.solon.claw.core.service.MemoryService;
import com.jimuqu.solon.claw.core.service.SessionSearchService;
import com.jimuqu.solon.claw.core.service.SkillGuardService;
import com.jimuqu.solon.claw.core.service.SkillHubService;
import com.jimuqu.solon.claw.core.service.SkillImportService;
import com.jimuqu.solon.claw.core.service.SkillLearningService;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.engine.DefaultContextBudgetService;
import com.jimuqu.solon.claw.engine.DefaultContextCompressionService;
import com.jimuqu.solon.claw.engine.DefaultSessionSearchService;
import com.jimuqu.solon.claw.skillhub.service.DefaultSkillGuardService;
import com.jimuqu.solon.claw.skillhub.service.DefaultSkillHubService;
import com.jimuqu.solon.claw.skillhub.service.DefaultSkillImportService;
import com.jimuqu.solon.claw.skillhub.source.GitHubSkillSource;
import com.jimuqu.solon.claw.skillhub.support.DefaultSkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.GitHubAuth;
import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import com.jimuqu.solon.claw.storage.repository.SqlitePreferenceStore;
import java.io.File;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/** context bean configuration. */
@Configuration
public class ContextConfiguration {
    @Bean
    public LocalSkillService localSkillService(
            AppConfig appConfig,
            SqlitePreferenceStore preferenceStore,
            SkillImportService skillImportService,
            SkillHubStateStore skillHubStateStore) {
        return new LocalSkillService(
                appConfig, preferenceStore, skillImportService, skillHubStateStore);
    }

    @Bean
    public PersonaWorkspaceService personaWorkspaceService(AppConfig appConfig) {
        return new PersonaWorkspaceService(appConfig);
    }

    @Bean
    public SkillCuratorService skillCuratorService(
            AppConfig appConfig, LocalSkillService localSkillService) {
        return new SkillCuratorService(appConfig, localSkillService);
    }

    @Bean
    public AgentRuntimeService agentRuntimeService(
            AppConfig appConfig, AgentProfileRepository agentProfileRepository) {
        return new AgentRuntimeService(appConfig, agentProfileRepository);
    }

    @Bean
    public AgentProfileService agentProfileService(
            AgentProfileRepository agentProfileRepository,
            AgentRuntimeService agentRuntimeService) {
        return new AgentProfileService(agentProfileRepository, agentRuntimeService);
    }

    @Bean
    public FileContextService fileContextService(
            AppConfig appConfig,
            LocalSkillService localSkillService,
            MemoryManager memoryManager,
            GlobalSettingRepository globalSettingRepository,
            PersonaWorkspaceService personaWorkspaceService) {
        return new FileContextService(
                appConfig,
                localSkillService,
                memoryManager,
                globalSettingRepository,
                personaWorkspaceService);
    }

    @Bean
    public MemoryService memoryService(AppConfig appConfig) {
        return new FileMemoryService(appConfig);
    }

    @Bean
    public SkillHubStateStore skillHubStateStore(AppConfig appConfig) {
        return new SkillHubStateStore(FileUtil.file(appConfig.getRuntime().getSkillsDir()));
    }

    @Bean
    public SkillHubHttpClient skillHubHttpClient() {
        return new DefaultSkillHubHttpClient();
    }

    @Bean
    public GitHubAuth gitHubAuth(SkillHubHttpClient skillHubHttpClient) {
        return new GitHubAuth(skillHubHttpClient);
    }

    @Bean
    public SkillGuardService skillGuardService() {
        return new DefaultSkillGuardService();
    }

    @Bean
    public SkillImportService skillImportService(
            AppConfig appConfig,
            SkillGuardService skillGuardService,
            SkillHubStateStore skillHubStateStore) {
        return new DefaultSkillImportService(
                FileUtil.file(appConfig.getRuntime().getSkillsDir()),
                skillGuardService,
                skillHubStateStore);
    }

    @Bean
    public GitHubSkillSource gitHubSkillSource(
            GitHubAuth gitHubAuth,
            SkillHubHttpClient skillHubHttpClient,
            SkillHubStateStore skillHubStateStore) {
        return new GitHubSkillSource(gitHubAuth, skillHubHttpClient, skillHubStateStore);
    }

    @Bean
    public SkillHubService skillHubService(
            AppConfig appConfig,
            SkillImportService skillImportService,
            SkillGuardService skillGuardService,
            SkillHubStateStore skillHubStateStore,
            SkillHubHttpClient skillHubHttpClient,
            GitHubAuth gitHubAuth,
            GitHubSkillSource gitHubSkillSource) {
        return new DefaultSkillHubService(
                new File(System.getProperty("user.dir")),
                FileUtil.file(appConfig.getRuntime().getSkillsDir()),
                skillImportService,
                skillGuardService,
                skillHubStateStore,
                skillHubHttpClient,
                gitHubAuth,
                gitHubSkillSource);
    }

    @Bean
    public MemoryProvider builtinMemoryProvider(MemoryService memoryService) {
        return new BuiltinMemoryProvider(memoryService);
    }

    @Bean
    public MemoryManager memoryManager(MemoryProvider builtinMemoryProvider) {
        java.util.List<MemoryProvider> providers = new java.util.ArrayList<MemoryProvider>();
        providers.add(builtinMemoryProvider);
        return new DefaultMemoryManager(providers);
    }

    @Bean
    public ContextCompressionService contextCompressionService(AppConfig appConfig) {
        return new DefaultContextCompressionService(appConfig);
    }

    @Bean
    public ContextBudgetService contextBudgetService(AppConfig appConfig) {
        return new DefaultContextBudgetService(appConfig);
    }

    @Bean
    public SessionSearchService sessionSearchService(
            SessionRepository sessionRepository,
            LlmGateway llmGateway,
            AgentRunRepository agentRunRepository) {
        return new DefaultSessionSearchService(sessionRepository, llmGateway, agentRunRepository);
    }

    @Bean(destroyMethod = "shutdown")
    public SkillLearningService skillLearningService(
            AppConfig appConfig,
            SessionRepository sessionRepository,
            MemoryService memoryService,
            LocalSkillService localSkillService,
            CheckpointService checkpointService,
            LlmGateway llmGateway,
            SqliteDatabase sqliteDatabase) {
        return new AsyncSkillLearningService(
                appConfig,
                sessionRepository,
                memoryService,
                localSkillService,
                checkpointService,
                llmGateway,
                sqliteDatabase);
    }
}
