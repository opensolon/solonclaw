package com.jimuqu.solon.claw.bootstrap;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.context.PersonaWorkspaceService;
import com.jimuqu.solon.claw.context.SkillCuratorService;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.core.repository.GatewayPolicyRepository;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.scheduler.DefaultCronScheduler;
import com.jimuqu.solon.claw.scheduler.HeartbeatScheduler;
import com.jimuqu.solon.claw.scheduler.SkillCuratorScheduler;
import com.jimuqu.solon.claw.engine.AgentRunSupervisor;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/** scheduler bean configuration. */
@Configuration
public class SchedulerConfiguration {
    @Bean(destroyMethod = "shutdown")
    public DefaultCronScheduler defaultCronScheduler(
            AppConfig appConfig,
            CronJobRepository cronJobRepository,
            ConversationOrchestrator conversationOrchestrator,
            DeliveryService deliveryService,
            GatewayPolicyRepository gatewayPolicyRepository) {
        DefaultCronScheduler scheduler =
                new DefaultCronScheduler(
                        appConfig,
                        cronJobRepository,
                        conversationOrchestrator,
                        deliveryService,
                        gatewayPolicyRepository);
        scheduler.start();
        return scheduler;
    }

    @Bean(destroyMethod = "shutdown")
    public HeartbeatScheduler heartbeatScheduler(
            AppConfig appConfig,
            GatewayPolicyRepository gatewayPolicyRepository,
            ConversationOrchestrator conversationOrchestrator,
            DeliveryService deliveryService,
            PersonaWorkspaceService personaWorkspaceService) {
        HeartbeatScheduler scheduler =
                new HeartbeatScheduler(
                        appConfig,
                        gatewayPolicyRepository,
                        conversationOrchestrator,
                        deliveryService,
                        personaWorkspaceService);
        scheduler.start();
        return scheduler;
    }

    @Bean(destroyMethod = "shutdown")
    public SkillCuratorScheduler skillCuratorScheduler(
            AppConfig appConfig,
            SkillCuratorService skillCuratorService,
            AgentRunControlService agentRunControlService) {
        SkillCuratorScheduler scheduler =
                new SkillCuratorScheduler(appConfig, skillCuratorService, agentRunControlService);
        scheduler.start();
        return scheduler;
    }

    @Bean
    public Object staleRunRecoveryBootstrap(
            AppConfig appConfig, AgentRunSupervisor agentRunSupervisor) {
        long staleAfterMinutes = Math.max(1, appConfig.getTask().getStaleAfterMinutes());
        agentRunSupervisor.recoverStaleRuns(staleAfterMinutes * 60L * 1000L);
        return new Object();
    }
}
