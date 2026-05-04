package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ModelMetadata;
import com.jimuqu.solon.claw.llm.LlmProviderSupport;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.ModelMetadataService;
import com.jimuqu.solon.claw.support.constants.LlmConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/** Dashboard provider 配置管理服务。 */
public class DashboardProviderService {
    private final AppConfig appConfig;
    private final com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
            gatewayRuntimeRefreshService;
    private final LlmProviderService llmProviderService;
    private final ModelMetadataService modelMetadataService;

    public DashboardProviderService(
            AppConfig appConfig,
            com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
                    gatewayRuntimeRefreshService,
            LlmProviderService llmProviderService) {
        this.appConfig = appConfig;
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        this.llmProviderService = llmProviderService;
        this.modelMetadataService = new ModelMetadataService(appConfig);
    }

    public Map<String, Object> listProviders() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, AppConfig.ProviderConfig> entry :
                appConfig.getProviders().entrySet()) {
            items.add(toProviderMap(entry.getKey(), entry.getValue()));
        }
        result.put("providers", items);
        result.put("defaultProviderKey", appConfig.getModel().getProviderKey());
        result.put("defaultModel", appConfig.getModel().getDefault());
        result.put("fallbackProviders", cloneFallbackProviders(appConfig.getFallbackProviders()));
        return result;
    }

    public Map<String, Object> hermesModels() {
        Map<String, Object> result = listProviders();
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, AppConfig.ProviderConfig> entry :
                appConfig.getProviders().entrySet()) {
            AppConfig.ProviderConfig provider = entry.getValue();
            ModelMetadata metadata = modelMetadataService.resolve(entry.getKey(), provider);
            Map<String, Object> model = new LinkedHashMap<String, Object>();
            model.put("provider", entry.getKey());
            model.put("model", provider.getDefaultModel());
            model.put("dialect", provider.getDialect());
            model.put("role", entry.getKey().equals(appConfig.getModel().getProviderKey()) ? "primary" : "auxiliary");
            model.put("status", providerStatus(provider));
            model.put("metadata", metadataMap(metadata));
            model.put("aliases", metadata.getAliases());
            model.put("context_window", metadata.getContextWindow());
            model.put("max_output", metadata.getMaxOutput());
            model.put("reasoning_effort", appConfig.getLlm().getReasoningEffort());
            models.add(model);
        }
        result.put("models", models);
        result.put("fallback_chain", cloneFallbackProviders(appConfig.getFallbackProviders()));
        return result;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> providers = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, AppConfig.ProviderConfig> entry :
                appConfig.getProviders().entrySet()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("provider", entry.getKey());
            item.put("status", providerStatus(entry.getValue()));
            item.put("checked_at", System.currentTimeMillis());
            providers.add(item);
        }
        result.put("providers", providers);
        return result;
    }

    private String providerStatus(AppConfig.ProviderConfig provider) {
        if (provider == null || StrUtil.isBlank(provider.getBaseUrl())) {
            return "unreachable";
        }
        if (StrUtil.isBlank(provider.getApiKey())
                && !"ollama".equalsIgnoreCase(provider.getDialect())) {
            return "missing_key";
        }
        return "configured";
    }

    private Map<String, Object> metadataMap(ModelMetadata metadata) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("provider", metadata.getProvider());
        map.put("model", metadata.getModel());
        map.put("dialect", metadata.getDialect());
        map.put("context_window", metadata.getContextWindow());
        map.put("max_output", metadata.getMaxOutput());
        map.put("tool_calling", Boolean.valueOf(metadata.isSupportsTools()));
        map.put("streaming", Boolean.valueOf(metadata.isSupportsStreaming()));
        map.put("reasoning", Boolean.valueOf(metadata.isSupportsReasoning()));
        map.put("prompt_cache", Boolean.valueOf(metadata.isSupportsPromptCache()));
        map.put("default_model", Boolean.valueOf(metadata.isDefaultModel()));
        map.put("supported", Boolean.valueOf(metadata.isSupported()));
        return map;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createProvider(Map<String, Object> data) {
        String providerKey = readString(data, "providerKey");
        ensureProviderKey(providerKey);
        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> providers = getOrCreateMap(root, "providers");
        if (providers.containsKey(providerKey)) {
            throw new IllegalArgumentException("Provider 已存在：" + providerKey);
        }
        providers.put(providerKey, toProviderNode(data, null));

        Map<String, Object> model = getOrCreateMap(root, "model");
        if (StrUtil.isBlank(readString(model, "providerKey"))) {
            model.put("providerKey", providerKey);
        }

        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateProvider(String providerKey, Map<String, Object> data) {
        ensureProviderKey(providerKey);
        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> providers = getOrCreateMap(root, "providers");
        Object existing = providers.get(providerKey);
        if (!(existing instanceof Map)) {
            throw new IllegalArgumentException("Provider 不存在：" + providerKey);
        }
        providers.put(providerKey, toProviderNode(data, (Map<String, Object>) existing));
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> deleteProvider(String providerKey) {
        ensureProviderKey(providerKey);
        if (StrUtil.equals(providerKey, appConfig.getModel().getProviderKey())) {
            throw new IllegalArgumentException("当前默认 provider 不能删除。");
        }
        for (AppConfig.FallbackProviderConfig fallback : appConfig.getFallbackProviders()) {
            if (fallback != null && StrUtil.equals(providerKey, fallback.getProvider())) {
                throw new IllegalArgumentException("该 provider 正在 fallbackProviders 中使用，不能删除。");
            }
        }

        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> providers = getOrCreateMap(root, "providers");
        providers.remove(providerKey);
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> updateDefaultModel(String providerKey, String model) {
        String nextProviderKey =
                StrUtil.isNotBlank(providerKey)
                        ? providerKey.trim()
                        : appConfig.getModel().getProviderKey();
        if (!llmProviderService.hasProvider(nextProviderKey)) {
            throw new IllegalArgumentException("未找到 provider：" + nextProviderKey);
        }

        Map<String, Object> root = loadRootForMutation();
        Map<String, Object> modelNode = getOrCreateMap(root, "model");
        modelNode.put("providerKey", nextProviderKey);
        modelNode.put("default", StrUtil.nullToEmpty(model).trim());
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> updateFallbackProviders(List<Map<String, Object>> items) {
        List<Object> next = new ArrayList<Object>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                String provider = readString(item, "provider");
                if (!llmProviderService.hasProvider(provider)) {
                    throw new IllegalArgumentException(
                            "fallbackProviders 引用了不存在的 provider：" + provider);
                }
                Map<String, Object> node = new LinkedHashMap<String, Object>();
                node.put("provider", provider);
                String model = readString(item, "model");
                if (StrUtil.isNotBlank(model)) {
                    node.put("model", model);
                }
                next.add(node);
            }
        }

        Map<String, Object> root = loadRootForMutation();
        root.put("fallbackProviders", next);
        write(root);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> listRemoteModels(Map<String, Object> data) {
        String providerKey = readString(data, "providerKey");
        String baseUrl = readString(data, "baseUrl");
        String apiKey = readString(data, "apiKey");
        String dialect = LlmProviderSupport.normalizeDialect(readString(data, "dialect"));
        AppConfig.ProviderConfig provider =
                StrUtil.isBlank(providerKey) ? null : appConfig.getProviders().get(providerKey);
        if (provider != null) {
            baseUrl = StrUtil.blankToDefault(baseUrl, provider.getBaseUrl());
            apiKey = StrUtil.blankToDefault(apiKey, provider.getApiKey());
            dialect =
                    LlmProviderSupport.normalizeDialect(
                            StrUtil.blankToDefault(dialect, provider.getDialect()));
        }
        if (StrUtil.isBlank(baseUrl)) {
            throw new IllegalArgumentException("baseUrl 不能为空。");
        }
        if (StrUtil.isBlank(dialect) || !LlmProviderSupport.isSupportedDialect(dialect)) {
            throw new IllegalArgumentException("不支持的 dialect：" + dialect);
        }

        String url = LlmProviderSupport.buildModelListUrl(baseUrl, dialect);
        HttpRequest request = HttpRequest.get(url).timeout(15000);
        if (StrUtil.isNotBlank(apiKey)) {
            if (LlmConstants.PROVIDER_GEMINI.equals(dialect)) {
                request.form("key", apiKey);
            } else {
                request.header("Authorization", "Bearer " + apiKey);
            }
            if (LlmConstants.PROVIDER_ANTHROPIC.equals(dialect)) {
                request.header("x-api-key", apiKey);
                request.header("anthropic-version", "2023-06-01");
            }
        }

        HttpResponse response = request.execute();
        int status = response.getStatus();
        String body = response.body();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("获取模型列表失败：HTTP " + status + " " + trimForError(body));
        }

        List<String> models = parseModels(body, dialect);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("url", url);
        result.put("models", models);
        return result;
    }

    private Map<String, Object> toProviderMap(
            String providerKey, AppConfig.ProviderConfig provider) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("providerKey", providerKey);
        item.put("name", StrUtil.blankToDefault(provider.getName(), providerKey));
        item.put("baseUrl", StrUtil.nullToEmpty(provider.getBaseUrl()));
        item.put("defaultModel", StrUtil.nullToEmpty(provider.getDefaultModel()));
        item.put("dialect", StrUtil.nullToEmpty(provider.getDialect()));
        item.put("hasApiKey", StrUtil.isNotBlank(provider.getApiKey()));
        item.put("isDefault", StrUtil.equals(providerKey, appConfig.getModel().getProviderKey()));
        item.put("metadata", metadataMap(modelMetadataService.resolve(providerKey, provider)));
        return item;
    }

    private Map<String, Object> toProviderNode(AppConfig.ProviderConfig provider) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", StrUtil.nullToEmpty(provider.getName()).trim());
        result.put("baseUrl", StrUtil.nullToEmpty(provider.getBaseUrl()).trim());
        result.put("apiKey", StrUtil.nullToEmpty(provider.getApiKey()).trim());
        result.put("defaultModel", StrUtil.nullToEmpty(provider.getDefaultModel()).trim());
        result.put("dialect", StrUtil.nullToEmpty(provider.getDialect()).trim());
        return result;
    }

    private List<Map<String, Object>> cloneFallbackProviders(
            List<AppConfig.FallbackProviderConfig> source) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (source == null) {
            return result;
        }
        for (AppConfig.FallbackProviderConfig item : source) {
            if (item == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("provider", StrUtil.nullToEmpty(item.getProvider()));
            row.put("model", StrUtil.nullToEmpty(item.getModel()));
            result.add(row);
        }
        return result;
    }

    private void ensureProviderKey(String providerKey) {
        if (StrUtil.isBlank(providerKey)) {
            throw new IllegalArgumentException("providerKey 不能为空。");
        }
    }

    private Map<String, Object> toProviderNode(
            Map<String, Object> source, Map<String, Object> base) {
        String name = readString(source, "name");
        String baseUrl = readString(source, "baseUrl");
        String apiKey =
                source.containsKey("apiKey")
                        ? readString(source, "apiKey")
                        : readString(base, "apiKey");
        String defaultModel = readString(source, "defaultModel");
        String dialect = LlmProviderSupport.normalizeDialect(readString(source, "dialect"));

        if (StrUtil.isBlank(baseUrl)) {
            throw new IllegalArgumentException("baseUrl 不能为空。");
        }
        if (StrUtil.isBlank(dialect) || !LlmProviderSupport.isSupportedDialect(dialect)) {
            throw new IllegalArgumentException("不支持的 dialect：" + dialect);
        }
        if (StrUtil.isBlank(defaultModel) && StrUtil.isBlank(appConfig.getModel().getDefault())) {
            throw new IllegalArgumentException("defaultModel 不能为空。");
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", StrUtil.nullToEmpty(name).trim());
        result.put("baseUrl", StrUtil.nullToEmpty(baseUrl).trim());
        result.put("apiKey", StrUtil.nullToEmpty(apiKey).trim());
        result.put("defaultModel", StrUtil.nullToEmpty(defaultModel).trim());
        result.put("dialect", dialect);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRootForMutation() {
        File configFile = new File(appConfig.getRuntime().getConfigFile());
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        if (configFile.exists()) {
            Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
            if (parsed instanceof Map) {
                root.putAll(sanitizeMap((Map<?, ?>) parsed));
            }
        }

        if (!(root.get("providers") instanceof Map)) {
            Map<String, Object> providers = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, AppConfig.ProviderConfig> entry :
                    appConfig.getProviders().entrySet()) {
                providers.put(entry.getKey(), toProviderNode(entry.getValue()));
            }
            root.put("providers", providers);
        }
        if (!(root.get("model") instanceof Map)) {
            Map<String, Object> model = new LinkedHashMap<String, Object>();
            model.put("providerKey", appConfig.getModel().getProviderKey());
            model.put("default", appConfig.getModel().getDefault());
            root.put("model", model);
        }
        if (!(root.get("fallbackProviders") instanceof List)) {
            root.put(
                    "fallbackProviders",
                    new ArrayList<Object>(
                            cloneFallbackProviders(appConfig.getFallbackProviders())));
        }
        return root;
    }

    private void write(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);

        File configFile = new File(appConfig.getRuntime().getConfigFile());
        FileUtil.mkParentDirs(configFile);
        FileUtil.writeUtf8String(new Yaml(options).dump(root), configFile);
        gatewayRuntimeRefreshService.refreshConfigOnly();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(Map<String, Object> root, String key) {
        Object current = root.get(key);
        if (current instanceof Map) {
            return (Map<String, Object>) current;
        }
        Map<String, Object> created = new LinkedHashMap<String, Object>();
        root.put(key, created);
        return created;
    }

    private String readString(Map<String, Object> source, String key) {
        if (source == null || key == null) {
            return "";
        }
        Object value = source.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseModels(String body, String dialect) {
        List<String> models = new ArrayList<String>();
        Object parsed = ONode.deserialize(StrUtil.nullToEmpty(body), Object.class);
        if (!(parsed instanceof Map)) {
            return models;
        }
        Map<String, Object> root = (Map<String, Object>) parsed;
        if (LlmConstants.PROVIDER_OLLAMA.equals(dialect)) {
            Object items = root.get("models");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<String, Object> row = (Map<String, Object>) item;
                        addModel(models, row.get("name"));
                        addModel(models, row.get("model"));
                    }
                }
            }
            return models;
        }
        if (LlmConstants.PROVIDER_GEMINI.equals(dialect)) {
            Object items = root.get("models");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<String, Object> row = (Map<String, Object>) item;
                        String name = StrUtil.nullToEmpty(String.valueOf(row.get("name"))).trim();
                        if (StrUtil.startWith(name, "models/")) {
                            name = name.substring("models/".length());
                        }
                        addModel(models, name);
                    }
                }
            }
            return models;
        }
        Object items = root.get("data");
        if (items instanceof List) {
            for (Object item : (List<?>) items) {
                if (item instanceof Map) {
                    addModel(models, ((Map<String, Object>) item).get("id"));
                }
            }
        }
        return models;
    }

    private void addModel(List<String> models, Object model) {
        String normalized = model == null ? "" : String.valueOf(model).trim();
        if (StrUtil.isNotBlank(normalized) && !models.contains(normalized)) {
            models.add(normalized);
        }
    }

    private String trimForError(String body) {
        String text = StrUtil.nullToEmpty(body).replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.put(key, sanitizeMap((Map<?, ?>) value));
            } else if (value instanceof List) {
                result.put(key, sanitizeList((List<?>) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<Object> sanitizeList(List<?> raw) {
        List<Object> result = new ArrayList<Object>();
        for (Object item : raw) {
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
}
