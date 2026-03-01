package com.jimuqu.solonclaw.util;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件服务
 * <p>
 * 处理文件读取、临时 token 生成等功能
 *
 * @author SolonClaw
 */
@Component
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * 支持的图片格式
     */
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>();
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Inject
    private TempTokenService tempTokenService;

    static {
        IMAGE_EXTENSIONS.add("png");
        IMAGE_EXTENSIONS.add("jpg");
        IMAGE_EXTENSIONS.add("jpeg");
        IMAGE_EXTENSIONS.add("gif");
        IMAGE_EXTENSIONS.add("webp");
        IMAGE_EXTENSIONS.add("svg");
    }

    /**
     * 文件路径正则表达式
     */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(/[^/\\s\"'`<>]+\\.(png|jpg|jpeg|gif|webp|svg|PNG|JPG|JPEG|GIF|WEBP|SVG))|"
            + "(~?/[a-zA-Z0-9_\\-./]+\\.(png|jpg|jpeg|gif|webp|svg|PNG|JPG|JPEG|GIF|WEBP|SVG))"
    );

    /**
     * 从文本中提取所有图片文件路径
     *
     * @param text 文本内容
     * @return 文件路径列表
     */
    public java.util.List<String> extractImagePaths(String text) {
        if (text == null || text.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        java.util.List<String> paths = new java.util.ArrayList<>();
        Matcher matcher = FILE_PATH_PATTERN.matcher(text);

        while (matcher.find()) {
            // 尝试所有分组，找到非 null 的那个
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null && !group.isEmpty()) {
                    // 过滤掉扩展名分组（只获取文件路径）
                    if (!group.matches("^(png|jpg|jpeg|gif|webp|svg|PNG|JPG|JPEG|GIF|WEBP|SVG)$")) {
                        paths.add(group);
                        break; // 找到文件路径就跳出
                    }
                }
            }
        }

        return paths;
    }

    /**
     * 检查文本中是否包含图片文件路径
     *
     * @param text 文本内容
     * @return 是否包含图片路径
     */
    public boolean containsImagePath(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return !extractImagePaths(text).isEmpty();
    }

    /**
     * 从文本中提取第一个图片文件路径
     *
     * @param text 文本内容
     * @return 文件路径，如果未找到返回 null
     */
    public String extractImagePath(String text) {
        java.util.List<String> paths = extractImagePaths(text);
        return paths.isEmpty() ? null : paths.get(0);
    }

    /**
     * 为图片文件生成临时访问链接
     *
     * @param filePath    文件路径
     * @param validSeconds 有效时间（秒）
     * @return 临时访问链接
     */
    public String generateTempAccessUrl(String filePath, int validSeconds) {
        try {
            // 检查文件是否存在
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.warn("文件不存在，无法生成访问链接: {}", filePath);
                return null;
            }

            // 检查是否为支持的图片格式
            String fileName = path.getFileName().toString();
            String extension = getFileExtension(fileName);
            if (!IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
                log.warn("不支持的文件格式: {}", extension);
                return null;
            }

            // 生成临时 token（使用秒数），返回 TokenResult
            TempTokenService.TokenResult tokenResult = tempTokenService.generateToken(filePath, validSeconds);

            // 构建访问 URL：/api/file/{randomFileName}?token=xxx
            String accessUrl = String.format("/api/file/%s?token=%s",
                    tokenResult.getRandomFileName(),
                    tokenResult.getToken());

            log.info("生成临时访问链接: filePath={}, validSeconds={}, url={}", filePath, validSeconds, accessUrl);

            return accessUrl;

        } catch (Exception e) {
            log.error("生成临时访问链接失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 处理响应内容，将图片路径转换为临时访问链接
     *
     * @param content 响应内容
     * @return 处理后的内容，包含 Markdown 图片语法
     */
    public String processImagesInContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        try {
            // 提取所有图片路径
            java.util.List<String> imagePaths = extractImagePaths(content);

            if (imagePaths.isEmpty()) {
                return content; // 没有找到图片路径，返回原内容
            }

            // 去重（避免同一个路径被替换多次）
            java.util.Set<String> uniquePaths = new java.util.LinkedHashSet<>(imagePaths);

            // 使用数组包装 result，以便在 lambda 中修改
            final String[] resultHolder = {content};

            // 处理每个图片路径（从长到短排序，避免替换问题）
            uniquePaths.stream()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .forEach(imagePath -> {
                        // 生成临时访问链接（300 秒 = 5 分钟有效期）
                        String accessUrl = generateTempAccessUrl(imagePath, 300);

                        if (accessUrl != null) {
                            // 转换成功，替换为临时访问链接
                            resultHolder[0] = resultHolder[0].replace(imagePath, accessUrl);
                            log.info("已将图片路径转换为临时访问链接: {} -> {}", imagePath, accessUrl);
                        }
                    });

            return resultHolder[0];

        } catch (Exception e) {
            log.error("处理图片时出错，返回原内容", e);
            return content;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * 根据文件扩展名获取 MIME 类型
     */
    private String getMimeType(String extension) {
        return switch (extension.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "image/png";
        };
    }
}
