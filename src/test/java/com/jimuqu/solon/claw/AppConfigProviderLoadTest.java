package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

public class AppConfigProviderLoadTest {
    @Test
    void shouldLoadProvidersAndFallbacksFromRuntimeConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-provider-load").toFile();
        FileUtil.writeUtf8String(
                "providers:\n"
                        + "  openai-direct:\n"
                        + "    name: OpenAI渠道\n"
                        + "    baseUrl: https://api.openai.com\n"
                        + "    apiKey: test-key\n"
                        + "    defaultModel: gpt-5-mini\n"
                        + "    dialect: openai-responses\n"
                        + "  backup:\n"
                        + "    name: 备用渠道\n"
                        + "    baseUrl: https://backup.example.com#\n"
                        + "    apiKey: backup-key\n"
                        + "    defaultModel: claude-sonnet-4\n"
                        + "    dialect: anthropic\n"
                        + "model:\n"
                        + "  providerKey: openai-direct\n"
                        + "  default: \n"
                        + "fallbackProviders:\n"
                        + "  - provider: backup\n",
                new File(runtimeHome, "config.yml"));

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());
        AppConfig config = AppConfig.load(props);

        assertThat(config.getProviders()).containsKeys("openai-direct", "backup");
        assertThat(config.getModel().getProviderKey()).isEqualTo("openai-direct");
        assertThat(config.getLlm().getProvider()).isEqualTo("openai-direct");
        assertThat(config.getLlm().getDialect()).isEqualTo("openai-responses");
        assertThat(config.getLlm().getApiUrl()).isEqualTo("https://api.openai.com/v1/responses");
        assertThat(config.getLlm().getModel()).isEqualTo("gpt-5-mini");
        assertThat(config.getFallbackProviders()).hasSize(1);
        assertThat(config.getFallbackProviders().get(0).getProvider()).isEqualTo("backup");
    }

    @Test
    void shouldRejectUnknownFallbackProvider() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-provider-load-invalid").toFile();
        FileUtil.writeUtf8String(
                "providers:\n"
                        + "  openai-direct:\n"
                        + "    name: OpenAI渠道\n"
                        + "    baseUrl: https://api.openai.com\n"
                        + "    defaultModel: gpt-5-mini\n"
                        + "    dialect: openai-responses\n"
                        + "model:\n"
                        + "  providerKey: openai-direct\n"
                        + "fallbackProviders:\n"
                        + "  - provider: missing-provider\n",
                new File(runtimeHome, "config.yml"));

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());

        assertThatThrownBy(() -> AppConfig.load(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallbackProviders");
    }
}
