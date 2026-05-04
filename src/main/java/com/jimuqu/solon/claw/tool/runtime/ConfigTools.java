package com.jimuqu.solon.claw.tool.runtime;

import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.core.model.ToolResultEnvelope;
import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** 运行时配置工具。 */
@RequiredArgsConstructor
public class ConfigTools {
    private final RuntimeSettingsService runtimeSettingsService;
    private final GatewayRuntimeRefreshService gatewayRuntimeRefreshService;

    @ToolMapping(
            name = "config_get",
            description =
                    "Read a whitelisted runtime config key, such as llm.model or channels.weixin.enabled.")
    public String configGet(@Param(name = "key", description = "配置键，例如 llm.model") String key) {
        try {
            Object value = runtimeSettingsService.getConfigValue(key);
            String preview = value == null ? "" : String.valueOf(value);
            return ToolResultEnvelope.ok("读取运行时配置：" + key)
                    .data("key", key)
                    .data("value", value)
                    .preview(preview)
                    .toJson();
        } catch (Exception e) {
            return error(e);
        }
    }

    @ToolMapping(
            name = "config_set",
            description =
                    "Update a whitelisted runtime config key. Global config changes take effect on the next message.")
    public String configSet(
            @Param(name = "key", description = "配置键，例如 llm.model 或 channels.weixin.enabled")
                    String key,
            @Param(name = "value", description = "新的配置值，列表键使用逗号分隔") String value) {
        try {
            runtimeSettingsService.setConfigValue(key, value);
            Object current = runtimeSettingsService.getConfigValue(key);
            return ToolResultEnvelope.ok("已更新运行时配置：" + key)
                    .data("key", key)
                    .data("value", current)
                    .data("note", "takes effect on the next message")
                    .preview(key + "=" + current)
                    .toJson();
        } catch (Exception e) {
            return error(e);
        }
    }

    @ToolMapping(
            name = "config_refresh",
            description =
                    "Validate runtime/config.yml first, then refresh runtime config. If validation fails, do not refresh.")
    public String configRefresh(
            @Param(
                            name = "reconnectChannels",
                            description = "是否重连渠道连接；默认 false",
                            required = false)
                    Boolean reconnectChannels) {
        try {
            GatewayRuntimeRefreshService.RefreshResult result =
                    Boolean.TRUE.equals(reconnectChannels)
                            ? gatewayRuntimeRefreshService.refreshNow()
                            : gatewayRuntimeRefreshService.refreshConfigOnly();
            ToolResultEnvelope envelope =
                    result.isSuccess()
                            ? ToolResultEnvelope.ok(result.getMessage())
                            : ToolResultEnvelope.error(result.getMessage());
            return envelope
                    .data("refreshed", Boolean.valueOf(result.isRefreshed()))
                    .data("reconnectedChannels", Boolean.valueOf(result.isReconnectedChannels()))
                    .data("configFile", result.getConfigFile())
                    .data("message", result.getMessage())
                    .preview(result.getMessage())
                    .toJson();
        } catch (Exception e) {
            return error(e);
        }
    }

    @ToolMapping(
            name = "config_set_secret",
            description =
                    "Update a whitelisted runtime secret key, such as providers.default.apiKey.")
    public String configSetSecret(
            @Param(name = "key", description = "配置键，例如 providers.default.apiKey") String key,
            @Param(name = "value", description = "新的密钥值") String value) {
        try {
            runtimeSettingsService.setSecretValue(key, value);
            return ToolResultEnvelope.ok("已更新运行时密钥：" + key)
                    .data("key", key)
                    .data("note", "takes effect on the next message")
                    .preview(key + "=***")
                    .toJson();
        } catch (Exception e) {
            return error(e);
        }
    }

    private String error(Exception e) {
        return ToolResultEnvelope.error(
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .toJson();
    }

    @RequiredArgsConstructor
    public static class ConfigGetTool {
        private final ConfigTools delegate;

        @ToolMapping(
                name = "config_get",
                description =
                        "Read a whitelisted runtime config key, such as llm.model or channels.weixin.enabled.")
        public String configGet(@Param(name = "key", description = "配置键，例如 llm.model") String key) {
            return delegate.configGet(key);
        }
    }

    @RequiredArgsConstructor
    public static class ConfigSetTool {
        private final ConfigTools delegate;

        @ToolMapping(
                name = "config_set",
                description =
                        "Update a whitelisted runtime config key. Global config changes take effect on the next message.")
        public String configSet(
                @Param(name = "key", description = "配置键，例如 llm.model 或 channels.weixin.enabled")
                        String key,
                @Param(name = "value", description = "新的配置值，列表键使用逗号分隔") String value) {
            return delegate.configSet(key, value);
        }
    }

    @RequiredArgsConstructor
    public static class ConfigSetSecretTool {
        private final ConfigTools delegate;

        @ToolMapping(
                name = "config_set_secret",
                description =
                        "Update a whitelisted runtime secret key, such as providers.default.apiKey.")
        public String configSetSecret(
                @Param(name = "key", description = "配置键，例如 providers.default.apiKey") String key,
                @Param(name = "value", description = "新的密钥值") String value) {
            return delegate.configSetSecret(key, value);
        }
    }

    @RequiredArgsConstructor
    public static class ConfigRefreshTool {
        private final ConfigTools delegate;

        @ToolMapping(
                name = "config_refresh",
                description =
                        "Validate runtime/config.yml first, then refresh runtime config. If validation fails, do not refresh.")
        public String configRefresh(
                @Param(
                                name = "reconnectChannels",
                                description = "是否重连渠道连接；默认 false",
                                required = false)
                        Boolean reconnectChannels) {
            return delegate.configRefresh(reconnectChannels);
        }
    }
}
