package com.jimuqu.solonclaw.gateway;

import com.jimuqu.solonclaw.util.TempTokenService;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Param;
import org.noear.solon.core.handle.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件访问控制器
 * <p>
 * 提供临时 token 访问文件的接口
 *
 * @author SolonClaw
 */
@Controller
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Inject
    private TempTokenService tempTokenService;

    /**
     * 通过临时 token 访问文件
     *
     * @param randomFileName 随机文件名
     * @param token          访问 token
     * @param ctx            HTTP 上下文
     */
    @Mapping("/api/file/{randomFileName}")
    public void accessFile(String randomFileName,
                           @Param("token") String token,
                           Context ctx) {
        try {
            // 验证 token 和文件名，获取文件路径
            String filePath = tempTokenService.verifyAndGetFilePath(token, randomFileName);

            if (filePath == null) {
                log.warn("文件访问失败：无效的 token 或文件名不匹配 (token={}, fileName={})", token, randomFileName);
                ctx.status(403);
                ctx.output("无效的访问链接");
                return;
            }

            Path path = Paths.get(filePath);

            // 检查文件是否存在
            if (!Files.exists(path)) {
                log.warn("文件不存在: {}", filePath);
                ctx.status(404);
                ctx.output("文件不存在");
                return;
            }

            // 检查文件大小
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                log.warn("文件过大: {} ({} bytes)", filePath, fileSize);
                ctx.status(403);
                ctx.output("文件过大");
                return;
            }

            // 设置 Content-Type
            String mimeType = getMimeType(path.getFileName().toString());
            ctx.contentType(mimeType);

            // 读取并返回文件内容
            byte[] fileContent = Files.readAllBytes(path);
            ctx.output(fileContent);

            log.info("文件访问成功: token={}, fileName={}, filePath={}, size={}", token, randomFileName, filePath, fileSize);

        } catch (IOException e) {
            log.error("读取文件失败", e);
            ctx.status(500);
            ctx.output("读取文件失败");
        }
    }

    /**
     * 根据文件扩展名获取 MIME 类型
     */
    private String getMimeType(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            String extension = fileName.substring(lastDot + 1).toLowerCase();
            return switch (extension) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "svg" -> "image/svg+xml";
                default -> "application/octet-stream";
            };
        }
        return "application/octet-stream";
    }
}