package com.jimuqu.claw.config;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.job.JobStoreService;
import com.jimuqu.claw.agent.job.WorkspaceJobService;
import com.jimuqu.claw.agent.runtime.impl.AgentRuntimeService;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.CancellationInterceptor;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.HeartbeatService;
import com.jimuqu.claw.agent.runtime.impl.IsolatedAgentRunService;
import com.jimuqu.claw.agent.runtime.impl.ReActLoggingInterceptor;
import com.jimuqu.claw.agent.runtime.impl.SolonAiConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.SystemEventRunner;
import com.jimuqu.claw.agent.runtime.registry.ActiveTaskRegistry;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.agent.tool.JobTools;
import com.jimuqu.claw.constitution.BlacklistInterceptor;
import com.jimuqu.claw.config.props.BlacklistProperties;
import org.noear.solon.ai.agent.react.intercept.HITLInterceptor;
import com.jimuqu.claw.agent.tool.WorkspaceAgentTools;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import com.jimuqu.claw.agent.workspace.WorkspacePromptService;
import com.jimuqu.claw.channel.dingtalk.service.DingTalkAccessTokenService;
import com.jimuqu.claw.channel.dingtalk.adapter.DingTalkChannelAdapter;
import com.jimuqu.claw.channel.dingtalk.sender.DingTalkRobotSender;
import com.jimuqu.claw.channel.feishu.sender.FeishuBotSender;
import com.jimuqu.claw.channel.feishu.adapter.FeishuChannelAdapter;
import com.jimuqu.claw.channel.weixin.adapter.WeixinChannelAdapter;
import com.jimuqu.claw.channel.weixin.sender.WeixinRobotSender;
import com.jimuqu.claw.channel.weixin.service.WeixinAccountStoreService;
import com.jimuqu.claw.channel.weixin.service.WeixinApiGateway;
import com.jimuqu.claw.channel.weixin.service.WeixinHttpGateway;
import com.jimuqu.claw.channel.weixin.service.WeixinLoginService;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.skills.cli.CliSkillProvider;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.BindProps;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.scheduling.scheduled.manager.IJobManager;

import java.io.File;

/**
 * 统一装配 SolonClaw 运行时依赖。
 */
@Configuration
public class SolonClawConfig {
    @Bean
    @BindProps(prefix = "solonclaw")
    public SolonClawProperties solonClawProperties() {
        return new SolonClawProperties();
    }

    @Bean
    public AgentWorkspaceService agentWorkspaceService(SolonClawProperties properties) {
        return new AgentWorkspaceService(properties.getWorkspace());
    }

    @Bean
    public WorkspacePromptService workspacePromptService(
            AgentWorkspaceService workspaceService,
            SolonClawProperties properties
    ) {
        return new WorkspacePromptService(workspaceService, properties.getAgent().getSystemPrompt());
    }

    @Bean
    public RuntimeStoreService runtimeStoreService(
            AgentWorkspaceService workspaceService
    ) {
        File runtimeDir = workspaceService.resolveWithinWorkspace(null, "runtime");
        return new RuntimeStoreService(runtimeDir);
    }

    @Bean
    public WorkspaceAgentTools workspaceAgentTools(
            AgentWorkspaceService workspaceService
    ) {
        return new WorkspaceAgentTools(workspaceService);
    }

    @Bean
    public JobStoreService jobStoreService(AgentWorkspaceService workspaceService) {
        return new JobStoreService(workspaceService);
    }

    @Bean(initMethod = "restorePersistedJobs")
    public WorkspaceJobService workspaceJobService(
            IJobManager jobManager,
            JobStoreService jobStoreService,
            RuntimeStoreService runtimeStoreService,
            SystemEventRunner systemEventRunner,
            IsolatedAgentRunService isolatedAgentRunService,
            SolonClawProperties properties
    ) {
        return new WorkspaceJobService(
                jobManager,
                jobStoreService,
                runtimeStoreService,
                systemEventRunner,
                isolatedAgentRunService,
                properties
        );
    }

