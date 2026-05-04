package com.jimuqu.solon.claw.support.update;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import com.jimuqu.solon.claw.support.BoundedAttachmentIO;
import com.jimuqu.solon.claw.support.BoundedExecutorFactory;
import com.jimuqu.solon.claw.support.SecretRedactor;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.noear.snack4.ONode;

/** 版本检查与在线更新服务。 */
public class AppUpdateService {
    private static final long CACHE_TTL_MILLIS = 60L * 60L * 1000L;

    private final AppConfig appConfig;
    private final AppVersionService versionService;
    private final ScheduledExecutorService exitExecutor =
            BoundedExecutorFactory.scheduled("self-update-exit", 1);
    private volatile String lastErrorMessage;
    private volatile long lastErrorAt;

    public AppUpdateService(AppConfig appConfig, AppVersionService versionService) {
        this.appConfig = appConfig;
        this.versionService = versionService;
    }

    public VersionStatus getVersionStatus(boolean forceRefresh) {
        ReleaseInfo latest =
                forceRefresh ? fetchAndCacheLatestRelease() : loadCachedLatestRelease();
        String current = versionService.currentVersion();
        VersionStatus status = new VersionStatus();
        status.setCurrentVersion(current);
        status.setCurrentTag(versionService.currentTag());
        status.setDeploymentMode(versionService.deploymentMode());
        status.setRepo(versionService.releaseRepo());
        status.setReleaseApiUrl(versionService.releaseApiUrl());
        status.setTagsApiUrl(versionService.tagsApiUrl());
        status.setUpdateErrorMessage(lastErrorMessage);
        status.setUpdateErrorAt(lastErrorAt);
        if (latest != null) {
            status.setLatestVersion(latest.getVersion());
            status.setLatestTag(latest.getTag());
            status.setReleaseUrl(latest.getHtmlUrl());
            status.setPublishedAt(latest.getPublishedAt());
            status.setJarAssetUrl(latest.getJarAssetUrl());
            status.setJarAssetName(latest.getJarAssetName());
            status.setVersionSource(latest.getSource());
            status.setUpdateAvailable(
                    AppVersionService.compareVersions(current, latest.getVersion()) < 0);
        }
        return status;
    }

    public String formatVersionReport(boolean forceRefresh) {
        VersionStatus status = getVersionStatus(forceRefresh);
        StringBuilder buffer = new StringBuilder();
        appendLine(buffer, "当前版本", status.getCurrentTag());
        appendLine(buffer, "部署方式", status.getDeploymentMode());
        appendLine(buffer, "发布仓库", status.getRepo());
        appendLine(buffer, "Release API", status.getReleaseApiUrl());
        appendLine(buffer, "Tags API", status.getTagsApiUrl());
        if (StrUtil.isBlank(status.getLatestTag())) {
            if (StrUtil.isNotBlank(status.getUpdateErrorMessage())) {
                appendLine(buffer, "最新版本", "检查失败");
                appendLine(buffer, "失败原因", status.getUpdateErrorMessage());
            } else {
                appendLine(buffer, "最新版本", "尚未检查或暂不可用");
            }
            return buffer.toString().trim();
        }
        appendLine(buffer, "最新版本", status.getLatestTag());
        if (StrUtil.isNotBlank(status.getVersionSource())) {
            appendLine(buffer, "版本来源", status.getVersionSource());
        }
        if (StrUtil.isNotBlank(status.getPublishedAt())) {
            appendLine(buffer, "发布时间", status.getPublishedAt());
        }
        appendLine(buffer, "更新状态", status.isUpdateAvailable() ? "可升级" : "已是最新");
        if (StrUtil.isNotBlank(status.getReleaseUrl())) {
            appendLine(buffer, "发布页", status.getReleaseUrl());
        }
        if ("docker".equals(status.getDeploymentMode())) {
            appendLine(buffer, "在线升级", "Docker 部署不支持进程内自更新，请在宿主机拉取新镜像并重建容器。");
        } else if ("jar".equals(status.getDeploymentMode()) && status.isUpdateAvailable()) {
            appendLine(buffer, "在线升级", "可执行 `/version update` 自动下载并重启到最新 jar。");
        } else if ("jar".equals(status.getDeploymentMode())) {
            appendLine(buffer, "在线升级", "当前 jar 已是最新，无需升级。");
        } else {
            appendLine(buffer, "在线升级", "当前为开发态运行，建议通过 Git/IDE 更新代码。");
        }
        return buffer.toString().trim();
    }

