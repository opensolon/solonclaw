package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard 运行时配置管理服务。 */
public class DashboardRuntimeConfigService {
    private final RuntimeConfigResolver configResolver;
    private final List<ConfigItemDefinition> definitions;
    private final com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
            gatewayRuntimeRefreshService;

    public DashboardRuntimeConfigService(
            AppConfig appConfig,
            com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
                    gatewayRuntimeRefreshService) {
        this.configResolver = RuntimeConfigResolver.initialize(appConfig.getRuntime().getHome());
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        this.definitions =
                Arrays.asList(
                        item(
                                "model.providerKey",
                                "默认模型 provider key",
                                "provider",
                                false,
                                false,
                                "llm"),
                        item("model.default", "全局默认模型覆盖", "provider", false, false, "llm"),
                        item(
                                "providers.default.name",
                                "默认 provider 名称",
                                "provider",
                                false,
                                false,
                                "llm"),
                        item(
                                "providers.default.baseUrl",
                                "默认 provider 基础地址",
                                "provider",
                                false,
                                false,
                                "llm"),
                        item(
                                "providers.default.defaultModel",
                                "默认 provider 模型",
                                "provider",
                                false,
                                false,
                                "llm"),
                        item(
                                "providers.default.dialect",
                                "默认 provider 协议方言",
                                "provider",
                                false,
                                false,
                                "llm"),
                        item(
                                "providers.default.apiKey",
                                "默认 provider API 密钥",
                                "provider",
                                true,
                                false,
                                "llm"),
                        item(
                                "solonclaw.react.maxSteps",
                                "主代理最大推理步数",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.retryMax",
                                "主代理决策重试次数",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.retryDelayMs",
                                "主代理决策重试延迟（毫秒）",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.delegateMaxSteps",
                                "子代理最大推理步数",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.delegateRetryMax",
                                "子代理决策重试次数",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.delegateRetryDelayMs",
                                "子代理决策重试延迟（毫秒）",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.summarizationEnabled",
                                "启用 ReAct 工作记忆摘要守卫",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.summarizationMaxMessages",
                                "ReAct 摘要触发消息阈值",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.react.summarizationMaxTokens",
                                "ReAct 摘要触发 token 阈值",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.compression.summaryModel",
                                "压缩/工作记忆摘要模型",
                                "provider",
                                false,
                                true,
                                "llm"),
                        item(
                                "solonclaw.channels.feishu.enabled",
                                "启用飞书渠道",
                                "messaging",
                                false,
                                false,
                                "feishu"),
                        item(
                                "solonclaw.channels.feishu.appId",
                                "飞书应用 ID",
                                "messaging",
                                false,
                                false,
                                "feishu"),
                        item(
                                "solonclaw.channels.feishu.appSecret",
                                "飞书应用密钥",
                                "messaging",
                                true,
                                false,
                                "feishu"),
                        item(
                                "solonclaw.channels.feishu.groupAllowedUsers",
                                "飞书群聊 allowlist",
                                "messaging",
                                false,
                                true,
                                "feishu"),
                        item(
                                "solonclaw.channels.feishu.botOpenId",
                                "飞书 bot Open ID",
                                "messaging",
                                false,
                                true,
                                "feishu"),
                        item(
                                "solonclaw.channels.feishu.botUserId",
                                "飞书 bot User ID",
                                "messaging",
                                false,
                                true,
                                "feishu"),
                        item(
                                "solonclaw.channels.dingtalk.enabled",
                                "启用钉钉渠道",
                                "messaging",
                                false,
                                false,
                                "dingtalk"),
                        item(
                                "solonclaw.channels.dingtalk.clientId",
                                "钉钉客户端 ID",
                                "messaging",
                                false,
                                false,
                                "dingtalk"),
                        item(
                                "solonclaw.channels.dingtalk.clientSecret",
                                "钉钉客户端密钥",
                                "messaging",
                                true,
                                false,
                                "dingtalk"),
                        item(
                                "solonclaw.channels.dingtalk.robotCode",
                                "钉钉机器人编码",
                                "messaging",
                                true,
                                false,
                                "dingtalk"),
                        item(
                                "solonclaw.channels.dingtalk.groupAllowedUsers",
                                "钉钉群聊 allowlist",
                                "messaging",
                                false,
                                true,
                                "dingtalk"),
                        item(
                                "solonclaw.channels.wecom.enabled",
                                "启用企微渠道",
                                "messaging",
                                false,
                                false,
                                "wecom"),
                        item(
                                "solonclaw.channels.wecom.botId",
                                "企微机器人 ID",
                                "messaging",
                                false,
                                false,
                                "wecom"),
                        item(
                                "solonclaw.channels.wecom.secret",
                                "企微机器人密钥",
                                "messaging",
                                true,
                                false,
                                "wecom"),
                        item(
                                "solonclaw.channels.wecom.groupAllowedUsers",
                                "企微群聊 allowlist",
                                "messaging",
                                false,
                                true,
                                "wecom"),
                        item(
                                "solonclaw.channels.weixin.enabled",
                                "启用微信渠道",
                                "messaging",
                                false,
                                false,
                                "weixin"),
                        item(
                                "solonclaw.channels.weixin.token",
                                "微信令牌",
                                "messaging",
                                true,
                                false,
                                "weixin"),
                        item(
                                "solonclaw.channels.weixin.accountId",
                                "微信 iLink accountId",
                                "messaging",
                                false,
                                false,
                                "weixin"),
                        item(
                                "solonclaw.channels.weixin.groupAllowedUsers",
                                "微信群聊 allowlist",
                                "messaging",
                                false,
                                true,
                                "weixin"),
                        item(
                                "solonclaw.channels.qqbot.enabled",
                                "启用 QQBot 渠道",
                                "messaging",
                                false,
                                false,
                                "qqbot"),
                        item(
                                "solonclaw.channels.qqbot.appId",
                                "QQBot 应用 ID",
                                "messaging",
                                false,
                                false,
                                "qqbot"),
                        item(
                                "solonclaw.channels.qqbot.clientSecret",
                                "QQBot 客户端密钥",
                                "messaging",
                                true,
                                false,
                                "qqbot"),
                        item(
                                "solonclaw.channels.yuanbao.enabled",
                                "启用腾讯元宝渠道",
                                "messaging",
                                false,
                                false,
                                "yuanbao"),
                        item(
                                "solonclaw.channels.yuanbao.appId",
                                "腾讯元宝应用 ID",
                                "messaging",
                                false,
                                false,
                                "yuanbao"),
                        item(
                                "solonclaw.channels.yuanbao.appSecret",
                                "腾讯元宝应用密钥",
                                "messaging",
                                true,
                                false,
                                "yuanbao"),
                        item(
                                "solonclaw.gateway.injectionSecret",
                                "HTTP gateway injection HMAC secret",
                                "security",
                                true,
                                true,
                                "gateway"),
                        item(
                                "solonclaw.gateway.injectionMaxBodyBytes",
                                "HTTP gateway injection max body bytes",
                                "security",
                                false,
                                true,
                                "gateway"),
                        item(
                                "solonclaw.gateway.injectionReplayWindowSeconds",
                                "HTTP gateway injection replay window seconds",
                                "security",
                                false,
                                true,
                                "gateway"),
                        item(
                                "solonclaw.dashboard.accessToken",
                                "Dashboard access token",
                                "dashboard",
                                true,
                                false,
                                "dashboard"),
                        item(
                                "solonclaw.update.repo",
                                "版本检查使用的 GitHub 仓库，格式 owner/repo",
                                "runtime",
                                false,
                                true,
                                "version"),
                        item(
                                "solonclaw.update.releaseApiUrl",
                                "自定义最新版本检查 API 地址，默认 GitHub releases/latest",
                                "runtime",
                                false,
                                true,
                                "version"),
                        item(
                                "solonclaw.update.tagsApiUrl",
                                "自定义 tags 检查 API 地址",
                                "runtime",
                                false,
                                true,
                                "version"),
                        item(
                                "solonclaw.update.httpProxy",
                                "版本检查 HTTP 代理地址，例如 http://proxy.example:7890",
                                "runtime",
                                false,
                                true,
                                "version"),
                        item(
                                "solonclaw.integrations.github.token",
                                "Skills Hub 使用的 GitHub 访问令牌",
                                "tool",
                                true,
                                true,
                                "skills_hub"),
                        item(
                                "solonclaw.integrations.github.cliToken",
                                "GitHub CLI 回退令牌",
                                "tool",
                                true,
                                true,
                                "skills_hub"),
                        item(
                                "solonclaw.integrations.github.appId",
                                "GitHub App ID",
                                "tool",
                                false,
                                true,
                                "skills_hub"),
                        item(
                                "solonclaw.integrations.github.privateKeyPath",
                                "GitHub App 私钥路径",
                                "tool",
                                false,
                                true,
                                "skills_hub"),
                        item(
                                "solonclaw.integrations.github.installationId",
                                "GitHub App 安装 ID",
                                "tool",
                                false,
                                true,
                                "skills_hub"));
    }

