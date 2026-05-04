package com.jimuqu.solon.claw.skillhub.support;

import cn.hutool.core.io.FileUtil;
import java.io.File;

/** Skills Hub 路径辅助。 */
public final class SkillHubPathSupport {
    private SkillHubPathSupport() {}

    public static File hubDir(File skillsDir) {
        return FileUtil.file(skillsDir, ".hub");
    }

    public static File quarantineDir(File skillsDir) {
        return FileUtil.file(hubDir(skillsDir), "quarantine");
    }

    public static File importedDir(File skillsDir) {
        return FileUtil.file(hubDir(skillsDir), "imported");
    }

    public static File indexCacheDir(File skillsDir) {
        return FileUtil.file(hubDir(skillsDir), "index-cache");
    }

    public static File lockFile(File skillsDir) {
        return FileUtil.file(hubDir(skillsDir), "lock.json");
    }

    public static File tapsFile(File skillsDir) {
        return FileUtil.file(hubDir(skillsDir), "taps.json");
    }

    public static File auditLog(File skillsDir) {
        return FileUtil.file(hubDir(skillsDir), "audit.log");
    }

    public static void ensureHubDirs(File skillsDir) {
        FileUtil.mkdir(hubDir(skillsDir));
        FileUtil.mkdir(quarantineDir(skillsDir));
        FileUtil.mkdir(importedDir(skillsDir));
        FileUtil.mkdir(indexCacheDir(skillsDir));
    }
}
