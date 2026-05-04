package com.jimuqu.solon.claw.support;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.io.File;
import java.io.IOException;

/** Canonical runtime path guard for file-oriented features. */
public class RuntimePathGuard {
    private final File runtimeHome;
    private final File contextDir;
    private final File skillsDir;
    private final File cacheDir;
    private final File mediaDir;
    private final File webDistDir;
    private final File projectDir;

    public RuntimePathGuard(AppConfig appConfig) {
        this.runtimeHome = canonical(new File(appConfig.getRuntime().getHome()));
        this.contextDir = canonical(new File(appConfig.getRuntime().getContextDir()));
        this.skillsDir = canonical(new File(appConfig.getRuntime().getSkillsDir()));
        this.cacheDir = canonical(new File(appConfig.getRuntime().getCacheDir()));
        this.mediaDir = canonical(new File(cacheDir, "media"));
        this.projectDir = canonical(new File(System.getProperty("user.dir")));
        this.webDistDir = canonical(new File(projectDir, "web/dist"));
    }

    public File requireAllowedToolPath(String path) {
        File file = canonical(resolve(path));
        requireUnderAny(file, projectDir, runtimeHome);
        return file;
    }

    public File requireUnderContext(File file) {
        File canonical = canonical(file);
        requireUnder(canonical, contextDir);
        return canonical;
    }

    public File requireUnderSkills(File file) {
        File canonical = canonical(file);
        requireUnder(canonical, skillsDir);
        return canonical;
    }

    public File requireUnderCache(File file) {
        File canonical = canonical(file);
        requireUnder(canonical, cacheDir);
        return canonical;
    }

    public File requireUnderMedia(File file) {
        File canonical = canonical(file);
        requireUnder(canonical, mediaDir);
        return canonical;
    }

    public File requireUnderWebDist(File file) {
        File canonical = canonical(file);
        requireUnder(canonical, webDistDir);
        return canonical;
    }

    public File mediaDir() {
        return mediaDir;
    }

    private File resolve(String path) {
        if (StrUtil.isBlank(path)) {
            throw new IllegalArgumentException("Path is required");
        }
        File file = FileUtil.file(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(projectDir, path);
    }

    private void requireUnderAny(File file, File... roots) {
        for (File root : roots) {
            if (isUnder(file, root)) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "Path is outside allowed roots: "
                        + file.getAbsolutePath()
                        + ". Allowed roots: "
                        + allowedRoots(roots));
    }

    private void requireUnder(File file, File root) {
        if (!isUnder(file, root)) {
            throw new IllegalArgumentException(
                    "Path is outside allowed root: " + file.getAbsolutePath());
        }
    }

    private boolean isUnder(File file, File root) {
        String filePath = normalize(file);
        String rootPath = normalize(root);
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private String normalize(File file) {
        String path = canonical(file).getAbsolutePath();
        if (isWindows()) {
            return path.toLowerCase(java.util.Locale.ROOT);
        }
        return path;
    }

    private boolean isWindows() {
        return File.separatorChar == '\\';
    }

    private File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid path: " + file, e);
        }
    }

    private String allowedRoots(File... roots) {
        StringBuilder buffer = new StringBuilder();
        for (File root : roots) {
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(root.getAbsolutePath());
        }
        return buffer.toString();
    }
}
