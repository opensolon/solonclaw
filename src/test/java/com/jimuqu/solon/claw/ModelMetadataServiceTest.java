package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ModelMetadata;
import com.jimuqu.solon.claw.support.ModelMetadataService;
import org.junit.jupiter.api.Test;

public class ModelMetadataServiceTest {
    @Test
    void shouldResolveHermesModelCapabilitiesFromProviderConfig() {
        AppConfig config = new AppConfig();
        config.getModel().setProviderKey("anthropic-main");
        config.getLlm().setContextWindowTokens(0);
        config.getLlm().setMaxTokens(8192);
        AppConfig.ProviderConfig provider = new AppConfig.ProviderConfig();
        provider.setDefaultModel("claude-sonnet-4");
        provider.setDialect("anthropic");

        ModelMetadata metadata = new ModelMetadataService(config).resolve("anthropic-main", provider);

        assertThat(metadata.getProvider()).isEqualTo("anthropic-main");
        assertThat(metadata.getModel()).isEqualTo("claude-sonnet-4");
        assertThat(metadata.getAliases()).contains("claude", "sonnet");
        assertThat(metadata.getContextWindow()).isEqualTo(200000);
        assertThat(metadata.getMaxOutput()).isEqualTo(8192);
        assertThat(metadata.isSupportsTools()).isTrue();
        assertThat(metadata.isSupportsReasoning()).isTrue();
        assertThat(metadata.isSupportsPromptCache()).isTrue();
        assertThat(metadata.isDefaultModel()).isTrue();
        assertThat(metadata.isSupported()).isTrue();
    }
}
