package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.solon.core.util.Assert;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Dashboard 配置读写与 schema 服务。 */
public class DashboardConfigService {
    private static final List<String> PASSTHROUGH_PREFIXES =
            Arrays.asList("channels.wecom.groups.");
    private static final Object WRITE_LOCK = new Object();

    private final AppConfig appConfig;
    private final com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
            gatewayRuntimeRefreshService;
    private final Map<String, FieldDefinition> fields =
            new LinkedHashMap<String, FieldDefinition>();
    private final List<String> categoryOrder =
            Arrays.asList("general", "agent", "compression", "security", "messaging");

    public DashboardConfigService(
            AppConfig appConfig,
            com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
                    gatewayRuntimeRefreshService) {
        this.appConfig = appConfig;
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        registerFields();
    }

    public Map<String, Object> getConfig() {
        return toNestedFieldMap(resolveCurrentValues());
    }

    public Map<String, Object> getDefaults() {
        return toNestedFieldMap(resolveDefaultValues());
    }

    public Map<String, Object> getSchema() {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        Map<String, Object> fieldMaps = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, FieldDefinition> entry : fields.entrySet()) {
            fieldMaps.put(entry.getKey(), entry.getValue().toSchemaMap());
        }
        response.put("fields", fieldMaps);
        response.put("category_order", categoryOrder);
        return response;
    }

    public Map<String, Object> getRaw() {
        return Collections.<String, Object>singletonMap("yaml", dumpYaml(resolveCurrentValues()));
    }

    public Map<String, Object> saveConfig(Map<String, Object> nestedConfig) {
        Map<String, Object> flat = flattenFieldMap(nestedConfig);
        validateKeys(flat.keySet());
        writeOverrideFile(flat);
        gatewayRuntimeRefreshService.refreshNow();
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> saveRaw(String yamlText) {
        Map<String, Object> flat = loadFieldMap(yamlText);
        validateKeys(flat.keySet());
        writeOverrideFile(flat);
        gatewayRuntimeRefreshService.refreshNow();
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> savePartialFlat(Map<String, Object> flatUpdates) {
        return savePartialFlat(flatUpdates, true);
    }

    public Map<String, Object> savePartialFlat(
            Map<String, Object> flatUpdates, boolean reconnectChannels) {
        validateKeys(flatUpdates.keySet());
        Map<String, Object> merged = mergeBaseValues();
        merged.putAll(flatUpdates);
        writeOverrideFile(merged);
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    private void registerFields() {
        addField(
                new FieldDefinition("llm.provider", "select", "general", "模型协议提供方")
                        .options("openai", "openai-responses", "ollama", "gemini", "anthropic"));
        addField(new FieldDefinition("llm.apiUrl", "string", "general", "所选提供方的 API 地址"));
        addField(new FieldDefinition("llm.model", "string", "general", "默认模型名"));
        addField(new FieldDefinition("llm.stream", "boolean", "general", "是否启用流式输出"));
        addField(
                new FieldDefinition("llm.reasoningEffort", "select", "general", "默认推理强度")
                        .options("minimal", "low", "medium", "high"));
        addField(new FieldDefinition("llm.temperature", "number", "general", "采样温度"));
        addField(new FieldDefinition("llm.maxTokens", "number", "general", "最大输出 token"));
        addField(
                new FieldDefinition("llm.contextWindowTokens", "number", "general", "上下文窗口 token"));
        addField(
                new FieldDefinition("display.toolProgress", "select", "general", "默认工具进度模式")
                        .options("off", "new", "all", "verbose"));
        addField(
                new FieldDefinition(
                        "display.showReasoning", "boolean", "general", "默认允许 reasoning 进入聊天窗口"));
        addField(new FieldDefinition("display.toolPreviewLength", "number", "general", "工具参数预览长度"));
        addField(
                new FieldDefinition(
                        "display.progressThrottleMs", "number", "general", "reasoning/进度消息节流毫秒"));
        addField(
                new FieldDefinition(
                        "display.runtimeFooter.enabled",
                        "boolean",
                        "general",
                        "最终回复 runtime footer 开关"));
        addField(
                new FieldDefinition(
                        "display.runtimeFooter.fields", "list", "general", "runtime footer 字段列表"));
        addField(
                new FieldDefinition(
                        "display.platforms.feishu.runtimeFooter.enabled",
                        "boolean",
                        "messaging",
                        "飞书 runtime footer 覆盖开关"));
        addField(
                new FieldDefinition(
                        "display.platforms.dingtalk.runtimeFooter.enabled",
                        "boolean",
                        "messaging",
                        "钉钉 runtime footer 覆盖开关"));
        addField(
                new FieldDefinition(
                        "display.platforms.wecom.runtimeFooter.enabled",
                        "boolean",
                        "messaging",
                        "企微 runtime footer 覆盖开关"));
        addField(
                new FieldDefinition(
                        "display.platforms.weixin.runtimeFooter.enabled",
                        "boolean",
                        "messaging",
                        "微信 runtime footer 覆盖开关"));
        addField(
                new FieldDefinition(
                        "display.platforms.qqbot.runtimeFooter.enabled",
                        "boolean",
                        "messaging",
                        "QQBot runtime footer 覆盖开关"));
        addField(
                new FieldDefinition(
                        "display.platforms.yuanbao.runtimeFooter.enabled",
                        "boolean",
                        "messaging",
                        "元宝 runtime footer 覆盖开关"));
        addField(new FieldDefinition("scheduler.enabled", "boolean", "general", "启用定时调度"));
        addField(new FieldDefinition("scheduler.tickSeconds", "number", "general", "调度轮询周期（秒）"));

        addField(new FieldDefinition("learning.enabled", "boolean", "agent", "启用主回复后的自动学习"));
        addField(
                new FieldDefinition(
                        "learning.toolCallThreshold", "number", "agent", "触发学习所需的最少工具调用数"));
        addField(
                new FieldDefinition(
                        "skills.curator.enabled", "boolean", "agent", "启用技能后台维护 Curator"));
        addField(
                new FieldDefinition(
                        "skills.curator.intervalHours", "number", "agent", "Curator 巡检周期（小时）"));
        addField(
                new FieldDefinition(
                        "skills.curator.minIdleHours", "number", "agent", "Curator 最小空闲窗口（小时）"));
        addField(
                new FieldDefinition(
                        "skills.curator.staleAfterDays", "number", "agent", "技能多久未使用后标记 stale"));
        addField(
                new FieldDefinition(
                        "skills.curator.archiveAfterDays", "number", "agent", "技能多久未使用后归档"));
        addField(
                new FieldDefinition(
                        "agent.heartbeat.intervalMinutes",
                        "number",
                        "agent",
                        "heartbeat 轮询间隔（分钟，0 表示关闭）"));
        addField(new FieldDefinition("rollback.enabled", "boolean", "agent", "启用 checkpoint 回滚"));
        addField(
                new FieldDefinition(
                        "rollback.maxCheckpointsPerSource",
                        "number",
                        "agent",
                        "每个来源保留的最大 checkpoint 数"));
        addField(new FieldDefinition("react.maxSteps", "number", "agent", "主代理最大推理步数"));
        addField(new FieldDefinition("react.retryMax", "number", "agent", "主代理决策重试次数"));
        addField(new FieldDefinition("react.retryDelayMs", "number", "agent", "主代理决策重试基础延迟（毫秒）"));
        addField(new FieldDefinition("react.delegateMaxSteps", "number", "agent", "子代理最大推理步数"));
        addField(new FieldDefinition("react.delegateRetryMax", "number", "agent", "子代理决策重试次数"));
        addField(
                new FieldDefinition(
                        "react.delegateRetryDelayMs", "number", "agent", "子代理决策重试基础延迟（毫秒）"));
        addField(
                new FieldDefinition(
                        "react.summarizationEnabled",
                        "boolean",
                        "compression",
                        "启用 ReAct 工作记忆摘要守卫"));
        addField(
                new FieldDefinition(
                        "react.summarizationMaxMessages",
                        "number",
                        "compression",
                        "ReAct 摘要触发消息阈值"));
        addField(
                new FieldDefinition(
                        "react.summarizationMaxTokens",
                        "number",
                        "compression",
                        "ReAct 摘要触发 token 阈值"));
        addField(
                new FieldDefinition(
                        "agent.personalities.helpful.description",
                        "string",
                        "agent",
                        "helpful 人格描述"));
        addField(
                new FieldDefinition(
                        "agent.personalities.helpful.systemPrompt",
                        "text",
                        "agent",
                        "helpful 人格系统提示词"));
        addField(
                new FieldDefinition(
                        "agent.personalities.concise.description",
                        "string",
                        "agent",
                        "concise 人格描述"));
        addField(
                new FieldDefinition(
                        "agent.personalities.concise.systemPrompt",
                        "text",
                        "agent",
                        "concise 人格系统提示词"));
        addField(
                new FieldDefinition(
                        "agent.personalities.technical.description",
                        "string",
                        "agent",
                        "technical 人格描述"));
        addField(
                new FieldDefinition(
                        "agent.personalities.technical.systemPrompt",
                        "text",
                        "agent",
                        "technical 人格系统提示词"));
        addField(
                new FieldDefinition(
                        "agent.personalities.technical.tone", "string", "agent", "technical 人格语气"));
        addField(
                new FieldDefinition(
                        "agent.personalities.technical.style",
                        "string",
                        "agent",
                        "technical 人格风格"));

        addField(new FieldDefinition("compression.enabled", "boolean", "compression", "启用上下文压缩"));
        addField(
                new FieldDefinition(
                        "compression.thresholdPercent", "number", "compression", "触发压缩的阈值比例"));
        addField(
                new FieldDefinition(
                        "compression.summaryModel", "string", "compression", "可选压缩/工作记忆摘要模型"));
        addField(
                new FieldDefinition(
                        "compression.protectHeadMessages", "number", "compression", "头部保护消息数"));
        addField(new FieldDefinition("compression.tailRatio", "number", "compression", "尾部保护比例"));

        addField(new FieldDefinition("gateway.allowedUsers", "list", "security", "全局允许用户列表"));
        addField(new FieldDefinition("gateway.allowAllUsers", "boolean", "security", "是否全局允许所有用户"));
        addField(
                new FieldDefinition(
                        "gateway.injectionSecret", "password", "security", "HTTP 网关注入签名密钥"));
        addField(
                new FieldDefinition(
                        "gateway.injectionMaxBodyBytes",
                        "number",
                        "security",
                        "HTTP 网关注入最大请求体字节数"));
        addField(
                new FieldDefinition(
                        "gateway.injectionReplayWindowSeconds",
                        "number",
                        "security",
                        "HTTP 网关注入重放窗口秒数"));

        addChannelFields("feishu");
        addField(
                new FieldDefinition(
                        "channels.feishu.websocketUrl", "string", "messaging", "飞书 websocket 地址"));
        addField(
                new FieldDefinition("channels.feishu.dmPolicy", "select", "messaging", "飞书私聊策略")
                        .options("open", "allowlist", "disabled", "pairing"));
        addField(
                new FieldDefinition("channels.feishu.groupPolicy", "select", "messaging", "飞书群聊策略")
                        .options("open", "allowlist", "disabled"));
        addField(
                new FieldDefinition(
                        "channels.feishu.groupAllowedUsers",
                        "list",
                        "messaging",
                        "飞书群聊 allowlist"));
        addField(
                new FieldDefinition(
                        "channels.feishu.botOpenId", "string", "messaging", "飞书 bot Open ID"));
        addField(
                new FieldDefinition(
                        "channels.feishu.botUserId", "string", "messaging", "飞书 bot User ID"));
        addField(
                new FieldDefinition(
                        "channels.feishu.botName", "string", "messaging", "飞书 bot 展示名"));
        addField(
                new FieldDefinition(
                                "channels.feishu.toolProgress", "select", "messaging", "飞书工具进度模式")
                        .options("off", "new", "all", "verbose"));
        addField(
                new FieldDefinition(
                        "channels.feishu.comment.enabled", "boolean", "messaging", "飞书文档评论智能回复开关"));
        addField(
                new FieldDefinition(
                        "channels.feishu.comment.pairingFile", "string", "messaging", "飞书评论配对文件"));

        addChannelFields("dingtalk");
        addField(
                new FieldDefinition(
                        "channels.dingtalk.coolAppCode",
                        "string",
                        "messaging",
                        "可选钉钉 Cool App 编码"));
        addField(
                new FieldDefinition(
                        "channels.dingtalk.streamUrl", "string", "messaging", "钉钉 stream 地址"));
        addField(
                new FieldDefinition(
                                "channels.dingtalk.toolProgress", "select", "messaging", "钉钉工具进度模式")
                        .options("off", "new", "all", "verbose"));
        addField(
                new FieldDefinition(
                        "channels.dingtalk.progressCardTemplateId",
                        "string",
                        "messaging",
                        "钉钉长任务进度卡模板 ID"));
        addField(
                new FieldDefinition("channels.dingtalk.dmPolicy", "select", "messaging", "钉钉私聊策略")
                        .options("open", "allowlist", "disabled", "pairing"));
        addField(
                new FieldDefinition(
                                "channels.dingtalk.groupPolicy", "select", "messaging", "钉钉群聊策略")
                        .options("open", "allowlist", "disabled"));
        addField(
                new FieldDefinition(
                        "channels.dingtalk.groupAllowedUsers",
                        "list",
                        "messaging",
                        "钉钉群聊 allowlist"));
        addField(
                new FieldDefinition(
                        "channels.dingtalk.aiCardStreaming.enabled",
                        "boolean",
                        "messaging",
                        "钉钉 AI Card 增量更新开关"));

        addChannelFields("wecom");
        addField(
                new FieldDefinition(
                        "channels.wecom.websocketUrl", "string", "messaging", "企微 websocket 地址"));
        addField(
                new FieldDefinition(
                                "channels.wecom.toolProgress", "select", "messaging", "企微工具进度模式")
                        .options("off", "new", "all", "verbose"));
        addField(
                new FieldDefinition("channels.wecom.dmPolicy", "select", "messaging", "企微私聊策略")
                        .options("open", "allowlist", "disabled", "pairing"));
        addField(
                new FieldDefinition("channels.wecom.groupPolicy", "select", "messaging", "企微群聊策略")
                        .options("open", "allowlist", "disabled"));
        addField(
                new FieldDefinition(
                        "channels.wecom.groupAllowedUsers", "list", "messaging", "企微群聊 allowlist"));

        addChannelFields("weixin");
        addField(
                new FieldDefinition(
                        "channels.weixin.accountId", "string", "messaging", "微信 iLink accountId"));
        addField(
                new FieldDefinition(
                        "channels.weixin.baseUrl", "string", "messaging", "微信 iLink API 地址"));
        addField(
                new FieldDefinition(
                        "channels.weixin.cdnBaseUrl", "string", "messaging", "微信 CDN 地址"));
        addField(
                new FieldDefinition(
                        "channels.weixin.longPollUrl", "string", "messaging", "微信 long-poll 地址"));
        addField(
                new FieldDefinition("channels.weixin.dmPolicy", "select", "messaging", "微信私聊策略")
                        .options("open", "allowlist", "disabled", "pairing"));
        addField(
                new FieldDefinition("channels.weixin.groupPolicy", "select", "messaging", "微信群聊策略")
                        .options("open", "allowlist", "disabled"));
        addField(
                new FieldDefinition(
                        "channels.weixin.groupAllowedUsers",
                        "list",
                        "messaging",
                        "微信群聊 allowlist"));
        addField(
                new FieldDefinition(
                        "channels.weixin.splitMultilineMessages",
                        "boolean",
                        "messaging",
                        "微信多行消息拆分"));
        addField(
                new FieldDefinition(
                                "channels.weixin.toolProgress", "select", "messaging", "微信工具进度模式")
                        .options("off", "new", "all", "verbose"));
        addField(
                new FieldDefinition(
                        "channels.weixin.sendChunkDelaySeconds",
                        "number",
                        "messaging",
                        "微信分片发送间隔（秒）"));
        addField(
                new FieldDefinition(
                        "channels.weixin.sendChunkRetries", "number", "messaging", "微信分片重试次数"));
        addField(
                new FieldDefinition(
                        "channels.weixin.sendChunkRetryDelaySeconds",
                        "number",
                        "messaging",
                        "微信分片重试间隔（秒）"));

        addChannelFields("qqbot");
        addField(
                new FieldDefinition(
                        "channels.qqbot.apiDomain", "string", "messaging", "QQBot API 域名"));
        addField(
                new FieldDefinition(
                        "channels.qqbot.websocketUrl",
                        "string",
                        "messaging",
                        "QQBot websocket 地址"));
        addField(
                new FieldDefinition(
                        "channels.qqbot.markdownSupport",
                        "boolean",
                        "messaging",
                        "QQBot Markdown 消息开关"));
        addField(
                new FieldDefinition(
                                "channels.qqbot.toolProgress",
                                "select",
                                "messaging",
                                "QQBot 工具进度模式")
                        .options("off", "new", "all", "verbose"));
        addField(
                new FieldDefinition("channels.qqbot.dmPolicy", "select", "messaging", "QQBot 私聊策略")
                        .options("open", "allowlist", "disabled", "pairing"));
        addField(
                new FieldDefinition(
                                "channels.qqbot.groupPolicy", "select", "messaging", "QQBot 群聊策略")
                        .options("open", "allowlist", "disabled"));
        addField(
                new FieldDefinition(
                        "channels.qqbot.groupAllowedUsers",
                        "list",
                        "messaging",
                        "QQBot 群聊 allowlist"));

        addChannelFields("yuanbao");
        addField(
                new FieldDefinition(
                        "channels.yuanbao.apiDomain", "string", "messaging", "腾讯元宝 API 域名"));
        addField(
                new FieldDefinition(
                        "channels.yuanbao.websocketUrl",
                        "string",
                        "messaging",
                        "腾讯元宝 websocket 地址"));
        addField(
                new FieldDefinition(
                                "channels.yuanbao.toolProgress",
                                "select",
                                "messaging",
                                "腾讯元宝工具进度模式")
                        .options("off", "new", "all", "verbose"));
        addField(
                new FieldDefinition("channels.yuanbao.dmPolicy", "select", "messaging", "腾讯元宝私聊策略")
                        .options("open", "allowlist", "disabled", "pairing"));
        addField(
                new FieldDefinition(
                                "channels.yuanbao.groupPolicy", "select", "messaging", "腾讯元宝群聊策略")
                        .options("open", "allowlist", "disabled"));
        addField(
                new FieldDefinition(
                        "channels.yuanbao.groupAllowedUsers",
                        "list",
                        "messaging",
                        "腾讯元宝群聊 allowlist"));
    }

    private void addChannelFields(String name) {
        FieldDefinition enabledField =
                new FieldDefinition(
                        "channels." + name + ".enabled",
                        "boolean",
                        "messaging",
                        channelLabel(name) + "渠道开关");
        addField(enabledField);
        addField(
                new FieldDefinition(
                        "channels." + name + ".allowedUsers",
                        "list",
                        "messaging",
                        channelLabel(name) + "允许用户列表"));
        addField(
                new FieldDefinition(
                        "channels." + name + ".allowAllUsers",
                        "boolean",
                        "messaging",
                        channelLabel(name) + "是否允许所有用户"));
        addField(
                new FieldDefinition(
                                "channels." + name + ".unauthorizedDmBehavior",
                                "select",
                                "messaging",
                                channelLabel(name) + "未授权私聊行为")
                        .options("pair", "ignore"));
    }

    private String channelLabel(String name) {
        if ("feishu".equals(name)) {
            return "飞书";
        }
        if ("dingtalk".equals(name)) {
            return "钉钉";
        }
        if ("wecom".equals(name)) {
            return "企微";
        }
        if ("weixin".equals(name)) {
            return "微信";
        }
        if ("qqbot".equals(name)) {
            return "QQBot";
        }
        if ("yuanbao".equals(name)) {
            return "腾讯元宝";
        }
        return name;
    }

    private void addField(FieldDefinition definition) {
        fields.put(definition.key, definition);
    }

    private Map<String, Object> resolveCurrentValues() {
        Map<String, Object> defaults = resolveDefaultValues();
        Map<String, Object> overrides = loadOverrideFields();
        Map<String, Object> current = new LinkedHashMap<String, Object>();
        for (FieldDefinition field : fields.values()) {
            Object value =
                    overrides.containsKey(field.key)
                            ? overrides.get(field.key)
                            : defaults.get(field.key);
            current.put(field.key, value);
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            if (isSupportedPassthroughKey(entry.getKey())) {
                current.put(entry.getKey(), entry.getValue());
            }
        }
        return current;
    }

    private Map<String, Object> resolveDefaultValues() {
        Map<String, Object> raw = loadFieldMap(loadClasspathAppYaml());
        Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        for (FieldDefinition field : fields.values()) {
            defaults.put(field.key, raw.get(field.key));
        }
        return defaults;
    }

    private String loadClasspathAppYaml() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("app.yml");
        if (stream == null) {
            return "";
        }
        return IoUtil.read(stream, StandardCharsets.UTF_8);
    }

    private Map<String, Object> loadOverrideFields() {
        File configFile = new File(appConfig.getRuntime().getConfigFile());
        if (!configFile.exists()) {
            return Collections.emptyMap();
        }
        return loadFieldMap(FileUtil.readUtf8String(configFile));
    }

    private Map<String, Object> loadFieldMap(String yamlText) {
        if (StrUtil.isBlank(yamlText)) {
            return Collections.emptyMap();
        }

        Object parsed = new Yaml().load(yamlText);
        if (!(parsed instanceof Map)) {
            return Collections.emptyMap();
        }

        Map<String, Object> flattened = new LinkedHashMap<String, Object>();
        flatten("", (Map<?, ?>) parsed, flattened);

        Map<String, Object> fieldValues = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : flattened.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("solonclaw.")) {
                key = key.substring("solonclaw.".length());
            }
            if (fields.containsKey(key) || isSupportedPassthroughKey(key)) {
                fieldValues.put(key, entry.getValue());
            }
        }
        return fieldValues;
    }

    private void flatten(String prefix, Map<?, ?> input, Map<String, Object> output) {
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

    private Map<String, Object> flattenFieldMap(Map<String, Object> nested) {
        Assert.notNull(nested, "config body is required");
        Map<String, Object> output = new LinkedHashMap<String, Object>();
        flattenNested("", nested, output);

        Map<String, Object> filtered = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : output.entrySet()) {
            if (fields.containsKey(entry.getKey()) || isSupportedPassthroughKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private void flattenNested(
            String prefix, Map<String, Object> input, Map<String, Object> output) {
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = prefix.length() == 0 ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenNested(key, (Map<String, Object>) value, output);
            } else {
                output.put(key, value);
            }
        }
    }

    private Map<String, Object> toNestedFieldMap(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            setNestedValue(root, entry.getKey(), entry.getValue());
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> root, String key, Object value) {
        String[] parts = key.split("\\.");
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object current = cursor.get(parts[i]);
            if (!(current instanceof Map)) {
                current = new LinkedHashMap<String, Object>();
                cursor.put(parts[i], current);
            }
            cursor = (Map<String, Object>) current;
        }
        cursor.put(parts[parts.length - 1], value);
    }

    private void validateKeys(Iterable<String> keys) {
        for (String key : keys) {
            if (key.startsWith("runtime.") || key.startsWith("solonclaw.runtime.")) {
                throw new IllegalStateException(
                        "solonclaw.runtime.* is not editable from the dashboard");
            }
            if (!fields.containsKey(key) && !isSupportedPassthroughKey(key)) {
                throw new IllegalStateException("Unsupported config key: " + key);
            }
        }
    }

    private Map<String, Object> mergeBaseValues() {
        return new LinkedHashMap<String, Object>(loadOverrideFields());
    }

    private boolean isSupportedPassthroughKey(String key) {
        for (String prefix : PASSTHROUGH_PREFIXES) {
            if (key != null && key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeOverrideFile(Map<String, Object> fieldValues) {
        synchronized (WRITE_LOCK) {
            Map<String, Object> root = loadRawConfigRoot();
            Map<String, Object> solonclaw = ensureSolonClawRoot(root);
            clearManagedFields(solonclaw);
            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                setNestedValue(solonclaw, entry.getKey(), entry.getValue());
            }

            File configFile = new File(appConfig.getRuntime().getConfigFile());
            FileUtil.mkParentDirs(configFile);
            File temp = new File(configFile.getParentFile(), configFile.getName() + ".tmp");
            FileUtil.writeUtf8String(dump(root), temp);
            try {
                try {
                    Files.move(
                            temp.toPath(),
                            configFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception atomicFailed) {
                    Files.move(
                            temp.toPath(),
                            configFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to write config file", e);
            }
        }
    }

    private String dumpYaml(Map<String, Object> fieldValues) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        Map<String, Object> solonclaw = new LinkedHashMap<String, Object>();
        root.put("solonclaw", solonclaw);
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            setNestedValue(solonclaw, entry.getKey(), entry.getValue());
        }
        return dump(root);
    }

    private String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        return new Yaml(options).dump(root);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRawConfigRoot() {
        File configFile = new File(appConfig.getRuntime().getConfigFile());
        if (!configFile.exists()) {
            return new LinkedHashMap<String, Object>();
        }
        Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
        if (!(parsed instanceof Map)) {
            return new LinkedHashMap<String, Object>();
        }
        return sanitizeMap((Map<?, ?>) parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureSolonClawRoot(Map<String, Object> root) {
        Object current = root.get("solonclaw");
        if (current instanceof Map) {
            return (Map<String, Object>) current;
        }
        Map<String, Object> solonclaw = new LinkedHashMap<String, Object>();
        root.put("solonclaw", solonclaw);
        return solonclaw;
    }

    private void clearManagedFields(Map<String, Object> jimuqu) {
        for (String key : fields.keySet()) {
            removeNestedValue(jimuqu, key);
        }
        for (String prefix : PASSTHROUGH_PREFIXES) {
            removeNestedPrefix(jimuqu, prefix);
        }
    }

    private Object parseTypedValue(String type, String raw) {
        if ("boolean".equals(type)) {
            return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
        }
        if ("number".equals(type)) {
            try {
                return raw.contains(".") ? Double.valueOf(raw) : Integer.valueOf(raw);
            } catch (Exception e) {
                return 0;
            }
        }
        if ("list".equals(type)) {
            List<String> values = new ArrayList<String>();
            for (String item : raw.split(",")) {
                if (StrUtil.isNotBlank(item)) {
                    values.add(item.trim());
                }
            }
            return values;
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = sanitizeMap((Map<?, ?>) value);
            } else if (value instanceof List) {
                value = sanitizeList((List<?>) value);
            }
            result.put(String.valueOf(entry.getKey()), value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> sanitizeList(List<?> input) {
        List<Object> result = new ArrayList<Object>();
        for (Object item : input) {
            if (item instanceof Map) {
                result.add(sanitizeMap((Map<?, ?>) item));
            } else if (item instanceof List) {
                result.add(sanitizeList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean removeNestedValue(Map<String, Object> root, String key) {
        String[] parts = key.split("\\.");
        List<Map<String, Object>> parents = new ArrayList<Map<String, Object>>();
        List<String> keys = new ArrayList<String>();
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object current = cursor.get(parts[i]);
            if (!(current instanceof Map)) {
                return false;
            }
            parents.add(cursor);
            keys.add(parts[i]);
            cursor = (Map<String, Object>) current;
        }
        Object removed = cursor.remove(parts[parts.length - 1]);
        if (removed == null) {
            return false;
        }
        for (int i = parents.size() - 1; i >= 0; i--) {
            Object current = parents.get(i).get(keys.get(i));
            if (current instanceof Map && ((Map<?, ?>) current).isEmpty()) {
                parents.get(i).remove(keys.get(i));
            } else {
                break;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void removeNestedPrefix(Map<String, Object> root, String prefix) {
        String normalized = prefix;
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.length() == 0) {
            return;
        }
        String[] parts = normalized.split("\\.");
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object current = cursor.get(parts[i]);
            if (!(current instanceof Map)) {
                return;
            }
            cursor = (Map<String, Object>) current;
        }
        cursor.remove(parts[parts.length - 1]);
    }

    private static class FieldDefinition {
        private final String key;
        private final String type;
        private final String category;
        private final String description;
        private List<String> options = Collections.emptyList();

        private FieldDefinition(String key, String type, String category, String description) {
            this.key = key;
            this.type = type;
            this.category = category;
            this.description = description;
        }

        private FieldDefinition options(String... values) {
            this.options = Arrays.asList(values);
            return this;
        }

        private Map<String, Object> toSchemaMap() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("type", type);
            result.put("category", category);
            result.put("description", description);
            if (!options.isEmpty()) {
                result.put("options", options);
            }
            return result;
        }
    }
}
