package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class AppVersionServiceTest {
    @Test
    void shouldCompareSemanticVersions() {
        assertThat(AppVersionService.compareVersions("0.0.1", "0.0.2")).isLessThan(0);
        assertThat(AppVersionService.compareVersions("v0.1.0", "0.0.9")).isGreaterThan(0);
        assertThat(AppVersionService.compareVersions("1.2.0-beta", "1.2.0")).isEqualTo(0);
    }

    @Test
    void shouldResolveCustomReleaseApiAndProxyFromRuntimeConfig() throws Exception {
        AppConfig config = new AppConfig();
        File runtimeHome = Files.createTempDirectory("solon-claw-version-config").toFile();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.normalizePaths();
        FileUtil.writeUtf8String(
                "solonclaw:\n"
                        + "  update:\n"
                        + "    releaseApiUrl: https://mirror.example/releases/latest\n"
                        + "    tagsApiUrl: https://mirror.example/tags?per_page=5\n"
                        + "    httpProxy: http://127.0.0.1:7890\n",
                new File(runtimeHome, "config.yml"));
        AppVersionService service = new AppVersionService(config);

        assertThat(service.releaseApiUrl()).isEqualTo("https://mirror.example/releases/latest");
        assertThat(service.tagsApiUrl()).isEqualTo("https://mirror.example/tags?per_page=5");
        assertThat(service.updateProxyUrl()).isEqualTo("http://127.0.0.1:7890");
    }
}
