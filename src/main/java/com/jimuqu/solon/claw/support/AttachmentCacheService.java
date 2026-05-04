package com.jimuqu.solon.claw.support;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.MessageAttachment;
import com.jimuqu.solon.claw.support.constants.RuntimePathConstants;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/** 附件缓存服务。 */
public class AttachmentCacheService {
    private static final long MAX_CACHE_BYTES = 32L * 1024L * 1024L;

    private final File runtimeHome;
    private final File cacheRoot;

    public AttachmentCacheService(AppConfig appConfig) {
        String runtimeHomeValue = null;
        String cacheDirValue = null;
        if (appConfig != null && appConfig.getRuntime() != null) {
            runtimeHomeValue = appConfig.getRuntime().getHome();
            cacheDirValue = appConfig.getRuntime().getCacheDir();
        }
        this.runtimeHome =
                FileUtil.file(StrUtil.blankToDefault(runtimeHomeValue, RuntimePathConstants.RUNTIME_HOME))
                        .getAbsoluteFile();
        File cacheDir =
                FileUtil.file(
                                StrUtil.blankToDefault(
                                        cacheDirValue,
                                        new File(runtimeHome, RuntimePathConstants.CACHE_DIR_NAME)
                                                .getPath()))
                        .getAbsoluteFile();
        this.cacheRoot = new File(cacheDir, "media");
    }

    /** 将原始字节落盘并返回附件模型。 */
    public MessageAttachment cacheBytes(
            PlatformType platform,
            String kind,
            String originalName,
            String mimeType,
            boolean fromQuote,
            String transcribedText,
            byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Attachment bytes are required");
        }
        if (data.length > MAX_CACHE_BYTES) {
            throw new IllegalStateException("Attachment too large: " + data.length);
        }

        File target = new File(platformDir(platform), prefixedName(originalName));
        FileUtil.mkParentDirs(target);
        FileUtil.writeBytes(data, target);