    @Bean
    public JobTools jobTools(
            WorkspaceJobService workspaceJobService,
            SolonAiConversationAgent conversationAgent
    ) {
        JobTools jobTools = new JobTools(workspaceJobService);
        conversationAgent.setJobTools(jobTools);
        return jobTools;
    }

    @Bean
    public CliSkillProvider cliSkillProvider(
            AgentWorkspaceService workspaceService,
            SolonClawProperties properties
    ) {
        String workDir = workspaceService.getWorkspaceDir().getAbsolutePath();
        String skillsDir = FileUtil.mkdir(workspaceService.fileInWorkspace("skills")).getAbsolutePath();

        CliSkillProvider cliSkillProvider = new CliSkillProvider(workDir)
                .skillPool("@skills", skillsDir);

        cliSkillProvider.getTerminalSkill().setSandboxMode(
                properties.getAgent().getTools().isSandboxMode()
        );

        return cliSkillProvider;
    }

    @Bean
    public ActiveTaskRegistry activeTaskRegistry(RuntimeStoreService runtimeStoreService) {
        ActiveTaskRegistry registry = new ActiveTaskRegistry(
                new File(runtimeStoreService.getRuntimeDir(), "runs")
        );
        registry.rebuildFromDisk();
        return registry;
    }

    @Bean
    public ConversationScheduler conversationScheduler(SolonClawProperties properties) {
        return new ConversationScheduler(
                properties.getAgent().getScheduler().getMaxConcurrentPerConversation(),
                properties.getAgent().getScheduler().getMaxConcurrentUserMessage()
        );
    }

    @Bean
    public ReActLoggingInterceptor reActLoggingInterceptor() {
        return new ReActLoggingInterceptor();
    }

    @Bean
    public HITLInterceptor blacklistInterceptor(SolonClawProperties properties) {
        BlacklistProperties blacklistProps = properties.getAgent().getBlacklist();
        BlacklistInterceptor strategy = new BlacklistInterceptor(
                blacklistProps != null && blacklistProps.isEnabled() ? blacklistProps : null
        );
        return new HITLInterceptor().onTool("bash", strategy);
    }

    @Bean
    public SolonAiConversationAgent conversationAgent(
            ChatModel chatModel,
            WorkspacePromptService workspacePromptService,
            WorkspaceAgentTools workspaceAgentTools,
            CliSkillProvider cliSkillProvider,
            ReActLoggingInterceptor reActLoggingInterceptor,
            HITLInterceptor blacklistInterceptor,
            CancellationInterceptor cancellationInterceptor,
            ActiveTaskRegistry activeTaskRegistry
    ) {
        SolonAiConversationAgent agent = new SolonAiConversationAgent(
                chatModel,
                workspacePromptService,
                workspaceAgentTools,
                cliSkillProvider,
                reActLoggingInterceptor,
                blacklistInterceptor
        );
        agent.setCancellationInterceptor(cancellationInterceptor);
        agent.setActiveTaskRegistry(activeTaskRegistry);
        return agent;
    }

    @Bean
    public CancellationInterceptor cancellationInterceptor(ActiveTaskRegistry activeTaskRegistry) {
        return new CancellationInterceptor(activeTaskRegistry);
    }

