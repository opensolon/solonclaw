package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

public class AppConfigPathNormalizationTest {
    @Test
    void shouldResolveRuntimePathsAgainstWorkingDirectoryOnlyOnce() {
        AppConfig config = new AppConfig();
        config.getRuntime().setHome("runtime-live-onboarding");
        config.getRuntime().setContextDir("outside/context");
        config.getRuntime().setSkillsDir("outside/skills");
        config.getRuntime().setCacheDir("outside/cache");
        config.getRuntime().setStateDb("outside/state.db");
        config.getRuntime().setLogsDir("outside/logs");

        config.normalizePaths();

        String base = new File(System.getProperty("user.dir")).getAbsolutePath();
        assertThat(config.getRuntime().getHome())
                .isEqualTo(new File(base, "runtime-live-onboarding").getAbsolutePath());
        assertThat(config.getRuntime().getContextDir())
                .isEqualTo(new File(base, "runtime-live-onboarding/context").getAbsolutePath());
        assertThat(config.getRuntime().getSkillsDir())
                .isEqualTo(new File(base, "runtime-live-onboarding/skills").getAbsolutePath());
        assertThat(config.getRuntime().getCacheDir())
                .isEqualTo(new File(base, "runtime-live-onboarding/cache").getAbsolutePath());
        assertThat(config.getRuntime().getStateDb())
                .isEqualTo(
                        new File(base, "runtime-live-onboarding/data/state.db").getAbsolutePath());
        assertThat(config.getRuntime().getLogsDir())
                .isEqualTo(new File(base, "runtime-live-onboarding/logs").getAbsolutePath());
    }

    @Test
    void shouldIgnoreExternalRuntimeChildPathOverrides() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-runtime-home").toFile();
        File outside = Files.createTempDirectory("jimuqu-runtime-outside").toFile();

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());
        props.put("solonclaw.runtime.contextDir", new File(outside, "context").getAbsolutePath());
        props.put("solonclaw.runtime.skillsDir", new File(outside, "skills").getAbsolutePath());
        props.put("solonclaw.runtime.cacheDir", new File(outside, "cache").getAbsolutePath());
        props.put("solonclaw.runtime.stateDb", new File(outside, "state.db").getAbsolutePath());
        props.put("solonclaw.logging.dir", new File(outside, "logs").getAbsolutePath());

        AppConfig config = AppConfig.load(props);

        assertThat(config.getRuntime().getHome()).isEqualTo(runtimeHome.getAbsolutePath());
        assertThat(config.getRuntime().getContextDir())
                .isEqualTo(new File(runtimeHome, "context").getAbsolutePath());
        assertThat(config.getRuntime().getSkillsDir())
                .isEqualTo(new File(runtimeHome, "skills").getAbsolutePath());
        assertThat(config.getRuntime().getCacheDir())
                .isEqualTo(new File(runtimeHome, "cache").getAbsolutePath());
        assertThat(config.getRuntime().getStateDb())
                .isEqualTo(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        assertThat(config.getRuntime().getLogsDir())
                .isEqualTo(new File(runtimeHome, "logs").getAbsolutePath());
    }

    @Test
    void shouldUseConfiguredRuntimeHomeAsOnlyExternalRuntimePath() throws Exception {
        File runtimeHome = Files.createTempDirectory("solon-claw-runtime-config-home").toFile();

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());
        AppConfig config = AppConfig.load(props);

        assertThat(config.getRuntime().getHome()).isEqualTo(runtimeHome.getAbsolutePath());
        assertThat(config.getRuntime().getContextDir())
                .isEqualTo(new File(runtimeHome, "context").getAbsolutePath());
        assertThat(config.getRuntime().getStateDb())
                .isEqualTo(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        assertThat(config.getRuntime().getLogsDir())
                .isEqualTo(new File(runtimeHome, "logs").getAbsolutePath());
    }

    @Test
    void shouldSyncConfigExampleIntoRuntimeHomeOnEveryLoad() throws Exception {
        File runtimeHome = Files.createTempDirectory("solon-claw-runtime-example").toFile();
        File runtimeExample = new File(runtimeHome, "config.example.yml");
        FileUtil.writeUtf8String("stale content", runtimeExample);

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());

        AppConfig.load(props);

        assertThat(runtimeExample).exists();
        assertThat(FileUtil.readUtf8String(runtimeExample))
                .startsWith("# Agent 请注意：这是只读参考模板，请不要修改本文件；需要变更运行配置时请修改同目录的 config.yml。")
                .doesNotContain("stale content");
    }

    @Test
    void shouldCreateMinimalRuntimeConfigWhenMissing() throws Exception {
        File runtimeHome = Files.createTempDirectory("solon-claw-runtime-init").toFile();
        File runtimeConfig = new File(runtimeHome, "config.yml");

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());

        AppConfig config = AppConfig.load(props);

        assertThat(runtimeConfig).exists();
        String content = FileUtil.readUtf8String(runtimeConfig);
        assertThat(content)
                .contains("baseUrl: https://api.openai.com")
                .contains("apiKey: \"\"")
                .contains("dialect: openai")
                .contains("accessToken: \"admin\"");
        assertThat(config.getDashboard().getAccessToken()).isEqualTo("admin");
        assertThat(config.getLlm().getDialect()).isEqualTo("openai");
        assertThat(config.getLlm().getApiUrl()).isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(config.getLlm().getApiKey()).isEqualTo("");
        assertThat(config.getLlm().getModel()).isEqualTo("gpt-5.4");
    }
}
