package com.jimuqu.solon.claw.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.support.constants.RuntimePathConstants;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** 运行时配置解析器，统一处理 runtime/config.yml 中的可写配置项。 */
public class RuntimeConfigResolver {
    private static final Object LOCK = new Object();
    private static volatile RuntimeConfigResolver current;
    private static final Map<String, String> KEY_PATHS = buildKeyPaths();

    private final File configFile;
    private volatile long lastLoadedAt;
    private volatile Map<String, Object> fileValues = Collections.emptyMap();

    private RuntimeConfigResolver(File configFile) {
        this.configFile = configFile;
        reload();
    }

    /** 基于已解析的运行时根目录初始化全局解析器。 */
    public static RuntimeConfigResolver initialize(String runtimeHome) {
        File homeDir = resolveRuntimeHome(runtimeHome);
        File configFile = FileUtil.file(homeDir, "config.yml");
        synchronized (LOCK) {
            if (current == null || !current.configFile.equals(configFile)) {
                current = new RuntimeConfigResolver(configFile);
            } else {
                current.reloadIfNeeded();
            }
            return current;
        }
    }

    /** 返回当前解析器；若尚未初始化，则使用默认 runtime 目录。 */
    public static RuntimeConfigResolver getInstance() {
        RuntimeConfigResolver instance = current;
        if (instance == null) {
            instance = initialize(RuntimePathConstants.RUNTIME_HOME);
        } else {
            instance.reloadIfNeeded();
        }
        return instance;
    }

    /** 读取生效配置值。 */
    public static String getValue(String key) {
        return getInstance().get(key);
    }

    /** 按配置路径读取原始配置值，保留 List/Map 类型。 */
    public static Object getRawValue(String key) {
        return getInstance().getRaw(key);
    }

    /** Hermes cfg_get 对齐入口：按嵌套路径读取 runtime/config.yml 的原始值。 */
    public static Object cfgGet(String path, Object defaultValue) {
        return getInstance().getByPath(path, defaultValue);
    }

    /** 返回 runtime/config.yml 文件路径。 */
    public File configFile() {
        return configFile;
    }

    /** 读取指定键的生效值。 */
    public String get(String key) {
        reloadIfNeeded();
        String path = resolvePath(key);
        if (StrUtil.isBlank(path)) {
            return null;
        }
        return stringify(fileValues.get(path));
    }

    /** 读取指定键的原始文件值。 */
    public Object getRaw(String key) {
        reloadIfNeeded();
        String path = resolvePath(key);
        if (StrUtil.isBlank(path)) {
            return null;
        }
        return fileValues.get(path);
    }

    /** 读取指定嵌套路径的原始文件值。 */
    public Object getByPath(String path, Object defaultValue) {
        reloadIfNeeded();
        if (StrUtil.isBlank(path)) {
            return defaultValue;
        }
        Object value = fileValues.get(path);
        return value == null ? defaultValue : value;
    }

