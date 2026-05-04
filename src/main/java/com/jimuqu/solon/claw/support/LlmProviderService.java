package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.llm.LlmProviderSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** LLM provider 解析服务。 */
public class LlmProviderService {
    private final AppConfig appConfig;

    public LlmProviderService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public ResolvedProvider resolveEffectiveProvider(SessionRecord session) {
        return resolveEffectiveProvider(session, null);
    }

    public ResolvedProvider resolveEffectiveProvider(
            SessionRecord session, String agentDefaultModel) {
        String providerKey = StrUtil.nullToEmpty(appConfig.getModel().getProviderKey()).trim();
        String model = "";
        String override =
                session == null ? "" : StrUtil.nullToEmpty(session.getModelOverride()).trim();
        if (StrUtil.isNotBlank(override)) {
            if (override.contains(":")) {
                String[] parts = override.split(":", 2);
                providerKey = StrUtil.nullToEmpty(parts[0]).trim();
                model = StrUtil.nullToEmpty(parts[1]).trim();
            } else {
                model = override;
            }
        } else if (StrUtil.isNotBlank(agentDefaultModel)) {
            model = agentDefaultModel.trim();
        }
        return resolveProvider(providerKey, model);
    }

    public ResolvedProvider resolveProvider(String providerKey, String explicitModel) {
        String key = StrUtil.nullToEmpty(providerKey).trim();
        AppConfig.ProviderConfig provider = appConfig.getProviders().get(key);
        if (provider == null) {
            throw new IllegalStateException("未找到 provider：" + key);
        }

        String model = StrUtil.nullToEmpty(explicitModel).trim();
        if (StrUtil.isBlank(model)) {
            model = StrUtil.nullToEmpty(provider.getDefaultModel()).trim();
        }
        if (StrUtil.isBlank(model)) {
            model = StrUtil.nullToEmpty(appConfig.getModel().getDefault()).trim();
        }

        ResolvedProvider resolved = new ResolvedProvider();
        resolved.setProviderKey(key);
        resolved.setLabel(StrUtil.blankToDefault(provider.getName(), key));
        resolved.setDialect(LlmProviderSupport.normalizeDialect(provider.getDialect()));
        resolved.setBaseUrl(StrUtil.nullToEmpty(provider.getBaseUrl()).trim());
        resolved.setApiUrl(
                LlmProviderSupport.buildApiUrl(provider.getBaseUrl(), provider.getDialect()));
        resolved.setApiKey(StrUtil.nullToEmpty(provider.getApiKey()).trim());
        resolved.setModel(model);
        return resolved;
    }

    public List<ResolvedProvider> resolveFallbackProviders() {
        if (appConfig.getFallbackProviders() == null
                || appConfig.getFallbackProviders().isEmpty()) {
            return Collections.emptyList();
        }

        List<ResolvedProvider> result = new ArrayList<ResolvedProvider>();
        for (AppConfig.FallbackProviderConfig fallback : appConfig.getFallbackProviders()) {
            if (fallback == null || StrUtil.isBlank(fallback.getProvider())) {
                continue;
            }
            result.add(resolveProvider(fallback.getProvider().trim(), fallback.getModel()));
        }
        return result;
    }

    public boolean hasProvider(String providerKey) {
        return appConfig.getProviders().containsKey(StrUtil.nullToEmpty(providerKey).trim());
    }

    public Map<String, AppConfig.ProviderConfig> providers() {
        return appConfig.getProviders();
    }

    public static class ResolvedProvider {
        private String providerKey;
        private String label;
        private String dialect;
        private String baseUrl;
        private String apiUrl;
        private String apiKey;
        private String model;

        public String getProviderKey() {
            return providerKey;
        }

        public void setProviderKey(String providerKey) {
            this.providerKey = providerKey;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDialect() {
            return dialect;
        }

        public void setDialect(String dialect) {
            this.dialect = dialect;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
