package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ChannelStatus;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.support.constants.AgentSettingConstants;
import com.jimuqu.solon.claw.support.constants.ToolNameConstants;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import com.jimuqu.solon.claw.web.DashboardConfigService;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 运行时设置读取与修改服务。 */
public class RuntimeSettingsService {
    private static final List<String> CONFIG_KEY_WHITELIST =
            Arrays.asList(
                    "model.providerKey",
                    "model.default",
                    "providers.default.name",
                    "providers.default.baseUrl",
                    "providers.default.apiKey",
                    "providers.default.defaultModel",
                    "providers.default.dialect",
                    "llm.stream",
                    "llm.reasoningEffort",
                    "llm.temperature",
                    "llm.maxTokens",
                    "llm.contextWindowTokens",
                    "display.toolProgress",
                    "display.showReasoning",
                    "display.toolPreviewLength",
                    "display.progressThrottleMs",
                    "display.runtimeFooter.enabled",
                    "display.runtimeFooter.fields",
                    "display.platforms.feishu.runtimeFooter.enabled",
                    "display.platforms.dingtalk.runtimeFooter.enabled",
                    "display.platforms.wecom.runtimeFooter.enabled",
                    "display.platforms.weixin.runtimeFooter.enabled",
                    "display.platforms.qqbot.runtimeFooter.enabled",
                    "display.platforms.yuanbao.runtimeFooter.enabled",
                    "scheduler.enabled",
                    "scheduler.tickSeconds",
                    "compression.enabled",
                    "compression.thresholdPercent",
                    "compression.summaryModel",
                    "compression.protectHeadMessages",
                    "compression.tailRatio",
                    "learning.enabled",
                    "learning.toolCallThreshold",
                    "skills.curator.enabled",
                    "skills.curator.intervalHours",
                    "skills.curator.minIdleHours",
                    "skills.curator.staleAfterDays",
                    "skills.curator.archiveAfterDays",
                    "agent.heartbeat.intervalMinutes",
                    "rollback.enabled",
                    "rollback.maxCheckpointsPerSource",
                    "react.maxSteps",
                    "react.retryMax",
                    "react.retryDelayMs",
                    "react.delegateMaxSteps",
                    "react.delegateRetryMax",
                    "react.delegateRetryDelayMs",
                    "react.summarizationEnabled",
                    "react.summarizationMaxMessages",
                    "react.summarizationMaxTokens",
                    "gateway.allowedUsers",
                    "gateway.allowAllUsers",
                    "gateway.injectionSecret",
                    "gateway.injectionMaxBodyBytes",
                    "gateway.injectionReplayWindowSeconds");

    private static final List<String> CHANNEL_KEY_SUFFIX_WHITELIST =
            Arrays.asList(
                    ".enabled",
                    ".allowedUsers",
                    ".allowAllUsers",
                    ".unauthorizedDmBehavior",
                    ".dmPolicy",
                    ".groupPolicy",
                    ".groupAllowedUsers",
                    ".websocketUrl",
                    ".streamUrl",
                    ".coolAppCode",
                    ".baseUrl",
                    ".cdnBaseUrl",
                    ".longPollUrl",
                    ".splitMultilineMessages",
                    ".sendChunkDelaySeconds",
                    ".sendChunkRetries",
                    ".sendChunkRetryDelaySeconds",
                    ".toolProgress",
                    ".progressCardTemplateId",
                    ".runtimeFooter.enabled",
                    ".comment.enabled",
                    ".comment.pairingFile",
                    ".aiCardStreaming.enabled",
                    ".apiDomain",
                    ".markdownSupport",
                    ".appId",
                    ".appSecret",
                    ".clientId",
                    ".clientSecret",
                    ".robotCode",
                    ".botId",
                    ".secret",
                    ".token",
                    ".accountId",
                    ".botOpenId",
                    ".botUserId",
                    ".botName");

    private final AppConfig appConfig;
    private final GlobalSettingRepository globalSettingRepository;
    private final DeliveryService deliveryService;
    private final DashboardConfigService dashboardConfigService;
    private final DashboardRuntimeConfigService dashboardRuntimeConfigService;
    private final AppVersionService appVersionService;
    private final LlmProviderService llmProviderService;
    private final com.jimuqu.solon.claw.web.DashboardProviderService dashboardProviderService;

