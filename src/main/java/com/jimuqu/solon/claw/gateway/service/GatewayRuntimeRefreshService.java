package com.jimuqu.solon.claw.gateway.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.constants.RuntimePathConstants;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.noear.solon.Solon;
import org.noear.solon.core.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/** 运行时配置刷新服务。 */
public class GatewayRuntimeRefreshService {
    private static final Logger log = LoggerFactory.getLogger(GatewayRuntimeRefreshService.class);

    private final AppConfig appConfig;
    private final ChannelConnectionManager channelConnectionManager;
    private volatile long lastConfigMtime;

    public GatewayRuntimeRefreshService(
            AppConfig appConfig, ChannelConnectionManager channelConnectionManager) {
        this.appConfig = appConfig;
        this.channelConnectionManager = channelConnectionManager;
        this.lastConfigMtime = fileMtime(appConfig.getRuntime().getConfigFile());
    }

    public RefreshResult refreshIfNeeded() {
        long configMtime = fileMtime(appConfig.getRuntime().getConfigFile());
        if (configMtime == lastConfigMtime) {
            return RefreshResult.skipped(runtimeConfigFile().getAbsolutePath(), "配置文件未变化。");
        }
        return refreshNow();
    }

    public synchronized RefreshResult refreshNow() {
        return refreshInternal(true);
    }

    public synchronized RefreshResult refreshConfigOnly() {
        return refreshInternal(false);
    }

