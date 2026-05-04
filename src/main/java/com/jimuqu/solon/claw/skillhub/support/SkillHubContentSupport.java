package com.jimuqu.solon.claw.skillhub.support;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Skills Hub 内容辅助。 */
public final class SkillHubContentSupport {
    private SkillHubContentSupport() {}

    public static String contentHash(File path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        if (path.isDirectory()) {
            List<File> files = FileUtil.loopFiles(path);
            files.sort((left, right) -> left.getAbsolutePath().compareTo(right.getAbsolutePath()));
            for (File file : files) {
                if (file.isDirectory()) {
                    continue;
                }
                digest.update(FileUtil.readBytes(file));
            }
        } else if (path.isFile()) {
            digest.update(FileUtil.readBytes(path));
        }
        return "sha256:"
                + cn.hutool.core.util.HexUtil.encodeHexStr(digest.digest()).substring(0, 16);
    }

    public static String bundleContentHash(SkillBundle bundle) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<String> keys = new ArrayList<String>(bundle.getFiles().keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            digest.update(key.getBytes(StandardCharsets.UTF_8));
            digest.update(
                    StrUtil.nullToEmpty(bundle.getFiles().get(key))
                            .getBytes(StandardCharsets.UTF_8));
        }
        return "sha256:"
                + cn.hutool.core.util.HexUtil.encodeHexStr(digest.digest()).substring(0, 16);
    }

    public static void writeBundle(File targetDir, SkillBundle bundle) {
        FileUtil.mkdir(targetDir);
        for (java.util.Map.Entry<String, String> entry : bundle.getFiles().entrySet()) {
            File target = FileUtil.file(targetDir, entry.getKey().replace('/', File.separatorChar));
            FileUtil.mkParentDirs(target);
            FileUtil.writeUtf8String(StrUtil.nullToEmpty(entry.getValue()), target);
        }
    }
}
