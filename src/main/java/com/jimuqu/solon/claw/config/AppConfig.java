package com.jimuqu.solon.claw.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.llm.LlmProviderSupport;
import com.jimuqu.solon.claw.support.constants.CheckpointConstants;
import com.jimuqu.solon.claw.support.constants.CompressionConstants;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import com.jimuqu.solon.claw.support.constants.RuntimePathConstants;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.noear.snack4.ONode;
import org.noear.solon.core.Props;
import org.yaml.snakeyaml.Yaml;

/** 应用级配置对象，负责承接 Solon 配置并做外部 config.yml 覆盖与路径标准化。 */
@Getter
@Setter
@NoArgsConstructor
public class AppConfig {
    /** 运行时目录配置。 */
    private RuntimeConfig runtime = new RuntimeConfig();

    /** 大模型接入配置。 */
    private LlmConfig llm = new LlmConfig();

    /** 多 provider 原始配置。 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<String, ProviderConfig>();

    /** 当前主模型选择。 */
    private ModelConfig model = new ModelConfig();

    /** 主模型故障切换链。 */
    private List<FallbackProviderConfig> fallbackProviders =
            new ArrayList<FallbackProviderConfig>();

    /** 定时任务调度配置。 */
    private SchedulerConfig scheduler = new SchedulerConfig();

    /** 上下文压缩配置。 */
    private CompressionConfig compression = new CompressionConfig();

    /** 任务后自动学习配置。 */
    private LearningConfig learning = new LearningConfig();

    /** 技能后台维护配置。 */
    private CuratorConfig curator = new CuratorConfig();

    /** 文件快照与回滚配置。 */
    private RollbackConfig rollback = new RollbackConfig();

    /** 聊天窗口显示配置。 */
    private DisplayConfig display = new DisplayConfig();

    /** 各渠道接入配置。 */
    private ChannelsConfig channels = new ChannelsConfig();

    /** 网关通用授权配置。 */
    private GatewayConfig gateway = new GatewayConfig();

    /** Dashboard and API access configuration. */
    private DashboardConfig dashboard = new DashboardConfig();

    /** Agent 运行配置。 */
    private AgentConfig agent = new AgentConfig();

    /** ReAct 运行配置。 */
    private ReActConfig react = new ReActConfig();

    /** Agent run 追踪配置。 */
    private TraceConfig trace = new TraceConfig();

    /** 长任务控制配置。 */
    private TaskConfig task = new TaskConfig();

    /** MCP 工具适配配置。 */
    private McpConfig mcp = new McpConfig();

