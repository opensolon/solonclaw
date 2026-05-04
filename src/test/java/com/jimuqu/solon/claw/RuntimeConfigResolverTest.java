package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class RuntimeConfigResolverTest {
    @Test
    void shouldReadNestedValuesWithCfgGetAndPreserveTypes() throws Exception {
        File runtimeHome = Files.createTempDirectory("solonclaw-runtime-config").toFile();
        FileUtil.writeUtf8String(
                "solonclaw:\n"
                        + "  display:\n"
                        + "    runtimeFooter:\n"
                        + "      enabled: true\n"
                        + "      fields:\n"
                        + "        - model\n"
                        + "        - cwd\n"
                        + "  skills:\n"
                        + "    curator:\n"
                        + "      intervalHours: 12\n",
                new File(runtimeHome, "config.yml"));

        RuntimeConfigResolver.initialize(runtimeHome.getAbsolutePath());

        assertThat(RuntimeConfigResolver.cfgGet("solonclaw.display.runtimeFooter.enabled", false))
                .isEqualTo(Boolean.TRUE);
        assertThat(RuntimeConfigResolver.cfgGet("solonclaw.skills.curator.intervalHours", 0))
                .isEqualTo(12);
        assertThat(RuntimeConfigResolver.cfgGet("missing.path", "fallback")).isEqualTo("fallback");
        assertThat(RuntimeConfigResolver.getRawValue("solonclaw.display.runtimeFooter.fields"))
                .isEqualTo(java.util.Arrays.asList("model", "cwd"));
    }

    @Test
    void shouldNotExposeRuntimeHomeAsRuntimeConfigKey() throws Exception {
        File runtimeHome = Files.createTempDirectory("solonclaw-runtime-home").toFile();
        FileUtil.writeUtf8String(
                "solonclaw:\n" + "  runtime:\n" + "    home: /tmp/other-runtime\n",
                new File(runtimeHome, "config.yml"));

        RuntimeConfigResolver resolver =
                RuntimeConfigResolver.initialize(runtimeHome.getAbsolutePath());

        assertThat(resolver.get("solonclaw.runtime.home")).isNull();
        assertThatThrownBy(() -> resolver.setFileValue("solonclaw.runtime.home", "runtime2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported config key");
    }
}