    private RefreshResult refreshInternal(boolean reconnectChannels) {
        File configFile = runtimeConfigFile();
        ValidationResult validation = validateRuntimeConfig(configFile);
        if (!validation.isSuccess()) {
            log.warn("Skip runtime refresh because config validation failed: {}", validation.message);
            return RefreshResult.failure(configFile.getAbsolutePath(), validation.message);
        }

        AppConfig latest;
        try {
            if (Solon.cfg() == null) {
                Props props = new Props();
                props.put("solonclaw.runtime.home", appConfig.getRuntime().getHome());
                latest = AppConfig.load(props);
            } else {
                latest = AppConfig.load(Solon.cfg());
            }
        } catch (Throwable e) {
            log.debug("Skip runtime refresh because config reload failed", e);
            return RefreshResult.failure(
                    configFile.getAbsolutePath(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        appConfig.applyFrom(latest);
        lastConfigMtime = fileMtime(appConfig.getRuntime().getConfigFile());
        if (!reconnectChannels) {
            return RefreshResult.success(
                    configFile.getAbsolutePath(), false, "运行时配置已刷新。");
        }
        channelConnectionManager.refreshAll();
        return RefreshResult.success(
                configFile.getAbsolutePath(), true, "运行时配置已刷新，渠道连接已重连。");
    }

    private long fileMtime(String path) {
        if (path == null) {
            return 0L;
        }
        File file = new File(path);
        return file.exists() ? file.lastModified() : 0L;
    }

    private File runtimeConfigFile() {
        String path = appConfig.getRuntime().getConfigFile();
        if (StrUtil.isNotBlank(path)) {
            return new File(path);
        }
        return new File(
                StrUtil.blankToDefault(appConfig.getRuntime().getHome(), RuntimePathConstants.RUNTIME_HOME),
                RuntimePathConstants.CONFIG_FILE_NAME);
    }

    private ValidationResult validateRuntimeConfig(File configFile) {
        if (configFile == null || !configFile.exists()) {
            return ValidationResult.success();
        }
        Object parsed;
        try {
            parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
        } catch (Exception e) {
            return ValidationResult.failure(
                    "runtime/config.yml 格式错误："
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        if (parsed == null) {
            return ValidationResult.success();
        }
        if (!(parsed instanceof Map)) {
            return ValidationResult.failure("runtime/config.yml 顶层必须是 YAML 对象。");
        }

        Map<String, Object> root = sanitizeMap((Map<?, ?>) parsed);
        ValidationResult containers = validateContainerTypes(root);
        if (!containers.isSuccess()) {
            return containers;
        }

        Map<String, Object> flattened = new LinkedHashMap<String, Object>();
        flatten("", root, flattened);
        for (Map.Entry<String, Object> entry : flattened.entrySet()) {
            ValidationResult typed = validateValueType(entry.getKey(), entry.getValue());
            if (!typed.isSuccess()) {
                return typed;
            }
        }
        return ValidationResult.success();
    }

    private ValidationResult validateContainerTypes(Map<String, Object> root) {
        for (String path : MAP_PATHS) {
            Object value = getByPath(root, path);
            if (value != null && !(value instanceof Map)) {
                return ValidationResult.failure(path + " 必须是 YAML 对象。");
            }
        }
        Object fallbackProviders = getByPath(root, "fallbackProviders");
        if (fallbackProviders != null && !(fallbackProviders instanceof java.util.List)) {
            return ValidationResult.failure("fallbackProviders 必须是 YAML 列表。");
        }
        Object providers = getByPath(root, "providers");
        if (providers instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) providers).entrySet()) {
                if (!(entry.getValue() instanceof Map)) {
                    return ValidationResult.failure(
                            "providers." + entry.getKey() + " 必须是 YAML 对象。");
                }
            }
        }
        if (fallbackProviders instanceof java.util.List) {
            int index = 0;
            for (Object item : (java.util.List<?>) fallbackProviders) {
                if (!(item instanceof Map)) {
                    return ValidationResult.failure(
                            "fallbackProviders[" + index + "] 必须是 YAML 对象。");
                }
                index++;
            }
        }
        return ValidationResult.success();
    }

    private ValidationResult validateValueType(String key, Object value) {
        if (INT_KEYS.contains(key) || hasSuffix(key, INT_SUFFIXES)) {
            return validateInteger(key, value);
        }
        if (DOUBLE_KEYS.contains(key) || hasSuffix(key, DOUBLE_SUFFIXES)) {
            return validateDouble(key, value);
        }
        if (BOOLEAN_KEYS.contains(key) || hasSuffix(key, BOOLEAN_SUFFIXES)) {
            return validateBoolean(key, value);
        }
        if (LIST_KEYS.contains(key) || hasSuffix(key, LIST_SUFFIXES)) {
            if (value instanceof java.util.List || value instanceof String) {
                return ValidationResult.success();
            }
            return ValidationResult.failure(key + " 必须是 YAML 列表或逗号分隔字符串。");
        }
        return ValidationResult.success();
    }

    private ValidationResult validateInteger(String key, Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            try {
                Integer.parseInt(String.valueOf(value).trim());
                return ValidationResult.success();
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (value instanceof String) {
            try {
                Integer.parseInt(((String) value).trim());
                return ValidationResult.success();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ValidationResult.failure(key + " 必须是整数。");
    }

    private ValidationResult validateDouble(String key, Object value) {
        if (value instanceof Number) {
            return ValidationResult.success();
        }
        if (value instanceof String) {
            try {
                Double.parseDouble(((String) value).trim());
                return ValidationResult.success();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ValidationResult.failure(key + " 必须是数字。");
    }

    private ValidationResult validateBoolean(String key, Object value) {
        if (value instanceof Boolean) {
            return ValidationResult.success();
        }
        if (value instanceof Number) {
            int number = ((Number) value).intValue();
            if (number == 0 || number == 1) {
                return ValidationResult.success();
            }
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)
                    || "false".equalsIgnoreCase(text)
                    || "1".equals(text)
                    || "0".equals(text)
                    || "yes".equalsIgnoreCase(text)
                    || "no".equalsIgnoreCase(text)) {
                return ValidationResult.success();
            }
        }
        return ValidationResult.failure(key + " 必须是布尔值。");
    }

    private boolean hasSuffix(String key, Set<String> suffixes) {
        if (key == null) {
            return false;
        }
        for (String suffix : suffixes) {
            if (key.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = sanitizeMap((Map<?, ?>) value);
            } else if (value instanceof java.util.List) {
                value = sanitizeList((java.util.List<?>) value);
            }
            result.put(String.valueOf(entry.getKey()), value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> sanitizeList(java.util.List<?> raw) {
        java.util.List<Object> result = new java.util.ArrayList<Object>();
        for (Object item : raw) {
            if (item instanceof Map) {
                result.add(sanitizeMap((Map<?, ?>) item));
            } else if (item instanceof java.util.List) {
                result.add(sanitizeList((java.util.List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object getByPath(Map<String, Object> root, String path) {
        if (root == null || StrUtil.isBlank(path)) {
            return null;
        }
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> input, Map<String, Object> output) {
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key =
                    prefix.length() == 0 ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<String, Object>) value, output);
            } else {
                output.put(key, value);
            }
        }
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    public static class RefreshResult {
        private final boolean success;
        private final boolean refreshed;
        private final boolean reconnectedChannels;
        private final String configFile;
        private final String message;

        private RefreshResult(
                boolean success,
                boolean refreshed,
                boolean reconnectedChannels,
                String configFile,
                String message) {
            this.success = success;
            this.refreshed = refreshed;
            this.reconnectedChannels = reconnectedChannels;
            this.configFile = configFile;
            this.message = message;
        }

        public static RefreshResult success(
                String configFile, boolean reconnectedChannels, String message) {
            return new RefreshResult(true, true, reconnectedChannels, configFile, message);
        }

        public static RefreshResult skipped(String configFile, String message) {
            return new RefreshResult(true, false, false, configFile, message);
        }

        public static RefreshResult failure(String configFile, String message) {
            return new RefreshResult(false, false, false, configFile, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isRefreshed() {
            return refreshed;
        }

        public boolean isReconnectedChannels() {
            return reconnectedChannels;
        }

        public String getConfigFile() {
            return configFile;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class ValidationResult {
        private final boolean success;
        private final String message;

        private ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static ValidationResult success() {
            return new ValidationResult(true, "");
        }

        static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

        boolean isSuccess() {
            return success;
        }
    }

    private static final Set<String> MAP_PATHS =
            setOf(
                    "providers",
                    "model",
                    "solonclaw",
                    "solonclaw.llm",
                    "solonclaw.scheduler",
                    "solonclaw.compression",
                    "solonclaw.learning",
                    "solonclaw.skills",
                    "solonclaw.skills.curator",
                    "solonclaw.rollback",
                    "solonclaw.display",
                    "solonclaw.display.runtimeFooter",
                    "solonclaw.display.platforms",
                    "solonclaw.gateway",
                    "solonclaw.dashboard",
                    "solonclaw.agent",
                    "solonclaw.agent.heartbeat",
                    "solonclaw.react",
                    "solonclaw.trace",
                    "solonclaw.task",
                    "solonclaw.mcp",
                    "solonclaw.channels",
                    "solonclaw.channels.feishu",
                    "solonclaw.channels.dingtalk",
                    "solonclaw.channels.wecom",
                    "solonclaw.channels.weixin",
                    "solonclaw.channels.qqbot",
                    "solonclaw.channels.yuanbao");

    private static final Set<String> INT_KEYS =
            setOf(
                    "solonclaw.llm.maxTokens",
                    "solonclaw.llm.contextWindowTokens",
                    "solonclaw.scheduler.tickSeconds",
                    "solonclaw.compression.protectHeadMessages",
                    "solonclaw.learning.toolCallThreshold",
                    "solonclaw.skills.curator.intervalHours",
                    "solonclaw.skills.curator.staleAfterDays",
                    "solonclaw.skills.curator.archiveAfterDays",
                    "solonclaw.rollback.maxCheckpointsPerSource",
                    "solonclaw.display.toolPreviewLength",
                    "solonclaw.display.progressThrottleMs",
                    "solonclaw.gateway.injectionMaxBodyBytes",
                    "solonclaw.gateway.injectionReplayWindowSeconds",
                    "solonclaw.agent.heartbeat.intervalMinutes",
                    "solonclaw.react.maxSteps",
                    "solonclaw.react.retryMax",
                    "solonclaw.react.retryDelayMs",
                    "solonclaw.react.delegateMaxSteps",
                    "solonclaw.react.delegateRetryMax",
                    "solonclaw.react.delegateRetryDelayMs",
                    "solonclaw.react.summarizationMaxMessages",
                    "solonclaw.react.summarizationMaxTokens",
                    "solonclaw.trace.retentionDays",
                    "solonclaw.trace.maxAttempts",
                    "solonclaw.trace.toolPreviewLength",
                    "solonclaw.task.staleAfterMinutes",
                    "solonclaw.task.subagentMaxConcurrency",
                    "solonclaw.task.subagentMaxDepth",
                    "solonclaw.task.toolOutputInlineLimit",
                    "solonclaw.task.mediaCacheTtlHours");

    private static final Set<String> DOUBLE_KEYS =
            setOf(
                    "solonclaw.llm.temperature",
                    "solonclaw.compression.thresholdPercent",
                    "solonclaw.compression.tailRatio",
                    "solonclaw.skills.curator.minIdleHours");

    private static final Set<String> BOOLEAN_KEYS =
            setOf(
                    "solonclaw.llm.stream",
                    "solonclaw.scheduler.enabled",
                    "solonclaw.compression.enabled",
                    "solonclaw.learning.enabled",
                    "solonclaw.skills.curator.enabled",
                    "solonclaw.rollback.enabled",
                    "solonclaw.display.showReasoning",
                    "solonclaw.display.runtimeFooter.enabled",
                    "solonclaw.gateway.allowAllUsers",
                    "solonclaw.react.summarizationEnabled",
                    "solonclaw.mcp.enabled");

    private static final Set<String> LIST_KEYS =
            setOf(
                    "solonclaw.display.runtimeFooter.fields",
                    "solonclaw.gateway.allowedUsers");

    private static final Set<String> INT_SUFFIXES =
            setOf(
                    ".sendChunkRetries",
                    ".toolPreviewLength",
                    ".progressThrottleMs");

    private static final Set<String> DOUBLE_SUFFIXES =
            setOf(".sendChunkDelaySeconds", ".sendChunkRetryDelaySeconds");

    private static final Set<String> BOOLEAN_SUFFIXES =
            setOf(
                    ".enabled",
                    ".allowAllUsers",
                    ".splitMultilineMessages",
                    ".comment.enabled",
                    ".aiCardStreaming.enabled",
                    ".markdownSupport",
                    ".runtimeFooter.enabled");

    private static final Set<String> LIST_SUFFIXES =
            setOf(".allowedUsers", ".groupAllowedUsers", ".runtimeFooter.fields");
}
