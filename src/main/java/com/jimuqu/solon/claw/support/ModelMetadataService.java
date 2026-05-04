package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ModelMetadata;
import com.jimuqu.solon.claw.llm.LlmProviderSupport;
import com.jimuqu.solon.claw.support.constants.LlmConstants;
import java.util.ArrayList;
import java.util.List;

/** 模型能力元数据解析服务。 */
public class ModelMetadataService {
    private final AppConfig appConfig;

    public ModelMetadataService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public ModelMetadata resolve(String providerKey, AppConfig.ProviderConfig provider) {
        String dialect =
                LlmProviderSupport.normalizeDialect(provider == null ? null : provider.getDialect());
        String model = StrUtil.nullToEmpty(provider == null ? null : provider.getDefaultModel()).trim();
        ModelMetadata metadata = new ModelMetadata();
        metadata.setProvider(StrUtil.nullToEmpty(providerKey).trim());
        metadata.setModel(model);
        metadata.setDialect(dialect);
        metadata.setAliases(aliases(model));
        metadata.setContextWindow(resolveContextWindow(dialect, model));
        metadata.setMaxOutput(appConfig.getLlm().getMaxTokens());
        metadata.setSupportsTools(resolveSupportsTools(dialect));
        metadata.setSupportsReasoning(resolveSupportsReasoning(dialect, model));
        metadata.setSupportsPromptCache(resolveSupportsPromptCache(dialect));
        metadata.setSupportsStreaming(true);
        metadata.setDefaultModel(StrUtil.equals(providerKey, appConfig.getModel().getProviderKey()));
        metadata.setSupported(LlmProviderSupport.isSupportedDialect(dialect));
        return metadata;
    }

    private List<String> aliases(String model) {
        List<String> aliases = new ArrayList<String>();
        String value = StrUtil.nullToEmpty(model).trim();
        String lower = value.toLowerCase();
        if (StrUtil.isBlank(value)) {
            return aliases;
        }
        if (lower.startsWith("gpt-")) {
            aliases.add("gpt");
        }
        if (lower.contains("codex")) {
            aliases.add("codex");
        }
        if (lower.startsWith("claude")) {
            aliases.add("claude");
            if (lower.contains("sonnet")) {
                aliases.add("sonnet");
            }
            if (lower.contains("opus")) {
                aliases.add("opus");
            }
            if (lower.contains("haiku")) {
                aliases.add("haiku");
            }
        }
        if (lower.startsWith("gemini")) {
            aliases.add("gemini");
        }
        if (lower.contains("qwen")) {
            aliases.add("qwen");
        }
        if (lower.contains("deepseek")) {
            aliases.add("deepseek");
        }
        return aliases;
    }

    private int resolveContextWindow(String dialect, String model) {
        int configured = appConfig.getLlm().getContextWindowTokens();
        if (configured > 0) {
            return configured;
        }
        String lower = StrUtil.nullToEmpty(model).toLowerCase();
        if (lower.contains("gpt-5") || lower.contains("gemini-2.5") || lower.contains("gemini-3")) {
            return 1000000;
        }
        if (lower.contains("claude")) {
            return 200000;
        }
        if (LlmConstants.PROVIDER_OLLAMA.equals(dialect)) {
            return 32768;
        }
        return 128000;
    }

    private boolean resolveSupportsTools(String dialect) {
        return LlmProviderSupport.isSupportedDialect(dialect);
    }

    private boolean resolveSupportsReasoning(String dialect, String model) {
        String lower = StrUtil.nullToEmpty(model).toLowerCase();
        return LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect)
                || LlmConstants.PROVIDER_ANTHROPIC.equals(dialect)
                || lower.contains("reason")
                || lower.startsWith("o3")
                || lower.startsWith("o4")
                || lower.contains("thinking");
    }

    private boolean resolveSupportsPromptCache(String dialect) {
        return LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect)
                || LlmConstants.PROVIDER_ANTHROPIC.equals(dialect)
                || LlmConstants.PROVIDER_GEMINI.equals(dialect);
    }
}