    @Bean
    public ChannelRegistry channelRegistry() {
        return new ChannelRegistry();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SystemEventRunner systemEventRunner(
            ConversationAgent conversationAgent,
            RuntimeStoreService runtimeStoreService,
            ConversationScheduler conversationScheduler,
            ChannelRegistry channelRegistry,
            SolonClawProperties properties
    ) {
        return new SystemEventRunner(
                conversationAgent,
                runtimeStoreService,
                conversationScheduler,
                channelRegistry,
                properties
        );
    }

    @Bean
    public IsolatedAgentRunService isolatedAgentRunService(
            ConversationAgent conversationAgent,
            RuntimeStoreService runtimeStoreService,
            ConversationScheduler conversationScheduler,
            ChannelRegistry channelRegistry,
            SolonClawProperties properties
    ) {
        return new IsolatedAgentRunService(
                conversationAgent,
                runtimeStoreService,
                conversationScheduler,
                channelRegistry,
                properties
        );
    }

    @Bean
    public FeishuBotSender feishuBotSender(SolonClawProperties properties) {
        return new FeishuBotSender(properties.getChannels().getFeishu());
    }

    @Bean
    public WeixinAccountStoreService weixinAccountStoreService(AgentWorkspaceService workspaceService) {
        return new WeixinAccountStoreService(workspaceService);
    }

    @Bean
    public WeixinApiGateway weixinApiGateway() {
        return new WeixinHttpGateway();
    }

    @Bean
    public WeixinLoginService weixinLoginService(
            WeixinApiGateway apiGateway,
            WeixinAccountStoreService accountStoreService,
            SolonClawProperties properties,
            WeixinChannelAdapter weixinChannelAdapter
    ) {
        return new WeixinLoginService(apiGateway, accountStoreService, properties.getChannels().getWeixin(), weixinChannelAdapter);
    }

    @Bean
    public WeixinRobotSender weixinRobotSender(
            WeixinApiGateway apiGateway,
            WeixinAccountStoreService accountStoreService,
            SolonClawProperties properties
    ) {
        return new WeixinRobotSender(apiGateway, accountStoreService, properties.getChannels().getWeixin());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DingTalkAccessTokenService dingTalkAccessTokenService(SolonClawProperties properties) {
        return new DingTalkAccessTokenService(properties.getChannels().getDingtalk());
    }

    @Bean
    public DingTalkRobotSender dingTalkRobotSender(
            DingTalkAccessTokenService dingTalkAccessTokenService,
            SolonClawProperties properties
    ) throws Exception {
        return new DingTalkRobotSender(dingTalkAccessTokenService, properties.getChannels().getDingtalk());
    }

    @Bean(destroyMethod = "shutdown")
    public AgentRuntimeService agentRuntimeService(
            ConversationAgent conversationAgent,
            RuntimeStoreService runtimeStoreService,
            ConversationScheduler conversationScheduler,
            ChannelRegistry channelRegistry,
            SystemEventRunner systemEventRunner,
            ActiveTaskRegistry activeTaskRegistry,
            SolonClawProperties properties
    ) {
        return new AgentRuntimeService(
                conversationAgent,
                runtimeStoreService,
                conversationScheduler,
                channelRegistry,
                systemEventRunner,
                activeTaskRegistry,
                properties
        );
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DingTalkChannelAdapter dingTalkChannelAdapter(
            AgentRuntimeService agentRuntimeService,
            DingTalkRobotSender dingTalkRobotSender,
            ChannelRegistry channelRegistry,
            SolonClawProperties properties
    ) {
        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(
                agentRuntimeService,
                dingTalkRobotSender,
                properties.getChannels().getDingtalk()
        );
        channelRegistry.register(adapter);
        return adapter;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public FeishuChannelAdapter feishuChannelAdapter(
            AgentRuntimeService agentRuntimeService,
            FeishuBotSender feishuBotSender,
            ChannelRegistry channelRegistry,
            SolonClawProperties properties
    ) {
        FeishuChannelAdapter adapter = new FeishuChannelAdapter(
                agentRuntimeService,
                feishuBotSender,
                properties.getChannels().getFeishu()
        );
        channelRegistry.register(adapter);
        return adapter;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public WeixinChannelAdapter weixinChannelAdapter(
            AgentRuntimeService agentRuntimeService,
            WeixinRobotSender weixinRobotSender,
            WeixinAccountStoreService accountStoreService,
            WeixinApiGateway apiGateway,
            ChannelRegistry channelRegistry,
            SolonClawProperties properties
    ) {
        WeixinChannelAdapter adapter = new WeixinChannelAdapter(
                agentRuntimeService,
                weixinRobotSender,
                accountStoreService,
                apiGateway,
                properties.getChannels().getWeixin()
        );
        channelRegistry.register(adapter);
        return adapter;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public HeartbeatService heartbeatService(
            SystemEventRunner systemEventRunner,
            RuntimeStoreService runtimeStoreService,
            SolonClawProperties properties
    ) {
        return new HeartbeatService(systemEventRunner, runtimeStoreService, properties);
    }
}


