package com.jimuqu.claw.config;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.job.JobStoreService;
import com.jimuqu.claw.agent.job.WorkspaceJobService;
import com.jimuqu.claw.agent.runtime.impl.AgentRuntimeService;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.HeartbeatService;
import com.jimuqu.claw.agent.runtime.impl.IsolatedAgentRunService;
import com.jimuqu.claw.agent.runtime.impl.ReActLoggingInterceptor;
import com.jimuqu.claw.agent.runtime.impl.SolonAiConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.SystemEventRunner;
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
    /**
     * 绑定项目自定义配置。
     *
     * @return 配置对象
     */
    @Bean
    @BindProps(prefix = "solonclaw")
    public SolonClawProperties solonClawProperties() {
        return new SolonClawProperties();
    }

    /**
     * 创建工作区服务。
     *
     * @param properties 项目配置
     * @return 工作区服务
     */
    @Bean
    public AgentWorkspaceService agentWorkspaceService(SolonClawProperties properties) {
        return new AgentWorkspaceService(properties.getWorkspace());
    }

    /**
     * 创建工作区提示词服务。
     *
     * @param workspaceService 工作区服务
     * @return 工作区提示词服务
     */
    @Bean
    public WorkspacePromptService workspacePromptService(
            AgentWorkspaceService workspaceService,
            SolonClawProperties properties
    ) {
        return new WorkspacePromptService(workspaceService, properties.getAgent().getSystemPrompt());
    }

    /**
     * 创建运行时存储服务。
     *
     * @param workspaceService 工作区服务
     * @return 存储服务
     */
    @Bean
    public RuntimeStoreService runtimeStoreService(
            AgentWorkspaceService workspaceService
    ) {
        File runtimeDir = workspaceService.resolveWithinWorkspace(null, "runtime");
        return new RuntimeStoreService(runtimeDir);
    }

    /**
     * 创建工作区工具集。
     *
     * @param workspaceService 工作区服务
     * @return 工具集
     */
    @Bean
    public WorkspaceAgentTools workspaceAgentTools(
            AgentWorkspaceService workspaceService
    ) {
        return new WorkspaceAgentTools(workspaceService);
    }

    /**
     * 创建定时任务存储服务。
     *
     * @param workspaceService 工作区服务
     * @return 定时任务存储服务
     */
    @Bean
    public JobStoreService jobStoreService(AgentWorkspaceService workspaceService) {
        return new JobStoreService(workspaceService);
    }

    /**
     * 创建工作区定时任务服务，并在启动时恢复任务。
     *
     * @param jobManager 任务管理器
     * @param jobStoreService 定时任务存储服务
     * @return 工作区定时任务服务
     */
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

    /**
     * 创建定时任务工具。
     *
     * @param workspaceJobService 工作区定时任务服务
     * @return 定时任务工具
     */
    @Bean
    public JobTools jobTools(
            WorkspaceJobService workspaceJobService,
            SolonAiConversationAgent conversationAgent
    ) {
        JobTools jobTools = new JobTools(workspaceJobService);
        conversationAgent.setJobTools(jobTools);
        return jobTools;
    }

    /**
     * 创建 CLI 技能提供者，并挂载工作区技能目录。
     *
     * @param workspaceService 工作区服务
     * @return CLI 技能提供者
     */
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

    /**
     * 创建会话调度器。
     *
     * @param properties 项目配置
     * @return 会话调度器
     */
    @Bean
    public ConversationScheduler conversationScheduler(SolonClawProperties properties) {
        return new ConversationScheduler(properties.getAgent().getScheduler().getMaxConcurrentPerConversation());
    }

    /**
     * 创建 ReAct 运行日志拦截器。
     *
     * @return ReAct 日志拦截器
     */
    @Bean
    public ReActInterceptor reActLoggingInterceptor() {
        return new ReActLoggingInterceptor();
    }

    /**
     * 创建黑名单拦截器。
     * 始终创建，空黑名单不会拦截任何命令。
     */
    @Bean
    public HITLInterceptor blacklistInterceptor(SolonClawProperties properties) {
        BlacklistProperties blacklistProps = properties.getAgent().getBlacklist();
        BlacklistInterceptor strategy = new BlacklistInterceptor(
                blacklistProps != null && blacklistProps.isEnabled() ? blacklistProps : null
        );
        return new HITLInterceptor().onTool("bash", strategy);
    }

    /**
     * 创建会话执行 Agent。
     *
     * @param chatModel 聊天模型
     * @param workspacePromptService 工作区提示词服务
     * @return 会话执行 Agent
     */
    @Bean
    public SolonAiConversationAgent conversationAgent(
            ChatModel chatModel,
            WorkspacePromptService workspacePromptService,
            WorkspaceAgentTools workspaceAgentTools,
            CliSkillProvider cliSkillProvider,
            ReActInterceptor reActLoggingInterceptor,
            HITLInterceptor blacklistInterceptor
    ) {
        return new SolonAiConversationAgent(
                chatModel,
                workspacePromptService,
                workspaceAgentTools,
                cliSkillProvider,
                reActLoggingInterceptor,
                blacklistInterceptor
        );
    }

    /**
     * 创建渠道注册表。
     *
     * @return 渠道注册表
     */
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

    /**
     * 创建飞书消息发送服务。
     *
     * @param properties 项目配置
     * @return 飞书发送服务
     */
    @Bean
    public FeishuBotSender feishuBotSender(SolonClawProperties properties) {
        return new FeishuBotSender(properties.getChannels().getFeishu());
    }

    /**
     * 创建钉钉 token 服务。
     *
     * @param properties 项目配置
     * @return token 服务
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public DingTalkAccessTokenService dingTalkAccessTokenService(SolonClawProperties properties) {
        return new DingTalkAccessTokenService(properties.getChannels().getDingtalk());
    }

    /**
     * 创建钉钉机器人发送服务。
     *
     * @param dingTalkAccessTokenService token 服务
     * @param properties 项目配置
     * @return 发送服务
     * @throws Exception 创建底层客户端时的异常
     */
    @Bean
    public DingTalkRobotSender dingTalkRobotSender(
            DingTalkAccessTokenService dingTalkAccessTokenService,
            SolonClawProperties properties
    ) throws Exception {
        return new DingTalkRobotSender(dingTalkAccessTokenService, properties.getChannels().getDingtalk());
    }

    /**
     * 创建 Agent 运行时服务。
     *
     * @param conversationAgent 会话执行 Agent
     * @param runtimeStoreService 运行时存储服务
     * @param conversationScheduler 会话调度器
     * @param channelRegistry 渠道注册表
     * @param properties 项目配置
     * @return Agent 运行时服务
     */
    @Bean
    public AgentRuntimeService agentRuntimeService(
            ConversationAgent conversationAgent,
            RuntimeStoreService runtimeStoreService,
            ConversationScheduler conversationScheduler,
            ChannelRegistry channelRegistry,
            SystemEventRunner systemEventRunner,
            SolonClawProperties properties
    ) {
        return new AgentRuntimeService(
                conversationAgent,
                runtimeStoreService,
                conversationScheduler,
                channelRegistry,
                systemEventRunner,
                properties
        );
    }

    /**
     * 创建并注册钉钉渠道适配器。
     *
     * @param agentRuntimeService Agent 运行时服务
     * @param runtimeStoreService 运行时存储服务
     * @param dingTalkRobotSender 钉钉机器人发送服务
     * @param channelRegistry 渠道注册表
     * @param properties 项目配置
     * @return 钉钉渠道适配器
     */
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

    /**
     * 创建并注册飞书渠道适配器。
     *
     * @param agentRuntimeService Agent 运行时服务
     * @param feishuBotSender 飞书消息发送服务
     * @param channelRegistry 渠道注册表
     * @param properties 项目配置
     * @return 飞书渠道适配器
     */
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

    /**
     * 创建心跳服务。
     *
     * @param systemEventRunner 系统事件执行器
     * @param runtimeStoreService 运行时存储服务
     * @param properties 项目配置
     * @return 心跳服务
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public HeartbeatService heartbeatService(
            SystemEventRunner systemEventRunner,
            RuntimeStoreService runtimeStoreService,
            SolonClawProperties properties
    ) {
        return new HeartbeatService(systemEventRunner, runtimeStoreService, properties);
    }
}


