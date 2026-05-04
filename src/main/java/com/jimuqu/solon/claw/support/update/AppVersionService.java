package com.jimuqu.solon.claw.support.update;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.SolonClawApp;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 应用版本与部署形态识别服务。 */
public class AppVersionService {
    private static final String DEFAULT_REPO = "chengliang4810/solon-claw";

    private final AppConfig appConfig;

    public AppVersionService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public String currentVersion() {
        Package pkg = SolonClawApp.class.getPackage();
        if (pkg != null && StrUtil.isNotBlank(pkg.getImplementationVersion())) {
            return pkg.getImplementationVersion().trim();
        }

        String pomVersion = loadPomPropertiesVersion();
        if (StrUtil.isNotBlank(pomVersion)) {
            return pomVersion;
        }

        String projectVersion = readPomXmlVersion();
        return StrUtil.blankToDefault(projectVersion, "0.0.1");
    }

    public String currentTag() {
        return "v" + stripLeadingV(currentVersion());
    }

    public String releaseRepo() {
        String override = configValue("solonclaw.update.repo");
        return StrUtil.blankToDefault(override, DEFAULT_REPO).trim();
    }

    public String releaseApiUrl() {
        String override = configValue("solonclaw.update.releaseApiUrl");
        if (StrUtil.isNotBlank(override)) {
            return override.trim();
        }
        return "https://api.github.com/repos/" + releaseRepo() + "/releases/latest";
    }

    public String tagsApiUrl() {
        String override = configValue("solonclaw.update.tagsApiUrl");
        if (StrUtil.isNotBlank(override)) {
            return override.trim();
        }
        return "https://api.github.com/repos/" + releaseRepo() + "/tags?per_page=5";
    }

    public String updateProxyUrl() {
        String override = configValue("solonclaw.update.httpProxy");
        return StrUtil.nullToEmpty(override).trim();
    }

    public String deploymentMode() {
        if (isDocker()) {
            return "docker";
        }
        File codeSource = currentCodeSourceFile();
        if (codeSource != null && codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
            return "jar";
        }
        return "dev";
    }

    public boolean isDocker() {
        if (new File("/.dockerenv").exists()) {
            return true;
        }
        String cgroup =
                firstNonBlank(
                        readFileQuietly("/proc/1/cgroup"), readFileQuietly("/proc/self/cgroup"));
        return cgroup != null
                && (cgroup.contains("docker")
                        || cgroup.contains("kubepods")
                        || cgroup.contains("containerd"));
    }

    public boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public File currentCodeSourceFile() {
        try {
            URL location = SolonClawApp.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            String path = URLDecoder.decode(location.getPath(), "UTF-8");
            return new File(path).getAbsoluteFile();
        } catch (Exception e) {
            return null;
        }
    }

    public File currentJarFile() {
        File file = currentCodeSourceFile();
        if (file != null && file.isFile() && file.getName().endsWith(".jar")) {
            return file;
        }
        return null;
    }

    public String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        String executable = isWindows() ? "java.exe" : "java";
        return new File(new File(javaHome, "bin"), executable).getAbsolutePath();
    }

    public String[] startupArgs() {
        return SolonClawApp.startupArgs();
    }

    public File runtimeHome() {
        return new File(appConfig.getRuntime().getHome()).getAbsoluteFile();
    }

    public static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int size = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < size; i++) {
            int l = i < leftParts.length ? parseInt(leftParts[i]) : 0;
            int r = i < rightParts.length ? parseInt(rightParts[i]) : 0;
            if (l != r) {
                return l < r ? -1 : 1;
            }
        }
        return 0;
    }

    public static String normalizeVersion(String value) {
        String normalized = stripLeadingV(value);
        int dash = normalized.indexOf('-');
        if (dash >= 0) {
            normalized = normalized.substring(0, dash);
        }
        return normalized.trim();
    }

    public static String stripLeadingV(String value) {
        String normalized = StrUtil.nullToEmpty(value).trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private String loadPomPropertiesVersion() {
        InputStream inputStream = null;
        try {
            inputStream =
                    SolonClawApp.class
                            .getClassLoader()
                            .getResourceAsStream(
                                    "META-INF/maven/com.jimuqu.solon.claw/solon-claw/pom.properties");
            if (inputStream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty("version");
        } catch (Exception e) {
            return null;
        } finally {
            IoUtil.close(inputStream);
        }
    }

    private String readPomXmlVersion() {
        File pomFile = new File(System.getProperty("user.dir"), "pom.xml");
        if (!pomFile.isFile()) {
            return null;
        }
        String content = cn.hutool.core.io.FileUtil.readUtf8String(pomFile);
        Matcher matcher = Pattern.compile("<version>([^<]+)</version>").matcher(content);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String readFileQuietly(String path) {
        try {
            return cn.hutool.core.io.FileUtil.readUtf8String(new File(path));
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
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

    private String configValue(String key) {
        if (appConfig != null
                && appConfig.getRuntime() != null
                && StrUtil.isNotBlank(appConfig.getRuntime().getHome())) {
            return RuntimeConfigResolver.initialize(appConfig.getRuntime().getHome()).get(key);
        }
        return RuntimeConfigResolver.getValue(key);
    }
}
