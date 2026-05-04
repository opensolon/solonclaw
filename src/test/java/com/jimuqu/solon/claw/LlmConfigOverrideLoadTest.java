package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

public class LlmConfigOverrideLoadTest {
    @Test
    void shouldLoadStructuredProviderModelAndApiKeyFromRuntimeConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("solon-claw-llm-config").toFile();
        File configFile = new File(runtimeHome, "config.yml");
        FileUtil.writeUtf8String(
                "providers:\n"
                        + "  default:\n"
                        + "    name: Default Provider\n"
                        + "    baseUrl: https://api.jimuqu.com\n"
                        + "    apiKey: test-key\n"
                        + "    defaultModel: gpt-5.4\n"
                        + "    dialect: openai-responses\n"
                        + "model:\n"
                        + "  providerKey: default\n"
                        + "  default: \"\"\n",
                configFile);

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());
        props.put("providers.default.dialect", "ollama");
        props.put("providers.default.baseUrl", "http://127.0.0.1:11434");
        props.put("providers.default.defaultModel", "qwen");

        AppConfig config = AppConfig.load(props);

        assertThat(config.getLlm().getProvider()).isEqualTo("default");
        assertThat(config.getLlm().getDialect()).isEqualTo("openai-responses");
        assertThat(config.getLlm().getApiUrl()).isEqualTo("https://api.jimuqu.com/v1/responses");
        assertThat(config.getLlm().getModel()).isEqualTo("gpt-5.4");
        assertThat(config.getLlm().getApiKey()).isEqualTo("test-key");
    }
}