    public UpdateResult startUpdate() {
        VersionStatus status = getVersionStatus(true);
        if (StrUtil.isBlank(status.getLatestTag())) {
            return UpdateResult.error(
                    "无法检查最新版本：" + StrUtil.blankToDefault(status.getUpdateErrorMessage(), "未知错误"));
        }
        if (!status.isUpdateAvailable()) {
            return UpdateResult.ok("当前已是最新版本：" + status.getCurrentTag());
        }
        if ("docker".equals(status.getDeploymentMode())) {
            return UpdateResult.ok(
                    "检测到 Docker 部署，不能由进程内直接替换镜像。\n"
                            + "最新版本: "
                            + status.getLatestTag()
                            + "\n"
                            + "请在宿主机执行：\n"
                            + "docker compose pull\n"
                            + "docker compose up -d");
        }
        if (!"jar".equals(status.getDeploymentMode())) {
            return UpdateResult.ok(
                    "当前不是 jar 部署，不能执行在线升级。\n"
                            + "最新版本: "
                            + status.getLatestTag()
                            + "\n"
                            + "请通过 Git 或重新构建方式升级。");
        }
        if (versionService.isWindows()) {
            return UpdateResult.ok(
                    "Windows 下暂未启用 jar 自更新。\n"
                            + "最新版本: "
                            + status.getLatestTag()
                            + "\n"
                            + "请下载最新 jar 后手动替换。");
        }
        if (StrUtil.isBlank(status.getJarAssetUrl())) {
            return UpdateResult.error("未找到可下载的 jar 资产。当前仅检测到版本标签，尚无对应 Release 附件。");
        }

        try {
            File currentJar = versionService.currentJarFile();
            if (currentJar == null || !currentJar.isFile()) {
                return UpdateResult.error("未找到当前运行的 jar 文件，无法执行在线升级。");
            }

            File updateDir = new File(versionService.runtimeHome(), "update");
            File logsDir = new File(versionService.runtimeHome(), "logs");
            FileUtil.mkdir(updateDir);
            FileUtil.mkdir(logsDir);

            File downloadedJar =
                    new File(
                            updateDir,
                            "solon-claw-"
                                    + AppVersionService.stripLeadingV(status.getLatestTag())
                                    + ".jar.download");
            downloadAsset(status.getJarAssetUrl(), downloadedJar);

            File argsFile = new File(updateDir, "restart-args.json");
            ONode argsNode = new ONode();
            for (String arg : versionService.startupArgs()) {
                argsNode.add(arg);
            }
            FileUtil.writeUtf8String(argsNode.toJson(), argsFile);

            File updateLog = new File(logsDir, "update.log");
            List<String> command = new ArrayList<String>();
            command.add(versionService.javaExecutable());
            command.add("-cp");
            command.add(currentJar.getAbsolutePath());
            command.add(SelfUpdateLauncher.class.getName());
            command.add(currentJar.getAbsolutePath());
            command.add(downloadedJar.getAbsolutePath());
            command.add(argsFile.getAbsolutePath());
            command.add(updateLog.getAbsolutePath());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(versionService.runtimeHome());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(updateLog));
            builder.start();

            scheduleCurrentProcessExit();

            return UpdateResult.ok("已开始在线升级到 " + status.getLatestTag() + "，应用将在几秒后自动重启。");
        } catch (Exception e) {
            return UpdateResult.error("启动在线升级失败：" + e.getMessage());
        }
    }

    protected void scheduleCurrentProcessExit() {
        exitExecutor.schedule(
                new Runnable() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                },
                3L,
                TimeUnit.SECONDS);
    }

    public void shutdown() {
        exitExecutor.shutdownNow();
    }

    protected ReleaseInfo loadCachedLatestRelease() {
        File cacheFile = cacheFile();
        if (cacheFile.isFile()) {
            try {
                ONode node = ONode.ofJson(FileUtil.readUtf8String(cacheFile));
                long timestamp = node.get("timestamp").getLong(0L);
                if (System.currentTimeMillis() - timestamp < CACHE_TTL_MILLIS) {
                    clearLastError();
                    return ReleaseInfo.fromNode(node.get("release"));
                }
            } catch (Exception ignored) {
                // ignore stale cache
            }
        }
        return null;
    }

    protected ReleaseInfo fetchAndCacheLatestRelease() {
        ReleaseInfo releaseInfo = fetchLatestReleaseFromRemote();
        if (releaseInfo == null) {
            return null;
        }
        clearLastError();
        try {
            ONode node = new ONode();
            node.set("timestamp", System.currentTimeMillis());
            node.set("release", releaseInfo.toNode());
            FileUtil.mkParentDirs(cacheFile());
            FileUtil.writeUtf8String(node.toJson(), cacheFile());
        } catch (Exception ignored) {
            // ignore cache failures
        }
        return releaseInfo;
    }

    protected ReleaseInfo fetchLatestReleaseFromRemote() {
        ApiFetchResult releaseResult = executeGithubJson(versionService.releaseApiUrl());
        if (releaseResult.getStatusCode() >= 200 && releaseResult.getStatusCode() < 300) {
            ReleaseInfo releaseInfo = parseReleaseInfo(releaseResult.getBody());
            if (releaseInfo != null) {
                return releaseInfo;
            }
        }

        if (releaseResult.getStatusCode() == 404) {
            ApiFetchResult tagsResult = executeGithubJson(versionService.tagsApiUrl());
            if (tagsResult.getStatusCode() >= 200 && tagsResult.getStatusCode() < 300) {
                ReleaseInfo tagInfo = parseTagInfo(tagsResult.getBody());
                if (tagInfo != null) {
                    clearLastError();
                    return tagInfo;
                }
            }
            setLastError(buildGithubError("GitHub Tags API 请求失败", tagsResult));
            return null;
        }

        setLastError(buildGithubError("GitHub Release API 请求失败", releaseResult));
        return null;
    }

    protected void downloadAsset(String assetUrl, File target) {
        ensureTrustedUpdateAssetUrl(assetUrl);
        BoundedAttachmentIO.downloadHutoolToFile(
                assetUrl, target, 60000, BoundedAttachmentIO.UPDATE_JAR_MAX_BYTES);
    }

    private void ensureTrustedUpdateAssetUrl(String assetUrl) {
        try {
            URI uri = URI.create(assetUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || StrUtil.isBlank(host)) {
                throw new IllegalArgumentException("Update asset URL must be HTTPS");
            }
            String normalizedHost = host.toLowerCase();
            if (!("github.com".equals(normalizedHost)
                    || "api.github.com".equals(normalizedHost)
                    || "objects.githubusercontent.com".equals(normalizedHost)
                    || normalizedHost.endsWith(".githubusercontent.com"))) {
                throw new IllegalArgumentException(
                        "Update asset URL host is not trusted: " + normalizedHost);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid update asset URL");
        }
    }

    private File cacheFile() {
        return new File(versionService.runtimeHome(), ".update_check.json");
    }

    private void appendLine(StringBuilder buffer, String label, String value) {
        if (StrUtil.isBlank(label) || StrUtil.isBlank(value)) {
            return;
        }
        if (buffer.length() > 0) {
            buffer.append('\n');
        }
        buffer.append(label).append(": ").append(value);
    }

    protected ApiFetchResult executeGithubJson(String url) {
        ApiFetchResult result = new ApiFetchResult();
        result.setUrl(url);
        try {
            HttpRequest request =
                    HttpRequest.get(url)
                            .header(Header.ACCEPT, "application/vnd.github+json")
                            .header(Header.USER_AGENT, "solon-claw")
                            .timeout(5000);
            Proxy proxy = resolveProxy();
            if (proxy != null) {
                request.setProxy(proxy);
            }
            String token =
                    firstNonBlank(
                            RuntimeConfigResolver.getValue("solonclaw.integrations.github.token"),
                            RuntimeConfigResolver.getValue(
                                    "solonclaw.integrations.github.cliToken"));
            if (StrUtil.isNotBlank(token)) {
                request.header(Header.AUTHORIZATION, "Bearer " + token.trim());
            }
            HttpResponse response = request.execute();
            try {
                result.setStatusCode(response.getStatus());
                result.setBody(
                        BoundedAttachmentIO.readHutoolText(
                                response, BoundedAttachmentIO.JSON_MAX_BYTES));
            } finally {
                response.close();
            }
        } catch (Exception e) {
            result.setStatusCode(-1);
            result.setErrorMessage(
                    e.getClass().getSimpleName() + ": " + SecretRedactor.redact(e.getMessage()));
        }
        return result;
    }

    protected ReleaseInfo parseReleaseInfo(String body) {
        try {
            ONode node = ONode.ofJson(body);
            ReleaseInfo releaseInfo = new ReleaseInfo();
            releaseInfo.setTag(node.get("tag_name").getString());
            if (StrUtil.isBlank(releaseInfo.getTag())) {
                setLastError("Release API 响应缺少 tag_name");
                return null;
            }
            releaseInfo.setVersion(AppVersionService.stripLeadingV(releaseInfo.getTag()));
            releaseInfo.setHtmlUrl(node.get("html_url").getString());
            releaseInfo.setPublishedAt(node.get("published_at").getString());
            releaseInfo.setSource("release");
            ONode assets = node.get("assets");
            for (int i = 0; i < assets.size(); i++) {
                ONode asset = assets.get(i);
                String name = asset.get("name").getString();
                if (StrUtil.isBlank(name)) {
                    continue;
                }
                if (name.startsWith("solon-claw-") && name.endsWith(".jar")) {
                    releaseInfo.setJarAssetName(name);
                    releaseInfo.setJarAssetUrl(asset.get("browser_download_url").getString());
                    break;
                }
            }
            return releaseInfo;
        } catch (Exception e) {
            setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    protected ReleaseInfo parseTagInfo(String body) {
        try {
            ONode node = ONode.ofJson(body);
            if (!node.isArray() || node.size() == 0) {
                setLastError("Tags API 响应为空");
                return null;
            }
            ONode first = node.get(0);
            String tag = first.get("name").getString();
            if (StrUtil.isBlank(tag)) {
                setLastError("Tags API 响应缺少 name");
                return null;
            }
            ReleaseInfo releaseInfo = new ReleaseInfo();
            releaseInfo.setTag(tag);
            releaseInfo.setVersion(AppVersionService.stripLeadingV(tag));
            releaseInfo.setHtmlUrl("https://github.com/" + versionService.releaseRepo() + "/tags");
            releaseInfo.setPublishedAt(null);
            releaseInfo.setSource("tag");
            return releaseInfo;
        } catch (Exception e) {
            setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private String buildGithubError(String prefix, ApiFetchResult result) {
        if (result == null) {
            return prefix + "，未知错误";
        }
        if (StrUtil.isNotBlank(result.getErrorMessage())) {
            return prefix + "，" + result.getErrorMessage();
        }
        if (result.getStatusCode() > 0) {
            return prefix + "，HTTP " + result.getStatusCode();
        }
        return prefix + "，未知错误";
    }

    private Proxy resolveProxy() {
        String proxyUrl = versionService.updateProxyUrl();
        if (StrUtil.isBlank(proxyUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (StrUtil.isBlank(host) || port <= 0) {
                setLastError("更新代理地址无效: " + proxyUrl);
                return null;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (Exception e) {
            setLastError("更新代理地址解析失败: " + proxyUrl);
            return null;
        }
    }

    private void setLastError(String message) {
        this.lastErrorMessage = StrUtil.nullToEmpty(message).trim();
        this.lastErrorAt = System.currentTimeMillis();
    }

    private void clearLastError() {
        this.lastErrorMessage = null;
        this.lastErrorAt = 0L;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    public static class UpdateResult {
        private final boolean error;
        private final String message;

        private UpdateResult(boolean error, String message) {
            this.error = error;
            this.message = message;
        }

        public static UpdateResult ok(String message) {
            return new UpdateResult(false, message);
        }

        public static UpdateResult error(String message) {
            return new UpdateResult(true, message);
        }

        public boolean isError() {
            return error;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class VersionStatus {
        private String currentVersion;
        private String currentTag;
        private String latestVersion;
        private String latestTag;
        private String deploymentMode;
        private String repo;
        private String releaseUrl;
        private String releaseApiUrl;
        private String tagsApiUrl;
        private String publishedAt;
        private String jarAssetUrl;
        private String jarAssetName;
        private boolean updateAvailable;
        private String updateErrorMessage;
        private long updateErrorAt;
        private String versionSource;

        public String getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        public String getCurrentTag() {
            return currentTag;
        }

        public void setCurrentTag(String currentTag) {
            this.currentTag = currentTag;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public void setLatestVersion(String latestVersion) {
            this.latestVersion = latestVersion;
        }

        public String getLatestTag() {
            return latestTag;
        }

        public void setLatestTag(String latestTag) {
            this.latestTag = latestTag;
        }

        public String getDeploymentMode() {
            return deploymentMode;
        }

        public void setDeploymentMode(String deploymentMode) {
            this.deploymentMode = deploymentMode;
        }

        public String getRepo() {
            return repo;
        }

        public void setRepo(String repo) {
            this.repo = repo;
        }

        public String getReleaseUrl() {
            return releaseUrl;
        }

        public void setReleaseUrl(String releaseUrl) {
            this.releaseUrl = releaseUrl;
        }

        public String getReleaseApiUrl() {
            return releaseApiUrl;
        }

        public void setReleaseApiUrl(String releaseApiUrl) {
            this.releaseApiUrl = releaseApiUrl;
        }

        public String getTagsApiUrl() {
            return tagsApiUrl;
        }

        public void setTagsApiUrl(String tagsApiUrl) {
            this.tagsApiUrl = tagsApiUrl;
        }

        public String getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
        }

        public String getJarAssetUrl() {
            return jarAssetUrl;
        }

        public void setJarAssetUrl(String jarAssetUrl) {
            this.jarAssetUrl = jarAssetUrl;
        }

        public String getJarAssetName() {
            return jarAssetName;
        }

        public void setJarAssetName(String jarAssetName) {
            this.jarAssetName = jarAssetName;
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }

        public void setUpdateAvailable(boolean updateAvailable) {
            this.updateAvailable = updateAvailable;
        }

        public String getUpdateErrorMessage() {
            return updateErrorMessage;
        }

        public void setUpdateErrorMessage(String updateErrorMessage) {
            this.updateErrorMessage = updateErrorMessage;
        }

        public long getUpdateErrorAt() {
            return updateErrorAt;
        }

        public void setUpdateErrorAt(long updateErrorAt) {
            this.updateErrorAt = updateErrorAt;
        }

        public String getVersionSource() {
            return versionSource;
        }

        public void setVersionSource(String versionSource) {
            this.versionSource = versionSource;
        }
    }

    protected static class ReleaseInfo {
        private String tag;
        private String version;
        private String htmlUrl;
        private String publishedAt;
        private String jarAssetUrl;
        private String jarAssetName;
        private String source;

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public void setHtmlUrl(String htmlUrl) {
            this.htmlUrl = htmlUrl;
        }

        public String getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
        }

        public String getJarAssetUrl() {
            return jarAssetUrl;
        }

        public void setJarAssetUrl(String jarAssetUrl) {
            this.jarAssetUrl = jarAssetUrl;
        }

        public String getJarAssetName() {
            return jarAssetName;
        }

        public void setJarAssetName(String jarAssetName) {
            this.jarAssetName = jarAssetName;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public ONode toNode() {
            return new ONode()
                    .set("tag", tag)
                    .set("version", version)
                    .set("htmlUrl", htmlUrl)
                    .set("publishedAt", publishedAt)
                    .set("jarAssetUrl", jarAssetUrl)
                    .set("jarAssetName", jarAssetName)
                    .set("source", source);
        }

        public static ReleaseInfo fromNode(ONode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            ReleaseInfo releaseInfo = new ReleaseInfo();
            releaseInfo.setTag(node.get("tag").getString());
            releaseInfo.setVersion(node.get("version").getString());
            releaseInfo.setHtmlUrl(node.get("htmlUrl").getString());
            releaseInfo.setPublishedAt(node.get("publishedAt").getString());
            releaseInfo.setJarAssetUrl(node.get("jarAssetUrl").getString());
            releaseInfo.setJarAssetName(node.get("jarAssetName").getString());
            releaseInfo.setSource(node.get("source").getString());
            return releaseInfo;
        }
    }

    protected static class ApiFetchResult {
        private int statusCode;
        private String body;
        private String errorMessage;
        private String url;

        public ApiFetchResult() {}

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