    public RuntimeSettingsService(
            AppConfig appConfig,
            GlobalSettingRepository globalSettingRepository,
            DeliveryService deliveryService,
            DashboardConfigService dashboardConfigService,
            DashboardRuntimeConfigService dashboardRuntimeConfigService,
            AppVersionService appVersionService,
            LlmProviderService llmProviderService,
            com.jimuqu.solon.claw.web.DashboardProviderService dashboardProviderService) {
        this.appConfig = appConfig;
        this.globalSettingRepository = globalSettingRepository;
        this.deliveryService = deliveryService;
        this.dashboardConfigService = dashboardConfigService;
        this.dashboardRuntimeConfigService = dashboardRuntimeConfigService;
        this.appVersionService = appVersionService;
        this.llmProviderService = llmProviderService;
        this.dashboardProviderService = dashboardProviderService;
    }

    public ResolvedModel resolveEffectiveModel(SessionRecord session) {
        return resolveEffectiveModel(session, null);
    }

    public ResolvedModel resolveEffectiveModel(
            SessionRecord session, AgentRuntimeScope agentScope) {
        String override =
                session == null ? "" : StrUtil.nullToEmpty(session.getModelOverride()).trim();
        LlmProviderService.ResolvedProvider resolved =
                llmProviderService.resolveEffectiveProvider(
                        session, agentScope == null ? null : agentScope.getDefaultModel());
        return new ResolvedModel(
                resolved.getProviderKey(),
                resolved.getDialect(),
                resolved.getModel(),
                override.length() > 0);
    }

    public String buildAgentRuntimePrompt(
            String sourceKey, SessionRecord session, List<String> enabledToolNames) {
        return buildAgentRuntimePrompt(sourceKey, session, enabledToolNames, null);
    }