    /** 返回 runtime/config.yml 中的文件值快照。 */
    public Map<String, String> fileValues() {
        reloadIfNeeded();
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : KEY_PATHS.entrySet()) {
            String value = stringify(fileValues.get(entry.getValue()));
            if (value != null) {
                result.put(entry.getValue(), value);
            }
        }
        return result;
    }

    /** 返回生效值快照。 */
    public Map<String, String> effectiveValues(Iterable<String> keys) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (String key : keys) {
            result.put(key, get(key));
        }
        return result;
    }

    /** 设置 runtime/config.yml 中的键值。 */
    public synchronized void setFileValue(String key, String value) {
        String path = requirePath(key);
        Map<String, Object> root = loadYamlRoot();
        setNestedValue(root, path, StrUtil.nullToEmpty(value));
        write(root);
    }

    /** 删除 runtime/config.yml 中的键值。 */
    public synchronized void removeFileValue(String key) {
        String path = requirePath(key);
        Map<String, Object> root = loadYamlRoot();
        removeNestedValue(root, path);
        write(root);
    }

    /** 强制重载 runtime/config.yml。 */
    public synchronized void reload() {
        FileUtil.mkParentDirs(configFile);
        if (!configFile.exists()) {
            fileValues = Collections.emptyMap();
            lastLoadedAt = 0L;
            return;
        }

        try {
            Map<String, Object> flattened = new LinkedHashMap<String, Object>();
            Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
            if (parsed instanceof Map) {
                flatten("", sanitizeMap((Map<?, ?>) parsed), flattened);
            }
            fileValues = flattened;
            lastLoadedAt = configFile.lastModified();
        } catch (Exception e) {
            lastLoadedAt = configFile.lastModified();
        }
    }

    private void reloadIfNeeded() {
        if (!configFile.exists()) {
            if (!fileValues.isEmpty()) {
                reload();
            }
            return;
        }
        if (configFile.lastModified() != lastLoadedAt) {
            synchronized (this) {
                if (configFile.lastModified() != lastLoadedAt) {
                    reload();
                }
            }
        }
    }

    private Map<String, Object> loadYamlRoot() {
        if (!configFile.exists()) {
            return new LinkedHashMap<String, Object>();
        }
        Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
        if (!(parsed instanceof Map)) {
            return new LinkedHashMap<String, Object>();
        }
        return sanitizeMap((Map<?, ?>) parsed);
    }

    private void write(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        FileUtil.mkParentDirs(configFile);
        try {
            File temp = new File(configFile.getParentFile(), configFile.getName() + ".tmp");
            FileUtil.writeUtf8String(new Yaml(options).dump(root), temp);
            try {
                Files.move(
                        temp.toPath(),
                        configFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(temp.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write runtime config", e);
        }
        reload();
    }

    private String requirePath(String key) {
        String path = resolvePath(key);
        if (StrUtil.isBlank(path)) {
            throw new IllegalStateException("Unsupported config key: " + key);
        }
        return path;
    }

    private String resolvePath(String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        String mapped = KEY_PATHS.get(key);
        if (StrUtil.isNotBlank(mapped)) {
            return mapped;
        }
        if (key.startsWith("solonclaw.runtime.")) {
            return null;
        }
        if (key.startsWith("solonclaw.")
                || key.startsWith("providers.")
                || key.startsWith("model.")
                || key.startsWith("fallbackProviders.")) {
            return key;
        }
        return null;
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            StringBuilder buffer = new StringBuilder();
            for (Object item : (List<?>) value) {
                if (item == null || StrUtil.isBlank(String.valueOf(item))) {
                    continue;
                }
                if (buffer.length() > 0) {
                    buffer.append(',');
                }
                buffer.append(String.valueOf(item).trim());
            }
            return buffer.toString();
        }
        if (value instanceof Map) {
            return ONode.serialize(value);
        }
        return String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object currentValue = cursor.get(parts[i]);
            if (!(currentValue instanceof Map)) {
                currentValue = new LinkedHashMap<String, Object>();
                cursor.put(parts[i], currentValue);
            }
            cursor = (Map<String, Object>) currentValue;
        }
        cursor.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private boolean removeNestedValue(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        List<Map<String, Object>> parents = new ArrayList<Map<String, Object>>();
        List<String> keys = new ArrayList<String>();
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object currentValue = cursor.get(parts[i]);
            if (!(currentValue instanceof Map)) {
                return false;
            }
            parents.add(cursor);
            keys.add(parts[i]);
            cursor = (Map<String, Object>) currentValue;
        }
        Object removed = cursor.remove(parts[parts.length - 1]);
        if (removed == null) {
            return false;
        }
        for (int i = parents.size() - 1; i >= 0; i--) {
            Object currentValue = parents.get(i).get(keys.get(i));
            if (currentValue instanceof Map && ((Map<?, ?>) currentValue).isEmpty()) {
                parents.get(i).remove(keys.get(i));
            } else {
                break;
            }
        }
        return true;
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

    private static File resolveRuntimeHome(String runtimeHome) {
        String raw = StrUtil.blankToDefault(runtimeHome, RuntimePathConstants.RUNTIME_HOME);
        File file = new File(raw);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(System.getProperty("user.dir"), raw);
    }

    private static Map<String, String> buildKeyPaths() {
        Map<String, String> mappings = new LinkedHashMap<String, String>();

        add(mappings, "model.providerKey");
        add(mappings, "model.default");
        add(mappings, "providers.default.name");
        add(mappings, "providers.default.baseUrl");
        add(mappings, "providers.default.apiKey");
        add(mappings, "providers.default.defaultModel");
        add(mappings, "providers.default.dialect");

        addAll(
                mappings,
                "solonclaw.llm.stream",
                "solonclaw.llm.reasoningEffort",
                "solonclaw.llm.temperature",
                "solonclaw.llm.maxTokens",
                "solonclaw.llm.contextWindowTokens",
                "solonclaw.scheduler.enabled",
                "solonclaw.scheduler.tickSeconds",
                "solonclaw.compression.enabled",
                "solonclaw.compression.thresholdPercent",
                "solonclaw.compression.summaryModel",
                "solonclaw.compression.protectHeadMessages",
                "solonclaw.compression.tailRatio",
                "solonclaw.learning.enabled",
                "solonclaw.learning.toolCallThreshold",
                "solonclaw.skills.curator.enabled",
                "solonclaw.skills.curator.intervalHours",
                "solonclaw.skills.curator.minIdleHours",
                "solonclaw.skills.curator.staleAfterDays",
                "solonclaw.skills.curator.archiveAfterDays",
                "solonclaw.rollback.enabled",
                "solonclaw.rollback.maxCheckpointsPerSource",
                "solonclaw.display.toolProgress",
                "solonclaw.display.showReasoning",
                "solonclaw.display.toolPreviewLength",
                "solonclaw.display.progressThrottleMs",
                "solonclaw.display.runtimeFooter.enabled",
                "solonclaw.display.runtimeFooter.fields",
                "solonclaw.gateway.allowedUsers",
                "solonclaw.gateway.allowAllUsers",
                "solonclaw.gateway.injectionSecret",
                "solonclaw.gateway.injectionMaxBodyBytes",
                "solonclaw.gateway.injectionReplayWindowSeconds",
                "solonclaw.dashboard.accessToken",
                "solonclaw.agent.heartbeat.intervalMinutes",
                "solonclaw.react.maxSteps",
                "solonclaw.react.retryMax",
                "solonclaw.react.retryDelayMs",
                "solonclaw.react.delegateMaxSteps",
                "solonclaw.react.delegateRetryMax",
                "solonclaw.react.delegateRetryDelayMs",
                "solonclaw.react.summarizationEnabled",
                "solonclaw.react.summarizationMaxMessages",
                "solonclaw.react.summarizationMaxTokens",
                "solonclaw.trace.retentionDays",
                "solonclaw.trace.maxAttempts",
                "solonclaw.trace.toolPreviewLength",
                "solonclaw.task.busyPolicy",
                "solonclaw.task.staleAfterMinutes",
                "solonclaw.task.subagentMaxConcurrency",
                "solonclaw.task.subagentMaxDepth",
                "solonclaw.task.toolOutputInlineLimit",
                "solonclaw.task.mediaCacheTtlHours",
                "solonclaw.mcp.enabled",
                "solonclaw.update.repo",
                "solonclaw.update.releaseApiUrl",
                "solonclaw.update.tagsApiUrl",
                "solonclaw.update.httpProxy",
                "solonclaw.tests.liveAi.enabled",
                "solonclaw.tests.dingtalk.privateOpenConversationId",
                "solonclaw.tests.dingtalk.privateUserId",
                "solonclaw.integrations.github.token",
                "solonclaw.integrations.github.cliToken",
                "solonclaw.integrations.github.appId",
                "solonclaw.integrations.github.privateKeyPath",
                "solonclaw.integrations.github.installationId",
                "solonclaw.pdf.fontPath");

        addChannelMappings(
                mappings,
                "feishu",
                "appId",
                "appSecret",
                "websocketUrl",
                "botOpenId",
                "botUserId",
                "botName",
                "toolProgress");
        addAll(
                mappings,
                "solonclaw.channels.feishu.comment.enabled",
                "solonclaw.channels.feishu.comment.pairingFile",
                "solonclaw.display.platforms.feishu.runtimeFooter.enabled");
        addChannelMappings(
                mappings,
                "dingtalk",
                "clientId",
                "clientSecret",
                "robotCode",
                "coolAppCode",
                "streamUrl",
                "toolProgress",
                "progressCardTemplateId");
        addAll(
                mappings,
                "solonclaw.channels.dingtalk.aiCardStreaming.enabled",
                "solonclaw.display.platforms.dingtalk.runtimeFooter.enabled");
        addChannelMappings(mappings, "wecom", "botId", "secret", "websocketUrl", "toolProgress");
        addAll(
                mappings,
                "solonclaw.channels.wecom.groupMemberAllowedUsers",
                "solonclaw.display.platforms.wecom.runtimeFooter.enabled");
        addChannelMappings(
                mappings,
                "weixin",
                "token",
                "accountId",
                "baseUrl",
                "cdnBaseUrl",
                "longPollUrl",
                "splitMultilineMessages",
                "sendChunkDelaySeconds",
                "sendChunkRetries",
                "sendChunkRetryDelaySeconds",
                "toolProgress");
        add(mappings, "solonclaw.display.platforms.weixin.runtimeFooter.enabled");
        addChannelMappings(
                mappings,
                "qqbot",
                "appId",
                "clientSecret",
                "apiDomain",
                "websocketUrl",
                "markdownSupport",
                "toolProgress");
        add(mappings, "solonclaw.display.platforms.qqbot.runtimeFooter.enabled");
        addChannelMappings(
                mappings,
                "yuanbao",
                "appId",
                "appSecret",
                "botId",
                "apiDomain",
                "websocketUrl",
                "toolProgress");
        add(mappings, "solonclaw.display.platforms.yuanbao.runtimeFooter.enabled");
        return mappings;
    }

    private static void addChannelMappings(
            Map<String, String> mappings, String channelName, String... extraFields) {
        String base = "solonclaw.channels." + channelName + ".";
        addAll(
                mappings,
                base + "enabled",
                base + "allowedUsers",
                base + "allowAllUsers",
                base + "unauthorizedDmBehavior",
                base + "dmPolicy",
                base + "groupPolicy",
                base + "groupAllowedUsers");
        for (String field : extraFields) {
            add(mappings, base + field);
        }
    }

    private static void addAll(Map<String, String> mappings, String... paths) {
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            add(mappings, path);
        }
    }

    private static void add(Map<String, String> mappings, String path) {
        mappings.put(path, path);
    }
}