        MessageAttachment attachment = new MessageAttachment();
        attachment.setKind(normalizeKind(kind, originalName, mimeType));
        attachment.setLocalPath(target.getAbsolutePath());
        attachment.setOriginalName(safeName(originalName));
        attachment.setMimeType(normalizeMimeType(mimeType, originalName));
        attachment.setFromQuote(fromQuote);
        attachment.setTranscribedText(StrUtil.nullToEmpty(transcribedText).trim());
        return attachment;
    }

    /** 由现有本地文件构造附件模型。 */
    public MessageAttachment fromLocalFile(
            PlatformType platform,
            File file,
            String explicitKind,
            boolean fromQuote,
            String transcribedText) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Attachment file does not exist: " + file);
        }
        File canonical = FileUtil.file(file).getAbsoluteFile();
        FileUtil.file(platformDir(platform)).getAbsoluteFile();
        if (!isUnderCacheRoot(canonical)) {
            throw new IllegalArgumentException("Attachment file is outside runtime cache: " + file);
        }

        MessageAttachment attachment = new MessageAttachment();
        attachment.setKind(normalizeKind(explicitKind, canonical.getName(), null));
        attachment.setLocalPath(canonical.getAbsolutePath());
        attachment.setOriginalName(safeName(canonical.getName()));
        attachment.setMimeType(normalizeMimeType(null, canonical.getName()));
        attachment.setFromQuote(fromQuote);
        attachment.setTranscribedText(StrUtil.nullToEmpty(transcribedText).trim());
        return attachment;
    }

    public MessageAttachment fromMediaCacheFile(
            PlatformType platform,
            File file,
            String explicitKind,
            boolean fromQuote,
            String transcribedText) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Attachment file does not exist: " + file);
        }
        File canonical = FileUtil.file(file).getAbsoluteFile();
        if (!isUnderMediaRoot(canonical)) {
            throw new IllegalArgumentException("Attachment file is outside media cache: " + file);
        }
        return fromLocalFile(platform, canonical, explicitKind, fromQuote, transcribedText);
    }

    /**
     * 发送工具引用 runtime 根目录下生成的附件时，先导入媒体缓存再发送。
     *
     * <p>只允许 runtime 根目录的一层生成物，避免把 config/data/logs 等运行时内部文件直接作为附件外发。
     */
    public MessageAttachment fromLocalOrGeneratedFile(
            PlatformType platform,
            File file,
            String explicitKind,
            boolean fromQuote,
            String transcribedText) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Attachment file does not exist: " + file);
        }
        File canonical = FileUtil.file(file).getAbsoluteFile();
        if (isUnderCacheRoot(canonical)) {
            return fromLocalFile(platform, canonical, explicitKind, fromQuote, transcribedText);
        }
        if (!isSafeRuntimeGeneratedFile(canonical)) {
            throw new IllegalArgumentException("Attachment file is outside runtime cache: " + file);
        }
        if (canonical.length() > MAX_CACHE_BYTES) {
            throw new IllegalStateException("Attachment too large: " + canonical.length());
        }

        File target = new File(platformDir(platform), prefixedName(canonical.getName()));
        FileUtil.mkParentDirs(target);
        try {
            Files.copy(canonical.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to cache generated attachment: " + canonical.getAbsolutePath(), e);
        }
        return fromLocalFile(platform, target, explicitKind, fromQuote, transcribedText);
    }

    public File platformDir(PlatformType platform) {
        return new File(
                cacheRoot,
                String.valueOf(platform == null ? PlatformType.MEMORY : platform)
                        .toLowerCase(Locale.ROOT));
    }

    public static String normalizeKind(String kind, String name, String mimeType) {
        String normalized = StrUtil.nullToEmpty(kind).trim().toLowerCase(Locale.ROOT);
        if ("image".equals(normalized)
                || "file".equals(normalized)
                || "video".equals(normalized)
                || "voice".equals(normalized)) {
            return normalized;
        }

        String mime = StrUtil.nullToEmpty(mimeType).toLowerCase(Locale.ROOT);
        String ext = extension(name);
        if (mime.startsWith("image/")
                || matches(ext, ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp")) {
            return "image";
        }
        if (mime.startsWith("video/")
                || matches(ext, ".mp4", ".mov", ".avi", ".mkv", ".webm", ".3gp", ".m4v")) {
            return "video";
        }
        if (mime.startsWith("audio/")
                || ".silk".equals(ext)
                || matches(ext, ".ogg", ".opus", ".mp3", ".wav", ".m4a", ".aac", ".flac", ".amr")) {
            return "voice";
        }
        return "file";
    }

    public static String normalizeMimeType(String mimeType, String name) {
        String normalized = StrUtil.nullToEmpty(mimeType).trim();
        if (normalized.length() > 0) {
            return normalized;
        }

        String ext = extension(name);
        if (matches(ext, ".png")) {
            return "image/png";
        }
        if (matches(ext, ".jpg", ".jpeg")) {
            return "image/jpeg";
        }
        if (matches(ext, ".gif")) {
            return "image/gif";
        }
        if (matches(ext, ".webp")) {
            return "image/webp";
        }
        if (matches(ext, ".bmp")) {
            return "image/bmp";
        }
        if (matches(ext, ".mp4", ".m4v")) {
            return "video/mp4";
        }
        if (matches(ext, ".mov")) {
            return "video/quicktime";
        }
        if (matches(ext, ".avi")) {
            return "video/x-msvideo";
        }
        if (matches(ext, ".mkv")) {
            return "video/x-matroska";
        }
        if (matches(ext, ".webm")) {
            return "video/webm";
        }
        if (matches(ext, ".ogg", ".opus")) {
            return "audio/ogg";
        }
        if (matches(ext, ".mp3")) {
            return "audio/mpeg";
        }
        if (matches(ext, ".wav")) {
            return "audio/wav";
        }
        if (matches(ext, ".m4a")) {
            return "audio/mp4";
        }
        if (matches(ext, ".aac")) {
            return "audio/aac";
        }
        if (matches(ext, ".flac")) {
            return "audio/flac";
        }
        if (matches(ext, ".amr")) {
            return "audio/amr";
        }
        if (matches(ext, ".silk")) {
            return "audio/silk";
        }
        if (matches(ext, ".pdf")) {
            return "application/pdf";
        }
        if (matches(ext, ".txt")) {
            return "text/plain";
        }
        if (matches(ext, ".md")) {
            return "text/markdown";
        }
        if (matches(ext, ".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (matches(ext, ".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (matches(ext, ".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        return "application/octet-stream";
    }

    private String prefixedName(String originalName) {
        return UUID.randomUUID().toString().replace("-", "") + "_" + safeName(originalName);
    }

    private static String safeName(String originalName) {
        String value =
                StrUtil.blankToDefault(originalName, "attachment.bin")
                        .replace("\\", "_")
                        .replace("/", "_")
                        .replace(":", "_")
                        .replace("*", "_")
                        .replace("?", "_")
                        .replace("\"", "_")
                        .replace("<", "_")
                        .replace(">", "_")
                        .replace("|", "_");
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            cleaned.append(Character.isISOControl(ch) ? '_' : ch);
        }
        value = cleaned.toString().trim();
        if (value.length() > 120) {
            value = value.substring(0, 120);
        }
        return value.length() == 0 ? "attachment.bin" : value;
    }

    private boolean isUnderCacheRoot(File file) {
        File runtimeCacheRoot =
                cacheRoot.getParentFile() == null ? cacheRoot : cacheRoot.getParentFile();
        return isUnder(file, runtimeCacheRoot);
    }

    private boolean isUnderMediaRoot(File file) {
        return isUnder(file, cacheRoot);
    }

    private boolean isSafeRuntimeGeneratedFile(File file) {
        if (!isUnder(file, runtimeHome)) {
            return false;
        }
        try {
            File parent = file.getCanonicalFile().getParentFile();
            if (parent == null || !parent.equals(runtimeHome.getCanonicalFile())) {
                return false;
            }
        } catch (Exception e) {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent == null || !parent.equals(runtimeHome.getAbsoluteFile())) {
                return false;
            }
        }
        String ext = extension(file.getName());
        return matches(
                ext,
                ".pdf",
                ".docx",
                ".xlsx",
                ".pptx",
                ".png",
                ".jpg",
                ".jpeg",
                ".gif",
                ".webp",
                ".bmp",
                ".mp4",
                ".mov",
                ".avi",
                ".mkv",
                ".webm",
                ".3gp",
                ".m4v",
                ".silk",
                ".ogg",
                ".opus",
                ".mp3",
                ".wav",
                ".m4a",
                ".aac",
                ".flac",
                ".amr");
    }

    private boolean isUnder(File file, File root) {
        try {
            if (isUnderPath(file.getCanonicalFile(), root.getCanonicalFile())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return isUnderPath(file.getAbsoluteFile(), root.getAbsoluteFile());
    }

    private boolean isUnderPath(File file, File root) {
        String rootPath = root.toPath().toAbsolutePath().normalize().toString();
        String filePath = file.toPath().toAbsolutePath().normalize().toString();
        if (File.separatorChar == '\\') {
            rootPath = rootPath.toLowerCase(Locale.ROOT);
            filePath = filePath.toLowerCase(Locale.ROOT);
        }
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private static String extension(String name) {
        String value = StrUtil.nullToEmpty(name).trim().toLowerCase(Locale.ROOT);
        int index = value.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return value.substring(index);
    }

    private static boolean matches(String ext, String... values) {
        for (String value : values) {
            if (value.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }
}