    public String buildAgentRuntimePrompt(
            String sourceKey,
            SessionRecord session,
            List<String> enabledToolNames,
            AgentRuntimeScope agentScope) {
        String[] parts = SourceKeySupport.split(sourceKey);
        ResolvedModel resolved = resolveEffectiveModel(session, agentScope);
        List<String> channelStates = new ArrayList<String>();
        try {
            for (ChannelStatus status : deliveryService.statuses()) {
                if (status.getPlatform() == null) {
                    continue;
                }
                channelStates.add(
                        status.getPlatform().name().toLowerCase()
                                + "(enabled="
                                + status.isEnabled()
                                + ",connected="
                                + status.isConnected()
                                + ")");
            }
        } catch (Exception ignored) {
            // best effort
        }

        String activePersonality = "default";
        try {
            String stored =
                    globalSettingRepository == null
                            ? null
                            : globalSettingRepository.get(AgentSettingConstants.ACTIVE_PERSONALITY);
            if (StrUtil.isNotBlank(stored)) {
                activePersonality = stored.trim();
            }
        } catch (Exception ignored) {
            // best effort
        }

        StringBuilder buffer = new StringBuilder();
        LlmProviderService.ResolvedProvider globalResolved =
                llmProviderService.resolveEffectiveProvider(null);
        buffer.append("[Agent Runtime]\n");
        buffer.append("agent_name=")
                .append(agentScope == null ? "default" : agentScope.getEffectiveName())
                .append('\n');
        buffer.append("agent_display_name=")
                .append(
                        agentScope == null
                                ? "默认 Agent"
                                : StrUtil.nullToEmpty(agentScope.getDisplayName()))
                .append('\n');
        buffer.append("agent_workspace=")
                .append(
                        agentScope == null
                                ? StrUtil.nullToEmpty(appConfig.getRuntime().getHome())
                                : StrUtil.nullToEmpty(agentScope.getWorkspaceDir()))
                .append('\n');
        buffer.append("source_key=").append(StrUtil.nullToEmpty(sourceKey)).append('\n');
        buffer.append("platform=").append(StrUtil.nullToEmpty(parts[0])).append('\n');
        buffer.append("chat_id=").append(StrUtil.nullToEmpty(parts[1])).append('\n');
        buffer.append("user_id=").append(StrUtil.nullToEmpty(parts[2])).append('\n');
        buffer.append("session_id=")
                .append(session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()))
                .append('\n');
        buffer.append("branch=")
                .append(session == null ? "" : StrUtil.nullToEmpty(session.getBranchName()))
                .append('\n');
        buffer.append("active_personality=").append(activePersonality).append('\n');
        buffer.append("default_provider=")
                .append(StrUtil.nullToEmpty(appConfig.getModel().getProviderKey()))
                .append('\n');
        buffer.append("default_model=")
                .append(StrUtil.nullToEmpty(globalResolved.getModel()))
                .append('\n');
        buffer.append("agent_default_model=")
                .append(agentScope == null ? "" : StrUtil.nullToEmpty(agentScope.getDefaultModel()))
                .append('\n');
        buffer.append("effective_provider=")
                .append(StrUtil.nullToEmpty(resolved.provider))
                .append('\n');
        buffer.append("effective_dialect=")
                .append(StrUtil.nullToEmpty(resolved.dialect))
                .append('\n');
        buffer.append("effective_model=").append(StrUtil.nullToEmpty(resolved.model)).append('\n');
        buffer.append("session_input_tokens=")
                .append(session == null ? 0 : session.getCumulativeInputTokens())
                .append('\n');
        buffer.append("session_output_tokens=")
                .append(session == null ? 0 : session.getCumulativeOutputTokens())
                .append('\n');
        buffer.append("session_total_tokens=")
                .append(session == null ? 0 : session.getCumulativeTotalTokens())
                .append('\n');
        buffer.append("react_summarization_enabled=")
                .append(appConfig.getReact().isSummarizationEnabled())
                .append('\n');
        buffer.append("react_summarization_max_messages=")
                .append(appConfig.getReact().getSummarizationMaxMessages())
                .append('\n');
        buffer.append("react_summarization_max_tokens=")
                .append(appConfig.getReact().getSummarizationMaxTokens())
                .append('\n');
        buffer.append("app_version=")
                .append(StrUtil.nullToEmpty(appVersionService.currentTag()))
                .append('\n');
        buffer.append("deployment_mode=")
                .append(StrUtil.nullToEmpty(appVersionService.deploymentMode()))
                .append('\n');
        buffer.append("has_session_model_override=").append(resolved.sessionOverride).append('\n');
        buffer.append("enabled_tools=").append(join(enabledToolNames)).append('\n');
        buffer.append("channels=").append(join(channelStates)).append('\n');
        buffer.append("runtime_home=")
                .append(StrUtil.nullToEmpty(appConfig.getRuntime().getHome()))
                .append('\n');
        appendShellGuidance(buffer, enabledToolNames);
        buffer.append(
                "Only change your own configuration through /model, config_set, or config_set_secret. If you edit runtime/config.yml directly, call config_refresh afterward; it validates YAML first and refuses invalid config. Global changes take effect on the next message.");
        return buffer.toString();
    }

    public String describeModel(SessionRecord session) {
        ResolvedModel resolved = resolveEffectiveModel(session);
        StringBuilder buffer = new StringBuilder();
        buffer.append("current.provider=")
                .append(StrUtil.nullToDefault(resolved.provider, "default"))
                .append('\n');
        buffer.append("current.dialect=")
                .append(StrUtil.nullToDefault(resolved.dialect, ""))
                .append('\n');
        buffer.append("current.model=")
                .append(StrUtil.nullToDefault(resolved.model, "default"))
                .append('\n');
        buffer.append("current.apiUrl=")
                .append(
                        StrUtil.nullToDefault(
                                llmProviderService.resolveEffectiveProvider(session).getApiUrl(),
                                ""))
                .append('\n');
        buffer.append("session.override=")
                .append(
                        session == null
                                ? ""
                                : StrUtil.nullToDefault(session.getModelOverride(), ""))
                .append('\n');
        buffer.append("global.provider=")
                .append(StrUtil.nullToDefault(appConfig.getModel().getProviderKey(), ""))
                .append('\n');
        buffer.append("global.model=")
                .append(
                        StrUtil.nullToDefault(
                                llmProviderService.resolveEffectiveProvider(null).getModel(), ""))
                .append('\n');
        return buffer.toString().trim();
    }

    public void setGlobalModel(String provider, String model) {
        dashboardProviderService.updateDefaultModel(provider, model);
    }

    public Object getConfigValue(String key) {
        ensureConfigKeyAllowed(key);
        return readNested(dashboardConfigService.getConfig(), key);
    }

    public void setConfigValue(String key, String rawValue) {
        ensureConfigKeyAllowed(key);
        persistConfigValue(
                key, parseValueForKey(key, rawValue), shouldReconnectChannelsForConfigKey(key));
    }

    public void setSecretValue(String key, String value) {
        dashboardRuntimeConfigService.set(key, value, shouldReconnectChannelsForRuntimeKey(key));
    }

    private void ensureConfigKeyAllowed(String key) {
        if (CONFIG_KEY_WHITELIST.contains(key)) {
            return;
        }
        if (key != null
                && (key.startsWith("channels.feishu.")
                        || key.startsWith("channels.dingtalk.")
                        || key.startsWith("channels.wecom.")
                        || key.startsWith("channels.weixin.")
                        || key.startsWith("channels.qqbot.")
                        || key.startsWith("channels.yuanbao."))) {
            for (String suffix : CHANNEL_KEY_SUFFIX_WHITELIST) {
                if (key.endsWith(suffix)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported config key: " + key);
    }

    private Object parseValueForKey(String key, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (key.endsWith(".enabled")
                || key.endsWith(".allowAllUsers")
                || key.endsWith(".splitMultilineMessages")
                || key.endsWith(".comment.enabled")
                || key.endsWith(".aiCardStreaming.enabled")
                || key.endsWith(".markdownSupport")
                || key.endsWith(".runtimeFooter.enabled")
                || "llm.stream".equals(key)
                || "display.showReasoning".equals(key)
                || "display.runtimeFooter.enabled".equals(key)
                || "scheduler.enabled".equals(key)
                || "compression.enabled".equals(key)
                || "learning.enabled".equals(key)
                || "rollback.enabled".equals(key)
                || "skills.curator.enabled".equals(key)
                || "gateway.allowAllUsers".equals(key)) {
            return "true".equalsIgnoreCase(value)
                    || "1".equals(value)
                    || "yes".equalsIgnoreCase(value);
        }
        if (key.endsWith("sendChunkRetries")
                || "scheduler.tickSeconds".equals(key)
                || "learning.toolCallThreshold".equals(key)
                || "agent.heartbeat.intervalMinutes".equals(key)
                || "rollback.maxCheckpointsPerSource".equals(key)
                || "react.maxSteps".equals(key)
                || "react.retryMax".equals(key)
                || "react.retryDelayMs".equals(key)
                || "react.delegateMaxSteps".equals(key)
                || "react.delegateRetryMax".equals(key)
                || "react.delegateRetryDelayMs".equals(key)
                || "react.summarizationMaxMessages".equals(key)
                || "react.summarizationMaxTokens".equals(key)
                || "compression.protectHeadMessages".equals(key)
                || "skills.curator.intervalHours".equals(key)
                || "skills.curator.staleAfterDays".equals(key)
                || "skills.curator.archiveAfterDays".equals(key)
                || "display.toolPreviewLength".equals(key)
                || "display.progressThrottleMs".equals(key)
                || "llm.maxTokens".equals(key)
                || "llm.contextWindowTokens".equals(key)
                || "gateway.injectionMaxBodyBytes".equals(key)
                || "gateway.injectionReplayWindowSeconds".equals(key)) {
            return Integer.valueOf(value);
        }
        if ("react.summarizationEnabled".equals(key)) {
            return "true".equalsIgnoreCase(value)
                    || "1".equals(value)
                    || "yes".equalsIgnoreCase(value);
        }
        if (key.endsWith("sendChunkDelaySeconds")
                || key.endsWith("sendChunkRetryDelaySeconds")
                || "llm.temperature".equals(key)
                || "compression.thresholdPercent".equals(key)
                || "compression.tailRatio".equals(key)
                || "skills.curator.minIdleHours".equals(key)) {
            return Double.valueOf(value);
        }
        if (key.endsWith("allowedUsers")
                || key.endsWith("groupAllowedUsers")
                || "display.runtimeFooter.fields".equals(key)
                || "gateway.allowedUsers".equals(key)) {
            List<String> values = new ArrayList<String>();
            if (value.length() == 0) {
                return values;
            }
            for (String item : value.split(",")) {
                if (StrUtil.isNotBlank(item)) {
                    values.add(item.trim());
                }
            }
            return values;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object readNested(Map<String, Object> root, String key) {
        String[] parts = key.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        for (String value : values) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(value.trim());
        }
        return buffer.toString();
    }

    private void appendShellGuidance(StringBuilder buffer, List<String> enabledToolNames) {
        if (enabledToolNames == null
                || !enabledToolNames.contains(ToolNameConstants.EXECUTE_SHELL)) {
            return;
        }

        buffer.append("shell_probe_policy=Use execute_shell for environment detection.\n");
        buffer.append(
                "shell_probe_example=Use execute_shell with commands like: command -v git >/dev/null 2>&1 && git --version || echo git_missing\n");
    }

    private void persistConfigValue(String key, Object value, boolean reconnectChannels) {
        Map<String, Object> updates = new LinkedHashMap<String, Object>();
        updates.put(key, value);
        dashboardConfigService.savePartialFlat(updates, reconnectChannels);
    }

    private boolean shouldReconnectChannelsForConfigKey(String key) {
        return key != null && (key.startsWith("channels.") || key.startsWith("gateway.injection"));
    }

    private boolean shouldReconnectChannelsForRuntimeKey(String key) {
        return key != null && key.startsWith("solonclaw.channels.");
    }

    public static class ResolvedModel {
        private final String provider;
        private final String dialect;
        private final String model;
        private final boolean sessionOverride;

        public ResolvedModel(
                String provider, String dialect, String model, boolean sessionOverride) {
            this.provider = provider;
            this.dialect = dialect;
            this.model = model;
            this.sessionOverride = sessionOverride;
        }

        public String getProvider() {
            return provider;
        }

        public String getModel() {
            return model;
        }

        public String getDialect() {
            return dialect;
        }

        public boolean isSessionOverride() {
            return sessionOverride;
        }
    }
}