    public Map<String, Object> getConfigItems() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (ConfigItemDefinition definition : definitions) {
            String value = configResolver.get(definition.key);

            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("is_set", StrUtil.isNotBlank(value));
            item.put("redacted_value", StrUtil.isBlank(value) ? null : redact(value));
            item.put("description", definition.description);
            item.put("url", definition.url);
            item.put("category", definition.category);
            item.put("is_password", definition.password);
            item.put("tools", definition.tools);
            item.put("advanced", definition.advanced);
            result.put(definition.key, item);
        }
        return result;
    }

    public Map<String, Object> reveal(String key) {
        ConfigItemDefinition definition = requireSupported(key);
        if (!definition.password) {
            throw new IllegalStateException("Runtime config item is not revealable: " + key);
        }
        String value = configResolver.get(key);
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("Runtime config item not set: " + key);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", key);
        result.put("value", value);
        return result;
    }

    public Map<String, Object> set(String key, String value) {
        return set(key, value, true);
    }

    public Map<String, Object> set(String key, String value, boolean reconnectChannels) {
        ensureSupported(key);
        configResolver.setFileValue(key, value);
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> remove(String key) {
        return remove(key, true);
    }

    public Map<String, Object> remove(String key, boolean reconnectChannels) {
        ensureSupported(key);
        configResolver.removeFileValue(key);
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    private void ensureSupported(String key) {
        requireSupported(key);
    }

    private ConfigItemDefinition requireSupported(String key) {
        for (ConfigItemDefinition definition : definitions) {
            if (definition.key.equals(key)) {
                return definition;
            }
        }
        throw new IllegalStateException("Unsupported runtime config item: " + key);
    }

    private String redact(String value) {
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private static ConfigItemDefinition item(
            String key,
            String description,
            String category,
            boolean password,
            boolean advanced,
            String tool) {
        return new ConfigItemDefinition(
                key, description, category, password, advanced, null, Arrays.asList(tool));
    }

    private static class ConfigItemDefinition {
        private final String key;
        private final String description;
        private final String category;
        private final boolean password;
        private final boolean advanced;
        private final String url;
        private final List<String> tools;

        private ConfigItemDefinition(
                String key,
                String description,
                String category,
                boolean password,
                boolean advanced,
                String url,
                List<String> tools) {
            this.key = key;
            this.description = description;
            this.category = category;
            this.password = password;
            this.advanced = advanced;
            this.url = url;
            this.tools = tools;
        }
    }
}
