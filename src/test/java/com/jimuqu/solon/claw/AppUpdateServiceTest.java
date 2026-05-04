package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.update.AppUpdateService;
import com.jimuqu.solon.claw.support.update.AppVersionService;
import org.junit.jupiter.api.Test;

public class AppUpdateServiceTest {
    @Test
    void shouldFallbackToTagsWhenReleaseApiReturns404() {
        FakeVersionService versionService = new FakeVersionService(new AppConfig());
        versionService.setDeploymentMode("docker");
        versionService.setCurrentVersion("0.0.1");
        versionService.setCurrentTag("v0.0.1");
        versionService.setReleaseRepo("chengliang4810/solon-claw");

        FakeUpdateService service = new FakeUpdateService(new AppConfig(), versionService);
        service.setReleaseStatus(404);
        service.setTagsBody("[{\"name\":\"v0.0.2\"}]");

        AppUpdateService.VersionStatus status = service.getVersionStatus(true);

        assertThat(status.getLatestTag()).isEqualTo("v0.0.2");
        assertThat(status.getVersionSource()).isEqualTo("tag");
        assertThat(status.isUpdateAvailable()).isTrue();
        assertThat(status.getUpdateErrorMessage()).isNull();
    }

    @Test
    void shouldRenderVersionReportAsMultiLineKeyValuePairs() {
        FakeVersionService versionService = new FakeVersionService(new AppConfig());
        versionService.setDeploymentMode("docker");
        versionService.setCurrentVersion("0.0.1");
        versionService.setCurrentTag("v0.0.1");
        versionService.setReleaseRepo("chengliang4810/solon-claw");

        FakeUpdateService service = new FakeUpdateService(new AppConfig(), versionService);
        service.setReleaseStatus(404);
        service.setTagsBody("[{\"name\":\"v0.0.2\"}]");

        String report = service.formatVersionReport(true);

        assertThat(report).contains("当前版本: v0.0.1");
        assertThat(report).contains("部署方式: docker");
        assertThat(report).contains("最新版本: v0.0.2");
        assertThat(report).contains("版本来源: tag");
        assertThat(report.split("\\R")).allMatch(line -> line.contains(": "));
    }

    @Test
    void shouldAllowDockerUpdateWhenLatestVersionComesFromTags() {
        FakeVersionService versionService = new FakeVersionService(new AppConfig());
        versionService.setDeploymentMode("docker");
        versionService.setCurrentVersion("0.0.1");
        versionService.setCurrentTag("v0.0.1");
        versionService.setReleaseRepo("chengliang4810/solon-claw");

        FakeUpdateService service = new FakeUpdateService(new AppConfig(), versionService);
        service.setReleaseStatus(404);
        service.setTagsBody("[{\"name\":\"v0.0.2\"}]");

        AppUpdateService.UpdateResult result = service.startUpdate();

        assertThat(result.isError()).isFalse();
        assertThat(result.getMessage()).contains("Docker 部署");
        assertThat(result.getMessage()).contains("最新版本: v0.0.2");
        assertThat(result.getMessage()).contains("docker compose pull");
    }

    private static class FakeUpdateService extends AppUpdateService {
        private int releaseStatus = 200;
        private String releaseBody = "";
        private int tagsStatus = 200;
        private String tagsBody = "";
        private final AppVersionService versionService;

        private FakeUpdateService(AppConfig appConfig, AppVersionService versionService) {
            super(appConfig, versionService);
            this.versionService = versionService;
        }

        @Override
        protected ApiFetchResult executeGithubJson(String url) {
            ApiFetchResult result = new ApiFetchResult();
            result.setUrl(url);
            if (url.equals(versionService.releaseApiUrl())) {
                result.setStatusCode(releaseStatus);
                result.setBody(releaseBody);
                return result;
            }
            if (url.equals(versionService.tagsApiUrl())) {
                result.setStatusCode(tagsStatus);
                result.setBody(tagsBody);
                return result;
            }
            result.setStatusCode(404);
            return result;
        }

        private void setReleaseStatus(int releaseStatus) {
            this.releaseStatus = releaseStatus;
        }

        private void setTagsBody(String tagsBody) {
            this.tagsBody = tagsBody;
        }
    }

    private static class FakeVersionService extends AppVersionService {
        private String deploymentMode = "dev";
        private String currentVersion = "0.0.1";
        private String currentTag = "v0.0.1";
        private String releaseRepo = "chengliang4810/solon-claw";

        private FakeVersionService(AppConfig appConfig) {
            super(appConfig);
        }

        @Override
        public String deploymentMode() {
            return deploymentMode;
        }

        @Override
        public String currentVersion() {
            return currentVersion;
        }

        @Override
        public String currentTag() {
            return currentTag;
        }

        @Override
        public String releaseRepo() {
            return releaseRepo;
        }

        private void setDeploymentMode(String deploymentMode) {
            this.deploymentMode = deploymentMode;
        }

        private void setCurrentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        private void setCurrentTag(String currentTag) {
            this.currentTag = currentTag;
        }

        private void setReleaseRepo(String releaseRepo) {
            this.releaseRepo = releaseRepo;
        }
    }
}