    /**
     * 从 Solon Props 构建应用配置。
     *
     * @param props Solon 启动时加载的配置源
     * @return 标准化后的配置对象
     */
    public static AppConfig load(Props props) {
        AppConfig config = new AppConfig();
        File userDir = new File(System.getProperty("user.dir"));
        File runtimeHome = asAbsoluteStatic(new File(resolveInitialRuntimeHome(props)), userDir);
        initializeRuntimeConfigIfMissing(runtimeHome);
        Map<String, Object> overrides = loadFlatOverrides(runtimeHome);
        Map<String, Object> structuredOverrides = loadStructuredOverrides(runtimeHome);
        RuntimeConfigResolver configResolver =
                RuntimeConfigResolver.initialize(runtimeHome.getAbsolutePath());

        config.getRuntime().setHome(resolveConfigString(runtimeHome.getPath()));
        config.getRuntime()
                .setContextDir(
                        runtimeChildPath(
                                config.getRuntime().getHome(),
                                RuntimePathConstants.CONTEXT_DIR_NAME));
        config.getRuntime()
                .setSkillsDir(
                        runtimeChildPath(
                                config.getRuntime().getHome(),
                                RuntimePathConstants.SKILLS_DIR_NAME));
        config.getRuntime()
                .setCacheDir(
                        runtimeChildPath(
                                config.getRuntime().getHome(),
                                RuntimePathConstants.CACHE_DIR_NAME));
        config.getRuntime()
                .setStateDb(
                        runtimeChildPath(
                                config.getRuntime().getHome(),
                                RuntimePathConstants.DATA_DIR_NAME,
                                RuntimePathConstants.STATE_DB_FILE_NAME));
        config.getRuntime().setConfigFile(configResolver.configFile().getPath());
        config.getRuntime()
                .setLogsDir(
                        runtimeChildPath(
                                config.getRuntime().getHome(), RuntimePathConstants.LOGS_DIR_NAME));

        config.getLlm()
                .setStream(
                        resolveBoolean(
                                readBoolean(props, overrides, "solonclaw.llm.stream", true)));
        config.getLlm()
                .setReasoningEffort(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.llm.reasoningEffort",
                                        RuntimePathConstants.DEFAULT_REASONING_EFFORT)));
        config.getLlm()
                .setTemperature(
                        resolveDouble(
                                readDouble(
                                        props,
                                        overrides,
                                        "solonclaw.llm.temperature",
                                        RuntimePathConstants.DEFAULT_TEMPERATURE)));
        config.getLlm()
                .setMaxTokens(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.llm.maxTokens",
                                        RuntimePathConstants.DEFAULT_MAX_TOKENS)));
        config.getLlm()
                .setContextWindowTokens(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.llm.contextWindowTokens",
                                        RuntimePathConstants.DEFAULT_CONTEXT_WINDOW_TOKENS)));
        applyProviderConfiguration(config, props, overrides, structuredOverrides);

        config.getScheduler()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props, overrides, "solonclaw.scheduler.enabled", true)));
        config.getScheduler()
                .setTickSeconds(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.scheduler.tickSeconds",
                                        RuntimePathConstants.DEFAULT_SCHEDULER_TICK_SECONDS)));

        config.getCompression()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props, overrides, "solonclaw.compression.enabled", true)));
        config.getCompression()
                .setThresholdPercent(
                        resolveDouble(
                                readDouble(
                                        props,
                                        overrides,
                                        "solonclaw.compression.thresholdPercent",
                                        CompressionConstants.DEFAULT_THRESHOLD_PERCENT)));
        config.getCompression()
                .setSummaryModel(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.compression.summaryModel",
                                        "")));
        config.getCompression()
                .setProtectHeadMessages(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.compression.protectHeadMessages",
                                        CompressionConstants.DEFAULT_PROTECT_HEAD_MESSAGES)));
        config.getCompression()
                .setTailRatio(
                        resolveDouble(
                                readDouble(
                                        props,
                                        overrides,
                                        "solonclaw.compression.tailRatio",
                                        CompressionConstants.DEFAULT_TAIL_RATIO)));

        config.getLearning()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(props, overrides, "solonclaw.learning.enabled", true)));
        config.getLearning()
                .setToolCallThreshold(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.learning.toolCallThreshold",
                                        5)));
        config.getCurator()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.skills.curator.enabled",
                                        true)));
        config.getCurator()
                .setIntervalHours(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.skills.curator.intervalHours",
                                        168)));
        config.getCurator()
                .setMinIdleHours(
                        resolveDouble(
                                readDouble(
                                        props,
                                        overrides,
                                        "solonclaw.skills.curator.minIdleHours",
                                        2.0D)));
        config.getCurator()
                .setStaleAfterDays(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.skills.curator.staleAfterDays",
                                        30)));
        config.getCurator()
                .setArchiveAfterDays(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.skills.curator.archiveAfterDays",
                                        90)));

        config.getRollback()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(props, overrides, "solonclaw.rollback.enabled", true)));
        config.getRollback()
                .setMaxCheckpointsPerSource(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.rollback.maxCheckpointsPerSource",
                                        CheckpointConstants.DEFAULT_MAX_CHECKPOINTS_PER_SOURCE)));

        config.getDisplay()
                .setToolProgress(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.display.toolProgress",
                                        "all")));
        config.getDisplay()
                .setShowReasoning(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.display.showReasoning",
                                        false)));
        config.getDisplay()
                .setToolPreviewLength(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.display.toolPreviewLength",
                                        80)));
        config.getDisplay()
                .setProgressThrottleMs(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.display.progressThrottleMs",
                                        1500)));
        config.getDisplay()
                .getRuntimeFooter()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.display.runtimeFooter.enabled",
                                        false)));
        config.getDisplay()
                .getRuntimeFooter()
                .setFields(
                        resolveList(
                                readRaw(
                                        props,
                                        overrides,
                                        "solonclaw.display.runtimeFooter.fields",
                                        "model,context_pct,cwd")));

        applyChannelConfig(
                config.getChannels().getFeishu(),
                props,
                overrides,
                "feishu",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST);
        config.getChannels()
                .getFeishu()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.enabled",
                                        false)));
        config.getChannels()
                .getFeishu()
                .setAppId(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.channels.feishu.appId", "")));
        config.getChannels()
                .getFeishu()
                .setAppSecret(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.appSecret",
                                        "")));
        config.getChannels()
                .getFeishu()
                .setWebsocketUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.websocketUrl",
                                        "")));
        config.getChannels()
                .getFeishu()
                .setBotOpenId(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.botOpenId",
                                        "")));
        config.getChannels()
                .getFeishu()
                .setBotUserId(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.botUserId",
                                        "")));
        config.getChannels()
                .getFeishu()
                .setBotName(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.botName",
                                        "")));
        config.getChannels()
                .getFeishu()
                .setToolProgress(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.toolProgress",
                                        "all")));
        config.getChannels()
                .getFeishu()
                .setCommentEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.comment.enabled",
                                        false)));
        config.getChannels()
                .getFeishu()
                .setCommentPairingFile(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.feishu.comment.pairingFile",
                                        "")));
        config.getChannels()
                .getFeishu()
                .setRuntimeFooterEnabled(
                        resolveOptionalBoolean(
                                readRaw(
                                        props,
                                        overrides,
                                        "solonclaw.display.platforms.feishu.runtimeFooter.enabled",
                                        null)));

        applyChannelConfig(
                config.getChannels().getDingtalk(),
                props,
                overrides,
                "dingtalk",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                GatewayBehaviorConstants.GROUP_POLICY_OPEN);
        config.getChannels()
                .getDingtalk()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.enabled",
                                        false)));
        config.getChannels()
                .getDingtalk()
                .setClientId(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.clientId",
                                        "")));
        config.getChannels()
                .getDingtalk()
                .setClientSecret(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.clientSecret",
                                        "")));
        config.getChannels()
                .getDingtalk()
                .setRobotCode(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.robotCode",
                                        "")));
        config.getChannels()
                .getDingtalk()
                .setCoolAppCode(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.coolAppCode",
                                        "")));
        config.getChannels()
                .getDingtalk()
                .setStreamUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.streamUrl",
                                        "")));
        config.getChannels()
                .getDingtalk()
                .setToolProgress(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.toolProgress",
                                        "all")));
        config.getChannels()
                .getDingtalk()
                .setProgressCardTemplateId(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.progressCardTemplateId",
                                        "")));
        config.getChannels()
                .getDingtalk()
                .setAiCardStreamingEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.dingtalk.aiCardStreaming.enabled",
                                        true)));
        config.getChannels()
                .getDingtalk()
                .setRuntimeFooterEnabled(
                        resolveOptionalBoolean(
                                readRaw(
                                        props,
                                        overrides,
                                        "solonclaw.display.platforms.dingtalk.runtimeFooter.enabled",
                                        null)));

        applyChannelConfig(
                config.getChannels().getWecom(),
                props,
                overrides,
                "wecom",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                GatewayBehaviorConstants.GROUP_POLICY_OPEN);
        config.getChannels()
                .getWecom()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.wecom.enabled",
                                        false)));
        config.getChannels()
                .getWecom()
                .setBotId(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.channels.wecom.botId", "")));
        config.getChannels()
                .getWecom()
                .setSecret(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.channels.wecom.secret", "")));
        config.getChannels()
                .getWecom()
                .setWebsocketUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.wecom.websocketUrl",
                                        "")));
        config.getChannels()
                .getWecom()
                .setToolProgress(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.wecom.toolProgress",
                                        "all")));
        config.getChannels()
                .getWecom()
                .setGroupMemberAllowedUsers(
                        collectGroupAllowMap(
                                props,
                                overrides,
                                "solonclaw.channels.wecom.groups.",
                                "solonclaw.channels.wecom.groupMemberAllowedUsers"));
        config.getChannels()
                .getWecom()
                .setRuntimeFooterEnabled(
                        resolveOptionalBoolean(
                                readRaw(
                                        props,
                                        overrides,
                                        "solonclaw.display.platforms.wecom.runtimeFooter.enabled",
                                        null)));

        applyChannelConfig(
                config.getChannels().getWeixin(),
                props,
                overrides,
                "weixin",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                GatewayBehaviorConstants.GROUP_POLICY_DISABLED);
        config.getChannels()
                .getWeixin()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.enabled",
                                        false)));
        config.getChannels()
                .getWeixin()
                .setToken(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.channels.weixin.token", "")));
        config.getChannels()
                .getWeixin()
                .setAccountId(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.accountId",
                                        "")));
        config.getChannels()
                .getWeixin()
                .setBaseUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.baseUrl",
                                        "")));
        config.getChannels()
                .getWeixin()
                .setCdnBaseUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.cdnBaseUrl",
                                        "")));
        config.getChannels()
                .getWeixin()
                .setLongPollUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.longPollUrl",
                                        "")));
        config.getChannels()
                .getWeixin()
                .setSplitMultilineMessages(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.splitMultilineMessages",
                                        false)));
        config.getChannels()
                .getWeixin()
                .setSendChunkDelaySeconds(
                        resolveDouble(
                                readDouble(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.sendChunkDelaySeconds",
                                        0.35D)));
        config.getChannels()
                .getWeixin()
                .setSendChunkRetries(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.sendChunkRetries",
                                        2)));
        config.getChannels()
                .getWeixin()
                .setSendChunkRetryDelaySeconds(
                        resolveDouble(
                                readDouble(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.sendChunkRetryDelaySeconds",
                                        1.0D)));
        config.getChannels()
                .getWeixin()
                .setToolProgress(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.weixin.toolProgress",
                                        "off")));
        config.getChannels()
                .getWeixin()
                .setRuntimeFooterEnabled(
                        resolveOptionalBoolean(
                                readRaw(
                                        props,
                                        overrides,
                                        "solonclaw.display.platforms.weixin.runtimeFooter.enabled",
                                        null)));

        applyChannelConfig(
                config.getChannels().getQqbot(),
                props,
                overrides,
                "qqbot",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                GatewayBehaviorConstants.GROUP_POLICY_OPEN);
        config.getChannels()
                .getQqbot()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.qqbot.enabled",
                                        false)));
        config.getChannels()
                .getQqbot()
                .setAppId(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.channels.qqbot.appId", "")));
        config.getChannels()
                .getQqbot()
                .setClientSecret(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.qqbot.clientSecret",
                                        "")));
        config.getChannels()
                .getQqbot()
                .setApiDomain(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.qqbot.apiDomain",
                                        "")));
        config.getChannels()
                .getQqbot()
                .setWebsocketUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.qqbot.websocketUrl",
                                        "")));
        config.getChannels()
                .getQqbot()
                .setMarkdownSupport(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.qqbot.markdownSupport",
                                        true)));
        config.getChannels()
                .getQqbot()
                .setToolProgress(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.qqbot.toolProgress",
                                        "all")));
        config.getChannels()
                .getQqbot()
                .setRuntimeFooterEnabled(
                        resolveOptionalBoolean(
                                readRaw(
                                        props,
                                        overrides,
                                        "solonclaw.display.platforms.qqbot.runtimeFooter.enabled",
                                        null)));

        applyChannelConfig(
                config.getChannels().getYuanbao(),
                props,
                overrides,
                "yuanbao",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                GatewayBehaviorConstants.GROUP_POLICY_OPEN);
        config.getChannels()
                .getYuanbao()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.channels.yuanbao.enabled",
                                        false)));
        config.getChannels()
                .getYuanbao()
                .setAppId(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.channels.yuanbao.appId", "")));
        config.getChannels()
                .getYuanbao()
                .setAppSecret(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.yuanbao.appSecret",
                                        "")));
        config.getChannels()
                .getYuanbao()
                .setBotId(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.channels.yuanbao.botId", "")));
        config.getChannels()
                .getYuanbao()
                .setApiDomain(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.yuanbao.apiDomain",
                                        "")));
        config.getChannels()
                .getYuanbao()
                .setWebsocketUrl(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.yuanbao.websocketUrl",
                                        "")));
        config.getChannels()
                .getYuanbao()
                .setToolProgress(
                        resolveConfigString(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.channels.yuanbao.toolProgress",
                                        "all")));
        config.getChannels()
                .getYuanbao()
                .setRuntimeFooterEnabled(
                        resolveOptionalBoolean(
                                readRaw(
                                        props,
                                        overrides,
                                        "solonclaw.display.platforms.yuanbao.runtimeFooter.enabled",
                                        null)));

        config.getGateway()
                .setAllowedUsers(
                        resolveList(
                                readRaw(props, overrides, "solonclaw.gateway.allowedUsers", "")));
        config.getGateway()
                .setAllowAllUsers(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.gateway.allowAllUsers",
                                        false)));
        config.getGateway()
                .setInjectionSecret(
                        resolveSecret(
                                readString(
                                        props,
                                        overrides,
                                        "solonclaw.gateway.injectionSecret",
                                        "")));
        config.getGateway()
                .setInjectionMaxBodyBytes(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.gateway.injectionMaxBodyBytes",
                                        65536)));
        config.getGateway()
                .setInjectionReplayWindowSeconds(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.gateway.injectionReplayWindowSeconds",
                                        300)));
        config.getDashboard()
                .setAccessToken(
                        resolveSecret(
                                readString(
                                        props, overrides, "solonclaw.dashboard.accessToken", "")));
        config.getAgent().setPersonalities(loadPersonalities(props, overrides));
        config.getAgent()
                .getHeartbeat()
                .setIntervalMinutes(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.agent.heartbeat.intervalMinutes",
                                        RuntimePathConstants.DEFAULT_HEARTBEAT_INTERVAL_MINUTES)));
        config.getReact()
                .setMaxSteps(resolveInt(readInt(props, overrides, "solonclaw.react.maxSteps", 12)));
        config.getReact()
                .setRetryMax(resolveInt(readInt(props, overrides, "solonclaw.react.retryMax", 3)));
        config.getReact()
                .setRetryDelayMs(
                        resolveInt(
                                readInt(props, overrides, "solonclaw.react.retryDelayMs", 2000)));
        config.getReact()
                .setDelegateMaxSteps(
                        resolveInt(
                                readInt(props, overrides, "solonclaw.react.delegateMaxSteps", 18)));
        config.getReact()
                .setDelegateRetryMax(
                        resolveInt(
                                readInt(props, overrides, "solonclaw.react.delegateRetryMax", 4)));
        config.getReact()
                .setDelegateRetryDelayMs(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.react.delegateRetryDelayMs",
                                        2500)));
        config.getReact()
                .setSummarizationEnabled(
                        resolveBoolean(
                                readBoolean(
                                        props,
                                        overrides,
                                        "solonclaw.react.summarizationEnabled",
                                        true)));
        config.getReact()
                .setSummarizationMaxMessages(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.react.summarizationMaxMessages",
                                        40)));
        config.getReact()
                .setSummarizationMaxTokens(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.react.summarizationMaxTokens",
                                        32000)));
        config.getTrace()
                .setRetentionDays(
                        resolveInt(
                                readInt(props, overrides, "solonclaw.trace.retentionDays", 14)));
        config.getTrace()
                .setMaxAttempts(
                        resolveInt(readInt(props, overrides, "solonclaw.trace.maxAttempts", 2)));
        config.getTrace()
                .setToolPreviewLength(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.trace.toolPreviewLength",
                                        1200)));
        config.getTask()
                .setBusyPolicy(
                        resolveConfigString(
                                readString(props, overrides, "solonclaw.task.busyPolicy", "queue")));
        config.getTask()
                .setStaleAfterMinutes(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.task.staleAfterMinutes",
                                        60)));
        config.getTask()
                .setSubagentMaxConcurrency(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.task.subagentMaxConcurrency",
                                        3)));
        config.getTask()
                .setSubagentMaxDepth(
                        resolveInt(
                                readInt(
                                        props, overrides, "solonclaw.task.subagentMaxDepth", 1)));
        config.getTask()
                .setToolOutputInlineLimit(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.task.toolOutputInlineLimit",
                                        4000)));
        config.getTask()
                .setMediaCacheTtlHours(
                        resolveInt(
                                readInt(
                                        props,
                                        overrides,
                                        "solonclaw.task.mediaCacheTtlHours",
                                        168)));
        config.getMcp()
                .setEnabled(
                        resolveBoolean(
                                readBoolean(props, overrides, "solonclaw.mcp.enabled", false)));

        config.normalizePaths();
        syncRuntimeConfigExample(config.getRuntime().getHome());
        return config;
    }

    /** 标准化运行时路径，确保所有路径都转换为基于 `user.dir` 的绝对路径。 */
    public void normalizePaths() {
        File userDir = new File(System.getProperty("user.dir"));
        File runtimeHome =
                asAbsolute(
                        new File(
                                StrUtil.blankToDefault(
                                        runtime.getHome(), RuntimePathConstants.RUNTIME_HOME)),
                        userDir);
        runtime.setHome(runtimeHome.getAbsolutePath());
        runtime.setContextDir(
                new File(runtimeHome, RuntimePathConstants.CONTEXT_DIR_NAME).getAbsolutePath());
        runtime.setSkillsDir(
                new File(runtimeHome, RuntimePathConstants.SKILLS_DIR_NAME).getAbsolutePath());
        runtime.setCacheDir(
                new File(runtimeHome, RuntimePathConstants.CACHE_DIR_NAME).getAbsolutePath());
        runtime.setStateDb(
                new File(
                                new File(runtimeHome, RuntimePathConstants.DATA_DIR_NAME),
                                RuntimePathConstants.STATE_DB_FILE_NAME)
                        .getAbsolutePath());
        runtime.setConfigFile(
                new File(runtimeHome, RuntimePathConstants.CONFIG_FILE_NAME).getAbsolutePath());
        runtime.setLogsDir(
                new File(runtimeHome, RuntimePathConstants.LOGS_DIR_NAME).getAbsolutePath());
    }

    /** 用新的配置快照覆盖当前实例，保留对象引用稳定。 */
    public synchronized void applyFrom(AppConfig other) {
        if (other == null) {
            return;
        }
        copyRuntime(other.getRuntime());
        copyLlm(other.getLlm());
        copyProviders(other.getProviders());
        copyModel(other.getModel());
        copyFallbackProviders(other.getFallbackProviders());
        copyScheduler(other.getScheduler());
        copyCompression(other.getCompression());
        copyLearning(other.getLearning());
        copyCurator(other.getCurator());
        copyRollback(other.getRollback());
        copyDisplay(other.getDisplay());
        copyReact(other.getReact());
        copyTrace(other.getTrace());
        copyTask(other.getTask());
        copyMcp(other.getMcp());
        copyChannel(this.channels.getFeishu(), other.getChannels().getFeishu());
        copyChannel(this.channels.getDingtalk(), other.getChannels().getDingtalk());
        copyChannel(this.channels.getWecom(), other.getChannels().getWecom());
        copyChannel(this.channels.getWeixin(), other.getChannels().getWeixin());
        copyChannel(this.channels.getQqbot(), other.getChannels().getQqbot());
        copyChannel(this.channels.getYuanbao(), other.getChannels().getYuanbao());
        this.gateway.setAllowedUsers(new ArrayList<String>(other.getGateway().getAllowedUsers()));
        this.gateway.setAllowAllUsers(other.getGateway().isAllowAllUsers());
        this.gateway.setInjectionSecret(other.getGateway().getInjectionSecret());
        this.gateway.setInjectionMaxBodyBytes(other.getGateway().getInjectionMaxBodyBytes());
        this.gateway.setInjectionReplayWindowSeconds(
                other.getGateway().getInjectionReplayWindowSeconds());
        this.dashboard.setAccessToken(other.getDashboard().getAccessToken());
        this.agent.setPersonalities(clonePersonalities(other.getAgent().getPersonalities()));
        this.agent
                .getHeartbeat()
                .setIntervalMinutes(other.getAgent().getHeartbeat().getIntervalMinutes());
    }

    private void copyRuntime(RuntimeConfig other) {
        this.runtime.setHome(other.getHome());
        this.runtime.setContextDir(other.getContextDir());
        this.runtime.setSkillsDir(other.getSkillsDir());
        this.runtime.setCacheDir(other.getCacheDir());
        this.runtime.setStateDb(other.getStateDb());
        this.runtime.setConfigFile(other.getConfigFile());
        this.runtime.setLogsDir(other.getLogsDir());
    }

    private void copyLlm(LlmConfig other) {
        this.llm.setProvider(other.getProvider());
        this.llm.setDialect(other.getDialect());
        this.llm.setApiUrl(other.getApiUrl());
        this.llm.setApiKey(other.getApiKey());
        this.llm.setModel(other.getModel());
        this.llm.setStream(other.isStream());
        this.llm.setReasoningEffort(other.getReasoningEffort());
        this.llm.setTemperature(other.getTemperature());
        this.llm.setMaxTokens(other.getMaxTokens());
        this.llm.setContextWindowTokens(other.getContextWindowTokens());
    }

    private void copyProviders(Map<String, ProviderConfig> other) {
        this.providers = new LinkedHashMap<String, ProviderConfig>();
        if (other == null) {
            return;
        }
        for (Map.Entry<String, ProviderConfig> entry : other.entrySet()) {
            ProviderConfig source = entry.getValue();
            if (source == null) {
                continue;
            }
            ProviderConfig copy = new ProviderConfig();
            copy.setName(source.getName());
            copy.setBaseUrl(source.getBaseUrl());
            copy.setApiKey(source.getApiKey());
            copy.setDefaultModel(source.getDefaultModel());
            copy.setDialect(source.getDialect());
            this.providers.put(entry.getKey(), copy);
        }
    }

    private void copyModel(ModelConfig other) {
        this.model = new ModelConfig();
        if (other == null) {
            return;
        }
        this.model.setProviderKey(other.getProviderKey());
        this.model.setDefault(other.getDefault());
    }

    private void copyFallbackProviders(List<FallbackProviderConfig> other) {
        this.fallbackProviders = new ArrayList<FallbackProviderConfig>();
        if (other == null) {
            return;
        }
        for (FallbackProviderConfig source : other) {
            if (source == null) {
                continue;
            }
            FallbackProviderConfig copy = new FallbackProviderConfig();
            copy.setProvider(source.getProvider());
            copy.setModel(source.getModel());
            this.fallbackProviders.add(copy);
        }
    }

    private void copyScheduler(SchedulerConfig other) {
        this.scheduler.setEnabled(other.isEnabled());
        this.scheduler.setTickSeconds(other.getTickSeconds());
    }

    private void copyCompression(CompressionConfig other) {
        this.compression.setEnabled(other.isEnabled());
        this.compression.setThresholdPercent(other.getThresholdPercent());
        this.compression.setSummaryModel(other.getSummaryModel());
        this.compression.setProtectHeadMessages(other.getProtectHeadMessages());
        this.compression.setTailRatio(other.getTailRatio());
    }

    private void copyLearning(LearningConfig other) {
        this.learning.setEnabled(other.isEnabled());
        this.learning.setToolCallThreshold(other.getToolCallThreshold());
    }

    private void copyCurator(CuratorConfig other) {
        this.curator.setEnabled(other.isEnabled());
        this.curator.setIntervalHours(other.getIntervalHours());
        this.curator.setMinIdleHours(other.getMinIdleHours());
        this.curator.setStaleAfterDays(other.getStaleAfterDays());
        this.curator.setArchiveAfterDays(other.getArchiveAfterDays());
    }

    private void copyRollback(RollbackConfig other) {
        this.rollback.setEnabled(other.isEnabled());
        this.rollback.setMaxCheckpointsPerSource(other.getMaxCheckpointsPerSource());
    }

    private void copyDisplay(DisplayConfig other) {
        this.display.setToolProgress(other.getToolProgress());
        this.display.setShowReasoning(other.isShowReasoning());
        this.display.setToolPreviewLength(other.getToolPreviewLength());
        this.display.setProgressThrottleMs(other.getProgressThrottleMs());
        this.display.getRuntimeFooter().setEnabled(other.getRuntimeFooter().isEnabled());
        this.display
                .getRuntimeFooter()
                .setFields(new ArrayList<String>(other.getRuntimeFooter().getFields()));
    }

    private void copyReact(ReActConfig other) {
        this.react.setMaxSteps(other.getMaxSteps());
        this.react.setRetryMax(other.getRetryMax());
        this.react.setRetryDelayMs(other.getRetryDelayMs());
        this.react.setDelegateMaxSteps(other.getDelegateMaxSteps());
        this.react.setDelegateRetryMax(other.getDelegateRetryMax());
        this.react.setDelegateRetryDelayMs(other.getDelegateRetryDelayMs());
        this.react.setSummarizationEnabled(other.isSummarizationEnabled());
        this.react.setSummarizationMaxMessages(other.getSummarizationMaxMessages());
        this.react.setSummarizationMaxTokens(other.getSummarizationMaxTokens());
    }

    private void copyTrace(TraceConfig other) {
        this.trace.setRetentionDays(other.getRetentionDays());
        this.trace.setMaxAttempts(other.getMaxAttempts());
        this.trace.setToolPreviewLength(other.getToolPreviewLength());
    }

    private void copyTask(TaskConfig other) {
        this.task.setBusyPolicy(other.getBusyPolicy());
        this.task.setStaleAfterMinutes(other.getStaleAfterMinutes());
        this.task.setSubagentMaxConcurrency(other.getSubagentMaxConcurrency());
        this.task.setSubagentMaxDepth(other.getSubagentMaxDepth());
        this.task.setToolOutputInlineLimit(other.getToolOutputInlineLimit());
        this.task.setMediaCacheTtlHours(other.getMediaCacheTtlHours());
    }

    private void copyMcp(McpConfig other) {
        this.mcp.setEnabled(other.isEnabled());
    }

    private void copyChannel(ChannelConfig target, ChannelConfig source) {
        target.setEnabled(source.isEnabled());
        target.setAppId(source.getAppId());
        target.setAppSecret(source.getAppSecret());
        target.setClientId(source.getClientId());
        target.setClientSecret(source.getClientSecret());
        target.setBotId(source.getBotId());
        target.setSecret(source.getSecret());
        target.setToken(source.getToken());
        target.setAccountId(source.getAccountId());
        target.setRobotCode(source.getRobotCode());
        target.setCoolAppCode(source.getCoolAppCode());
        target.setWebsocketUrl(source.getWebsocketUrl());
        target.setStreamUrl(source.getStreamUrl());
        target.setLongPollUrl(source.getLongPollUrl());
        target.setBaseUrl(source.getBaseUrl());
        target.setCdnBaseUrl(source.getCdnBaseUrl());
        target.setAllowedUsers(new ArrayList<String>(source.getAllowedUsers()));
        target.setDmPolicy(source.getDmPolicy());
        target.setGroupPolicy(source.getGroupPolicy());
        target.setGroupAllowedUsers(new ArrayList<String>(source.getGroupAllowedUsers()));
        target.setGroupMemberAllowedUsers(cloneGroupAllowMap(source.getGroupMemberAllowedUsers()));
        target.setBotOpenId(source.getBotOpenId());
        target.setBotUserId(source.getBotUserId());
        target.setBotName(source.getBotName());
        target.setAllowAllUsers(source.isAllowAllUsers());
        target.setUnauthorizedDmBehavior(source.getUnauthorizedDmBehavior());
        target.setSplitMultilineMessages(source.isSplitMultilineMessages());
        target.setSendChunkDelaySeconds(source.getSendChunkDelaySeconds());
        target.setSendChunkRetries(source.getSendChunkRetries());
        target.setSendChunkRetryDelaySeconds(source.getSendChunkRetryDelaySeconds());
        target.setToolProgress(source.getToolProgress());
        target.setProgressCardTemplateId(source.getProgressCardTemplateId());
        target.setRuntimeFooterEnabled(source.getRuntimeFooterEnabled());
        target.setCommentEnabled(source.isCommentEnabled());
        target.setCommentPairingFile(source.getCommentPairingFile());
        target.setAiCardStreamingEnabled(source.isAiCardStreamingEnabled());
        target.setApiDomain(source.getApiDomain());
        target.setMarkdownSupport(source.isMarkdownSupport());
    }

    private Map<String, PersonalityConfig> clonePersonalities(
            Map<String, PersonalityConfig> source) {
        Map<String, PersonalityConfig> result = new LinkedHashMap<String, PersonalityConfig>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, PersonalityConfig> entry : source.entrySet()) {
            PersonalityConfig config = new PersonalityConfig();
            if (entry.getValue() != null) {
                config.setDescription(entry.getValue().getDescription());
                config.setSystemPrompt(entry.getValue().getSystemPrompt());
                config.setTone(entry.getValue().getTone());
                config.setStyle(entry.getValue().getStyle());
            }
            result.put(entry.getKey(), config);
        }
        return result;
    }

    private Map<String, List<String>> cloneGroupAllowMap(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            result.put(
                    entry.getKey(),
                    entry.getValue() == null
                            ? new ArrayList<String>()
                            : new ArrayList<String>(entry.getValue()));
        }
        return result;
    }

    /**
     * 批量装配渠道共性配置。
     *
     * @param channelConfig 目标渠道配置
     * @param props 原始配置
     * @param channelName 渠道名称
     * @param defaultDmPolicy 默认私聊策略
     * @param defaultGroupPolicy 默认群聊策略
     */
    private static void applyChannelConfig(
            ChannelConfig channelConfig,
            Props props,
            Map<String, Object> overrides,
            String channelName,
            String defaultDmPolicy,
            String defaultGroupPolicy) {
        channelConfig.setAllowedUsers(
                resolveList(
                        readRaw(
                                props,
                                overrides,
                                "solonclaw.channels." + channelName + ".allowedUsers",
                                "")));
        channelConfig.setAllowAllUsers(
                resolveBoolean(
                        readBoolean(
                                props,
                                overrides,
                                "solonclaw.channels." + channelName + ".allowAllUsers",
                                false)));
        channelConfig.setUnauthorizedDmBehavior(
                resolveBehavior(
                        readString(
                                props,
                                overrides,
                                "solonclaw.channels." + channelName + ".unauthorizedDmBehavior",
                                GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR)));
        channelConfig.setDmPolicy(
                resolvePolicy(
                        readString(
                                props,
                                overrides,
                                "solonclaw.channels." + channelName + ".dmPolicy",
                                defaultDmPolicy),
                        defaultDmPolicy));
        channelConfig.setGroupPolicy(
                resolvePolicy(
                        readString(
                                props,
                                overrides,
                                "solonclaw.channels." + channelName + ".groupPolicy",
                                defaultGroupPolicy),
                        defaultGroupPolicy));
        channelConfig.setGroupAllowedUsers(
                resolveList(
                        readRaw(
                                props,
                                overrides,
                                "solonclaw.channels." + channelName + ".groupAllowedUsers",
                                "")));
    }

    /** 优先从配置文件解析密钥。 */
    private static String resolveSecret(String fallback) {
        return StrUtil.nullToEmpty(fallback).trim();
    }

    /** 优先从配置文件解析普通字符串配置。 */
    private static String resolveConfigString(String fallback) {
        return StrUtil.nullToEmpty(fallback).trim();
    }

    /** 支持通过配置文件覆盖布尔配置。 */
    private static boolean resolveBoolean(boolean fallback) {
        return fallback;
    }

    /** 支持三态布尔配置，未配置时保留 null 表示走全局默认。 */
    private static Boolean resolveOptionalBoolean(Object fallback) {
        if (fallback == null) {
            return null;
        }
        String raw = String.valueOf(fallback).trim();
        if (raw.length() == 0) {
            return null;
        }
        return Boolean.valueOf(parseBooleanText(raw, false));
    }

    private static boolean parseBooleanText(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String normalized = raw.trim();
        if ("true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)
                || "0".equals(normalized)
                || "no".equalsIgnoreCase(normalized)
                || "off".equalsIgnoreCase(normalized)) {
            return false;
        }
        return fallback;
    }

    /** 支持通过配置文件覆盖整型配置。 */
    private static int resolveInt(int fallback) {
        return fallback;
    }

    /** 支持通过配置文件覆盖浮点配置。 */
    private static double resolveDouble(double fallback) {
        return fallback;
    }

    /** 支持逗号分隔的用户列表解析。 */
    private static List<String> resolveList(Object fallback) {
        return splitObjectList(fallback);
    }

    /** 统一收敛未授权私聊用户的处理行为。 */
    private static String resolveBehavior(String fallback) {
        String value =
                StrUtil.nullToDefault(
                                fallback, GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR)
                        .trim();
        if (GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE.equalsIgnoreCase(value)) {
            return GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE;
        }
        return GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR;
    }

    /** 统一解析访问策略值。 */
    private static String resolvePolicy(String fallback, String defaultValue) {
        String value = StrUtil.nullToDefault(fallback, defaultValue).trim();
        return value.length() == 0 ? defaultValue : value.toLowerCase();
    }

    /** 将逗号分隔列表转为字符串集合。 */
    private static List<String> splitList(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        for (String item : Arrays.asList(raw.split(","))) {
            String trimmed = item == null ? "" : item.trim();
            if (trimmed.length() > 0) {
                values.add(trimmed);
            }
        }
        return values;
    }

    /** 支持从 YAML 列表或字符串中解析动态 allowlist。 */
    private static List<String> splitObjectList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof List) {
            List<String> values = new ArrayList<String>();
            for (Object item : (List<?>) raw) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            }
            return values;
        }
        return splitList(String.valueOf(raw));
    }

    /** 收集形如 channels.wecom.groups.<groupId>.allowFrom 的动态配置。 */
    private static Map<String, List<String>> collectGroupAllowMap(
            Props props, Map<String, Object> overrides, String prefix, String configKey) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (props != null) {
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!key.startsWith(prefix) || !key.endsWith(".allowFrom")) {
                    continue;
                }
                String groupId =
                        key.substring(prefix.length(), key.length() - ".allowFrom".length()).trim();
                if (groupId.length() == 0) {
                    continue;
                }
                result.put(groupId, splitObjectList(entry.getValue()));
            }
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix) || !key.endsWith(".allowFrom")) {
                continue;
            }
            String groupId =
                    key.substring(prefix.length(), key.length() - ".allowFrom".length()).trim();
            if (groupId.length() == 0) {
                continue;
            }
            result.put(groupId, splitObjectList(entry.getValue()));
        }
        Object configValue = readRaw(props, overrides, configKey, null);
        if (configValue instanceof Map) {
            result.putAll(parseGroupAllowMapJson(ONode.serialize(configValue)));
        } else if (configValue != null && StrUtil.isNotBlank(String.valueOf(configValue))) {
            result.putAll(parseGroupAllowMapJson(String.valueOf(configValue)));
        }
        return result;
    }

    /** 支持通过 JSON 配置文件注入 group -> allowlist 映射。 */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> parseGroupAllowMapJson(String json) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        Object parsed = ONode.deserialize(json, Object.class);
        if (!(parsed instanceof Map)) {
            return result;
        }
        Map<String, Object> raw = (Map<String, Object>) parsed;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.length() == 0) {
                continue;
            }
            result.put(key, splitObjectList(entry.getValue()));
        }
        return result;
    }

    /** 解析 personalities 配置映射。 */
    private static Map<String, PersonalityConfig> loadPersonalities(
            Props props, Map<String, Object> overrides) {
        Map<String, PersonalityConfig> result = new LinkedHashMap<String, PersonalityConfig>();
        if (props == null) {
            return result;
        }

        String prefix = "solonclaw.agent.personalities.";
        Map<String, String> rawEntries = new LinkedHashMap<String, String>();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String rawKey = String.valueOf(entry.getKey());
            if (!rawKey.startsWith(prefix)) {
                continue;
            }
            rawEntries.put(rawKey, props.get(rawKey, ""));
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                rawEntries.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        for (Map.Entry<String, String> entry : rawEntries.entrySet()) {
            String rawKey = entry.getKey();

            String suffix = rawKey.substring(prefix.length());
            int index = suffix.indexOf('.');
            if (index <= 0 || index >= suffix.length() - 1) {
                continue;
            }

            String name = suffix.substring(0, index).trim();
            String field = suffix.substring(index + 1).trim();
            if (StrUtil.isBlank(name) || StrUtil.isBlank(field)) {
                continue;
            }

            PersonalityConfig personality = result.get(name);
            if (personality == null) {
                personality = new PersonalityConfig();
                result.put(name, personality);
            }

            String value = entry.getValue();
            if ("description".equals(field)) {
                personality.setDescription(value);
            } else if ("systemPrompt".equals(field)) {
                personality.setSystemPrompt(value);
            } else if ("tone".equals(field)) {
                personality.setTone(value);
            } else if ("style".equals(field)) {
                personality.setStyle(value);
            }
        }
        return result;
    }

    /** 将相对路径转换为绝对路径。 */
    private File asAbsolute(File file, File base) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(base, file.getPath());
    }

    private static String readString(
            Props props, Map<String, Object> overrides, String key, String defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            return String.valueOf(override).trim();
        }
        return props.get(key, defaultValue);
    }

    private static Object readRaw(
            Props props, Map<String, Object> overrides, String key, Object defaultValue) {
        if (overrides.containsKey(key)) {
            return overrides.get(key);
        }
        Object value = props.get(key);
        return value == null ? defaultValue : value;
    }

    private static int readInt(
            Props props, Map<String, Object> overrides, String key, int defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            try {
                return Integer.parseInt(String.valueOf(override).trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return props.getInt(key, defaultValue);
    }

    private static double readDouble(
            Props props, Map<String, Object> overrides, String key, double defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            try {
                return Double.parseDouble(String.valueOf(override).trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return props.getDouble(key, defaultValue);
    }

    private static boolean readBoolean(
            Props props, Map<String, Object> overrides, String key, boolean defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            String text = String.valueOf(override).trim();
            return "true".equalsIgnoreCase(text)
                    || "1".equals(text)
                    || "yes".equalsIgnoreCase(text);
        }
        return props.getBool(key, defaultValue);
    }

    private static void applyProviderConfiguration(
            AppConfig config,
            Props props,
            Map<String, Object> overrides,
            Map<String, Object> structuredOverrides) {
        Map<String, ProviderConfig> providers =
                parseProviders(structuredOverrides.get("providers"), props);
        ModelConfig modelConfig = parseModelConfig(structuredOverrides.get("model"), props);
        List<FallbackProviderConfig> fallbackChain =
                parseFallbackProviders(structuredOverrides.get("fallbackProviders"));

        validateProviderConfiguration(providers, modelConfig, fallbackChain);

        config.setProviders(providers);
        config.setModel(modelConfig);
        config.setFallbackProviders(fallbackChain);

        ProviderConfig activeProvider = providers.get(modelConfig.getProviderKey());
        String effectiveModel =
                StrUtil.isNotBlank(modelConfig.getDefault())
                        ? modelConfig.getDefault().trim()
                        : StrUtil.nullToEmpty(activeProvider.getDefaultModel()).trim();

        config.getLlm().setProvider(modelConfig.getProviderKey());
        config.getLlm()
                .setDialect(LlmProviderSupport.normalizeDialect(activeProvider.getDialect()));
        config.getLlm()
                .setApiUrl(
                        LlmProviderSupport.buildApiUrl(
                                activeProvider.getBaseUrl(), activeProvider.getDialect()));
        config.getLlm().setApiKey(StrUtil.nullToEmpty(activeProvider.getApiKey()).trim());
        config.getLlm().setModel(effectiveModel);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ProviderConfig> parseProviders(Object rawProviders, Props props) {
        Map<String, ProviderConfig> providers = new LinkedHashMap<String, ProviderConfig>();
        providers.put(RuntimePathConstants.DEFAULT_PROVIDER_KEY, loadDefaultProvider(props));
        if (!(rawProviders instanceof Map)) {
            return providers;
        }

        Map<Object, Object> rawMap = (Map<Object, Object>) rawProviders;
        for (Map.Entry<Object, Object> entry : rawMap.entrySet()) {
            String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }

            Map<Object, Object> rawProvider = (Map<Object, Object>) entry.getValue();
            ProviderConfig provider =
                    RuntimePathConstants.DEFAULT_PROVIDER_KEY.equals(key)
                            ? cloneProvider(
                                    providers.get(RuntimePathConstants.DEFAULT_PROVIDER_KEY))
                            : new ProviderConfig();
            applyProviderString(rawProvider, "name", provider, "name");
            applyProviderString(rawProvider, "baseUrl", provider, "baseUrl");
            applyProviderString(rawProvider, "apiKey", provider, "apiKey");
            applyProviderString(rawProvider, "defaultModel", provider, "defaultModel");
            applyProviderString(rawProvider, "dialect", provider, "dialect");
            providers.put(key, provider);
        }

        return providers;
    }

    private static ProviderConfig cloneProvider(ProviderConfig source) {
        ProviderConfig copy = new ProviderConfig();
        if (source != null) {
            copy.setName(source.getName());
            copy.setBaseUrl(source.getBaseUrl());
            copy.setApiKey(source.getApiKey());
            copy.setDefaultModel(source.getDefaultModel());
            copy.setDialect(source.getDialect());
        }
        return copy;
    }

    private static void applyProviderString(
            Map<Object, Object> rawProvider, String key, ProviderConfig provider, String field) {
        if (!rawProvider.containsKey(key)) {
            return;
        }
        String value = readNestedString(rawProvider, key);
        if ("name".equals(field)) {
            provider.setName(value);
        } else if ("baseUrl".equals(field)) {
            provider.setBaseUrl(value);
        } else if ("apiKey".equals(field)) {
            provider.setApiKey(value);
        } else if ("defaultModel".equals(field)) {
            provider.setDefaultModel(value);
        } else if ("dialect".equals(field)) {
            provider.setDialect(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static ModelConfig parseModelConfig(Object rawModel, Props props) {
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setProviderKey(
                StrUtil.blankToDefault(
                        props.get("model.providerKey"), RuntimePathConstants.DEFAULT_PROVIDER_KEY));
        modelConfig.setDefault(StrUtil.nullToEmpty(props.get("model.default")).trim());
        if (!(rawModel instanceof Map)) {
            return modelConfig;
        }

        Map<Object, Object> rawMap = (Map<Object, Object>) rawModel;
        String providerKey = readNestedString(rawMap, "providerKey");
        String defaultModel = readNestedString(rawMap, "default");
        if (StrUtil.isNotBlank(providerKey)) {
            modelConfig.setProviderKey(providerKey);
        }
        if (StrUtil.isNotBlank(defaultModel)) {
            modelConfig.setDefault(defaultModel);
        }
        return modelConfig;
    }

    @SuppressWarnings("unchecked")
    private static List<FallbackProviderConfig> parseFallbackProviders(
            Object rawFallbackProviders) {
        List<FallbackProviderConfig> result = new ArrayList<FallbackProviderConfig>();
        if (!(rawFallbackProviders instanceof List)) {
            return result;
        }

        List<Object> rawList = (List<Object>) rawFallbackProviders;
        for (Object raw : rawList) {
            if (!(raw instanceof Map)) {
                continue;
            }

            Map<Object, Object> rawMap = (Map<Object, Object>) raw;
            FallbackProviderConfig config = new FallbackProviderConfig();
            config.setProvider(readNestedString(rawMap, "provider"));
            config.setModel(readNestedString(rawMap, "model"));
            result.add(config);
        }

        return result;
    }

    private static ProviderConfig loadDefaultProvider(Props props) {
        String dialect =
                StrUtil.blankToDefault(
                        props.get("providers.default.dialect"),
                        RuntimePathConstants.DEFAULT_LLM_PROVIDER);
        String baseUrl =
                StrUtil.blankToDefault(
                        props.get("providers.default.baseUrl"),
                        RuntimePathConstants.DEFAULT_LLM_API_URL);
        String defaultModel =
                StrUtil.blankToDefault(
                        props.get("providers.default.defaultModel"),
                        RuntimePathConstants.DEFAULT_LLM_MODEL);
        ProviderConfig provider = new ProviderConfig();
        provider.setName(
                StrUtil.blankToDefault(props.get("providers.default.name"), "DefaultProvider"));
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(StrUtil.nullToEmpty(props.get("providers.default.apiKey")).trim());
        provider.setDefaultModel(defaultModel);
        provider.setDialect(LlmProviderSupport.normalizeDialect(dialect));
        return provider;
    }

    private static void validateProviderConfiguration(
            Map<String, ProviderConfig> providers,
            ModelConfig modelConfig,
            List<FallbackProviderConfig> fallbackChain) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalStateException("至少需要配置一个 provider。");
        }
        if (modelConfig == null || StrUtil.isBlank(modelConfig.getProviderKey())) {
            throw new IllegalStateException("model.providerKey 不能为空。");
        }
        if (!providers.containsKey(modelConfig.getProviderKey())) {
            throw new IllegalStateException(
                    "model.providerKey 未命中 providers：" + modelConfig.getProviderKey());
        }

        String globalDefaultModel = StrUtil.nullToEmpty(modelConfig.getDefault()).trim();
        for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
            String providerKey = StrUtil.nullToEmpty(entry.getKey()).trim();
            ProviderConfig provider = entry.getValue();
            if (provider == null) {
                throw new IllegalStateException("provider 配置不能为空：" + providerKey);
            }
            if (StrUtil.isBlank(providerKey)) {
                throw new IllegalStateException("provider key 不能为空。");
            }
            if (StrUtil.isBlank(provider.getBaseUrl())) {
                throw new IllegalStateException("provider.baseUrl 不能为空：" + providerKey);
            }
            if (StrUtil.isBlank(provider.getDialect())) {
                throw new IllegalStateException("provider.dialect 不能为空：" + providerKey);
            }
            if (!LlmProviderSupport.isSupportedDialect(provider.getDialect())) {
                throw new IllegalStateException("不支持的 provider dialect：" + provider.getDialect());
            }
            if (StrUtil.isBlank(provider.getDefaultModel())
                    && StrUtil.isBlank(globalDefaultModel)) {
                throw new IllegalStateException("provider.defaultModel 不能为空：" + providerKey);
            }
        }

        if (fallbackChain == null) {
            return;
        }
        for (FallbackProviderConfig fallback : fallbackChain) {
            if (fallback == null || StrUtil.isBlank(fallback.getProvider())) {
                throw new IllegalStateException("fallbackProviders.provider 不能为空。");
            }
            ProviderConfig provider = providers.get(fallback.getProvider().trim());
            if (provider == null) {
                throw new IllegalStateException(
                        "fallbackProviders 引用了不存在的 provider：" + fallback.getProvider());
            }
            if (StrUtil.isBlank(fallback.getModel())
                    && StrUtil.isBlank(provider.getDefaultModel())
                    && StrUtil.isBlank(globalDefaultModel)) {
                throw new IllegalStateException(
                        "fallback provider 缺少可用模型：" + fallback.getProvider());
            }
        }
    }

    private static String readNestedString(Map<Object, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Map<String, Object> loadStructuredOverrides(File runtimeHome) {
        File configFile = new File(runtimeHome, "config.yml");
        if (!configFile.exists()) {
            return Collections.emptyMap();
        }

        try {
            Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
            if (!(parsed instanceof Map)) {
                return Collections.emptyMap();
            }
            return sanitizeStructuredMap((Map<?, ?>) parsed);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static Map<String, Object> sanitizeStructuredMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.put(key, sanitizeStructuredMap((Map<?, ?>) value));
            } else if (value instanceof List) {
                result.put(key, sanitizeStructuredList((List<?>) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private static List<Object> sanitizeStructuredList(List<?> raw) {
        List<Object> result = new ArrayList<Object>();
        for (Object item : raw) {
            if (item instanceof Map) {
                result.add(sanitizeStructuredMap((Map<?, ?>) item));
            } else if (item instanceof List) {
                result.add(sanitizeStructuredList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static Map<String, Object> loadFlatOverrides(File runtimeHome) {
        File configFile = new File(runtimeHome, "config.yml");
        if (!configFile.exists()) {
            return Collections.emptyMap();
        }

        try {
            Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
            if (!(parsed instanceof Map)) {
                return Collections.emptyMap();
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            flatten("", (Map<?, ?>) parsed, result);
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static void flatten(String prefix, Map<?, ?> input, Map<String, Object> output) {
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key =
                    prefix.length() == 0
                            ? String.valueOf(entry.getKey())
                            : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<?, ?>) value, output);
            } else {
                output.put(key, value);
            }
        }
    }

    private static File asAbsoluteStatic(File file, File base) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(base, file.getPath());
    }

    private static String resolveInitialRuntimeHome(Props props) {
        return props.get("solonclaw.runtime.home", RuntimePathConstants.RUNTIME_HOME);
    }

    private static void initializeRuntimeConfigIfMissing(File runtimeHome) {
        File configFile = new File(runtimeHome, RuntimePathConstants.CONFIG_FILE_NAME);
        if (configFile.exists()) {
            return;
        }

        try {
            FileUtil.mkParentDirs(configFile);
            FileUtil.writeUtf8String(defaultRuntimeConfigContent(), configFile);
        } catch (Exception ignored) {
            // 运行配置初始化失败时继续启动；后续权限检查和 Dashboard 保存路径仍可提示用户修复。
        }
    }

    private static String defaultRuntimeConfigContent() {
        return "# SolonClaw 最小运行配置。\n"
                + "# 启动时自动创建；可通过 Dashboard 或直接编辑本文件继续完善。\n"
                + "providers:\n"
                + "  default:\n"
                + "    name: DefaultProvider\n"
                + "    baseUrl: https://api.openai.com\n"
                + "    apiKey: \"\"\n"
                + "    defaultModel: gpt-5.4\n"
                + "    dialect: openai\n"
                + "\n"
                + "model:\n"
                + "  providerKey: default\n"
                + "  default: \"gpt-5.4\"\n"
                + "\n"
                + "fallbackProviders: []\n"
                + "\n"
                + "solonclaw:\n"
                + "  dashboard:\n"
                + "    accessToken: \"admin\"\n";
    }

    private static void syncRuntimeConfigExample(String runtimeHome) {
        try (InputStream stream =
                AppConfig.class
                        .getClassLoader()
                        .getResourceAsStream(RuntimePathConstants.CONFIG_EXAMPLE_FILE_NAME)) {
            if (stream == null) {
                return;
            }
            File target =
                    new File(
                            StrUtil.blankToDefault(runtimeHome, RuntimePathConstants.RUNTIME_HOME),
                            RuntimePathConstants.CONFIG_EXAMPLE_FILE_NAME);
            FileUtil.mkParentDirs(target);
            Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // 示例配置只用于 Agent 参考，启动不应因同步失败而中断。
        }
    }

    private static String runtimeChildPath(String runtimeHome, String childName) {
        return new File(
                        StrUtil.blankToDefault(runtimeHome, RuntimePathConstants.RUNTIME_HOME),
                        childName)
                .getPath();
    }

    private static String runtimeChildPath(String runtimeHome, String childName, String fileName) {
        return new File(runtimeChildPath(runtimeHome, childName), fileName).getPath();
    }

    /** 运行时目录配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RuntimeConfig {
        /** 运行时根目录。 */
        private String home;

        /** 上下文文件目录。 */
        private String contextDir;

        /** skills 本地目录。 */
        private String skillsDir;

        /** 缓存目录。 */
        private String cacheDir;

        /** SQLite 状态库路径。 */
        private String stateDb;

        /** runtime/config.yml 路径。 */
        private String configFile;

        /** runtime/logs 目录。 */
        private String logsDir;
    }

    /** 大模型接入配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LlmConfig {
        /** 运行时 provider key。 */
        private String provider;

        /** 协议方言。 */
        private String dialect;

        /** 请求地址。 */
        private String apiUrl;

        /** 访问密钥。 */
        private String apiKey;

        /** 默认模型名。 */
        private String model;

        /** 是否使用流式输出。 */
        private boolean stream;

        /** 推理强度。 */
        private String reasoningEffort;

        /** 温度参数。 */
        private double temperature;

        /** 最大输出 token。 */
        private int maxTokens;

        /** 模型上下文窗口大小，用于自动压缩阈值计算。 */
        private int contextWindowTokens;
    }

    /** 命名 provider 配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProviderConfig {
        private String name;
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private String dialect;
    }

    /** 当前主模型选择配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ModelConfig {
        private String providerKey;
        private String defaultModel;

        public String getDefault() {
            return defaultModel;
        }

        public void setDefault(String defaultModel) {
            this.defaultModel = defaultModel;
        }
    }

    /** 主模型故障切换项。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FallbackProviderConfig {
        private String provider;
        private String model;
    }

    /** 调度器配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SchedulerConfig {
        /** 是否启用调度器。 */
        private boolean enabled;

        /** 调度轮询周期，单位秒。 */
        private int tickSeconds;
    }

    /** 上下文压缩配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CompressionConfig {
        /** 是否启用自动压缩。 */
        private boolean enabled = true;

        /** 触发压缩的上下文阈值百分比。 */
        private double thresholdPercent = CompressionConstants.DEFAULT_THRESHOLD_PERCENT;

        /** 可选的压缩摘要模型。 */
        private String summaryModel;

        /** 头部消息保护数量。 */
        private int protectHeadMessages = CompressionConstants.DEFAULT_PROTECT_HEAD_MESSAGES;

        /** 尾部消息保护比例。 */
        private double tailRatio = CompressionConstants.DEFAULT_TAIL_RATIO;
    }

    /** 任务后学习闭环配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LearningConfig {
        /** 是否启用自动学习。 */
        private boolean enabled = true;

        /** 触发自动学习的最少工具调用数。 */
        private int toolCallThreshold = 5;
    }

    /** 技能后台维护配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CuratorConfig {
        /** 是否启用技能后台维护。 */
        private boolean enabled = true;

        /** 后台巡检周期，单位小时。 */
        private int intervalHours = 168;

        /** 最小空闲窗口，单位小时。 */
        private double minIdleHours = 2.0D;

        /** 多久未使用后标记为 stale。 */
        private int staleAfterDays = 30;

        /** 多久未使用后归档。 */
        private int archiveAfterDays = 90;
    }

    /** 文件快照与回滚配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RollbackConfig {
        /** 是否启用文件快照与回滚。 */
        private boolean enabled = true;

        /** 单来源键保留的最大 checkpoint 数。 */
        private int maxCheckpointsPerSource =
                CheckpointConstants.DEFAULT_MAX_CHECKPOINTS_PER_SOURCE;
    }

    /** 最终回复运行态 footer 配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RuntimeFooterConfig {
        /** 默认关闭，避免污染现有渠道回复。 */
        private boolean enabled = false;

        /** footer 字段顺序。 */
        private List<String> fields =
                new ArrayList<String>(Arrays.asList("model", "context_pct", "cwd"));
    }

    /** 聊天窗口显示配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DisplayConfig {
        /** 默认工具进度模式。 */
        private String toolProgress = "all";

        /** 是否默认展示 reasoning。 */
        private boolean showReasoning;

        /** 工具参数预览长度。 */
        private int toolPreviewLength = 80;

        /** reasoning/进度消息节流毫秒数。 */
        private int progressThrottleMs = 1500;

        /** 最终回复运行态 footer。 */
        private RuntimeFooterConfig runtimeFooter = new RuntimeFooterConfig();
    }

    /** Agent 行为配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class AgentConfig {
        /** 预定义人格列表。 */
        private Map<String, PersonalityConfig> personalities =
                new LinkedHashMap<String, PersonalityConfig>();

        /** HEARTBEAT.md 相关调度配置。 */
        private HeartbeatConfig heartbeat = new HeartbeatConfig();
    }

    /** heartbeat 调度配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class HeartbeatConfig {
        /** 固定轮询间隔（分钟）；0 表示关闭 heartbeat。 */
        private int intervalMinutes = RuntimePathConstants.DEFAULT_HEARTBEAT_INTERVAL_MINUTES;
    }

    /** ReAct 推理控制配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReActConfig {
        /** 主代理最大推理步数。 */
        private int maxSteps = 12;

        /** 主代理决策重试次数。 */
        private int retryMax = 3;

        /** 主代理决策重试基础延迟（毫秒）。 */
        private int retryDelayMs = 2000;

        /** 子代理最大推理步数。 */
        private int delegateMaxSteps = 18;

        /** 子代理决策重试次数。 */
        private int delegateRetryMax = 4;

        /** 子代理决策重试基础延迟（毫秒）。 */
        private int delegateRetryDelayMs = 2500;

        /** 是否启用 ReAct 工作记忆摘要守卫。 */
        private boolean summarizationEnabled = true;

        /** ReAct 摘要守卫触发的消息阈值。 */
        private int summarizationMaxMessages = 40;

        /** ReAct 摘要守卫触发的 token 阈值。 */
        private int summarizationMaxTokens = 32000;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TraceConfig {
        /** 运行轨迹保留天数。 */
        private int retentionDays = 14;

        /** 每个 run 最大外层 attempt 数。 */
        private int maxAttempts = 2;

        /** 工具结果预览最大长度。 */
        private int toolPreviewLength = 1200;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TaskConfig {
        /** 同一会话 busy 时的默认策略：queue / interrupt / steer / reject。 */
        private String busyPolicy = "queue";

        /** stale run 判定窗口，单位分钟。 */
        private int staleAfterMinutes = 60;

        /** 子 Agent 最大并发。 */
        private int subagentMaxConcurrency = 3;

        /** 子 Agent 最大 spawn 深度。 */
        private int subagentMaxDepth = 1;

        /** 工具输出超过该长度时应落盘/摘要化。 */
        private int toolOutputInlineLimit = 4000;

        /** 媒体缓存 TTL，单位小时。 */
        private int mediaCacheTtlHours = 168;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class McpConfig {
        /** MCP 工具适配默认关闭。 */
        private boolean enabled = false;
    }

    /** 单个人格定义。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PersonalityConfig {
        /** 描述文案。 */
        private String description;

        /** 系统提示词主体。 */
        private String systemPrompt;

        /** 额外语气提示。 */
        private String tone;

        /** 额外风格提示。 */
        private String style;

        /** 合并为最终注入文本。 */
        public String toPrompt() {
            StringBuilder buffer = new StringBuilder();
            if (StrUtil.isNotBlank(systemPrompt)) {
                buffer.append(systemPrompt.trim());
            }
            if (StrUtil.isNotBlank(tone)) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append("Tone: ").append(tone.trim());
            }
            if (StrUtil.isNotBlank(style)) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append("Style: ").append(style.trim());
            }
            return buffer.toString();
        }
    }

    /** 全部渠道配置集合。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChannelsConfig {
        /** 飞书渠道配置。 */
        private ChannelConfig feishu = new ChannelConfig();

        /** 钉钉渠道配置。 */
        private ChannelConfig dingtalk = new ChannelConfig();

        /** 企微渠道配置。 */
        private ChannelConfig wecom = new ChannelConfig();

        /** 微信渠道配置。 */
        private ChannelConfig weixin = new ChannelConfig();

        /** QQ Bot 渠道配置。 */
        private ChannelConfig qqbot = new ChannelConfig();

        /** 腾讯元宝渠道配置。 */
        private ChannelConfig yuanbao = new ChannelConfig();
    }

    /** 单个渠道配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChannelConfig {
        /** 是否启用该渠道。 */
        private boolean enabled;

        /** 飞书应用 ID。 */
        private String appId;

        /** 飞书应用密钥。 */
        private String appSecret;

        /** 钉钉客户端 ID。 */
        private String clientId;

        /** 钉钉客户端密钥。 */
        private String clientSecret;

        /** 企微机器人标识。 */
        private String botId;

        /** 企微密钥。 */
        private String secret;

        /** 微信令牌。 */
        private String token;

        /** 微信 accountId。 */
        private String accountId;

        /** 钉钉机器人编码。 */
        private String robotCode;

        /** 钉钉 Cool App 编码。 */
        private String coolAppCode;

        /** WebSocket 地址。 */
        private String websocketUrl;

        /** Stream 地址。 */
        private String streamUrl;

        /** Long Poll 地址。 */
        private String longPollUrl;

        /** 渠道基础地址。 */
        private String baseUrl;

        /** 渠道 CDN 基础地址。 */
        private String cdnBaseUrl;

        /** 渠道允许名单。 */
        private List<String> allowedUsers = new ArrayList<String>();

        /** 私聊访问策略。 */
        private String dmPolicy = GatewayBehaviorConstants.DM_POLICY_OPEN;

        /** 群聊访问策略。 */
        private String groupPolicy = GatewayBehaviorConstants.GROUP_POLICY_OPEN;

        /** 群聊允许名单。 */
        private List<String> groupAllowedUsers = new ArrayList<String>();

        /** 企微按群发送者 allowlist。 */
        private Map<String, List<String>> groupMemberAllowedUsers =
                new LinkedHashMap<String, List<String>>();

        /** 飞书 bot open id。 */
        private String botOpenId;

        /** 飞书 bot user id。 */
        private String botUserId;

        /** 飞书 bot 展示名。 */
        private String botName;

        /** 是否允许该渠道所有用户访问。 */
        private boolean allowAllUsers;

        /** 未授权私聊行为。 */
        private String unauthorizedDmBehavior =
                GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR;

        /** 微信是否按多行强制拆分。 */
        private boolean splitMultilineMessages;

        /** 微信分片发送间隔。 */
        private double sendChunkDelaySeconds = 0.35D;

        /** 微信分片重试次数。 */
        private int sendChunkRetries = 2;

        /** 微信分片重试间隔。 */
        private double sendChunkRetryDelaySeconds = 1.0D;

        /** 渠道默认工具进度模式。 */
        private String toolProgress;

        /** 钉钉长任务进度卡模板 ID。 */
        private String progressCardTemplateId;

        /** 渠道级 runtime footer 开关，null 表示继承全局。 */
        private Boolean runtimeFooterEnabled;

        /** 飞书文档评论智能回复开关。 */
        private boolean commentEnabled;

        /** 飞书评论与会话绑定文件。 */
        private String commentPairingFile;

        /** 钉钉 AI Card 是否使用增量流式更新。 */
        private boolean aiCardStreamingEnabled = true;

        /** 渠道 REST API 域名。 */
        private String apiDomain;

        /** 渠道是否支持 Markdown 文本。 */
        private boolean markdownSupport = true;
    }

    /** 网关通用授权配置。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class GatewayConfig {
        /** 全局允许名单。 */
        private List<String> allowedUsers = new ArrayList<String>();

        /** 是否允许所有用户访问。 */
        private boolean allowAllUsers;

        /** HTTP gateway injection HMAC secret. */
        private String injectionSecret;

        /** Maximum accepted gateway injection body size. */
        private int injectionMaxBodyBytes = 65536;

        /** Replay window in seconds for signed gateway injection requests. */
        private int injectionReplayWindowSeconds = 300;
    }

    /** Dashboard and API access configuration. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DashboardConfig {
        /** Shared bearer token for dashboard pages and API requests. */
        private String accessToken;
    }
}
