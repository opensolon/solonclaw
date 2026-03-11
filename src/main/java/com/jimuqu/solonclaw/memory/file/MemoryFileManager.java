package com.jimuqu.solonclaw.memory.file;

import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆文件管理服务
 * <p>
 * 负责管理每日笔记和长期记忆的读写操作
 *
 * @author SolonClaw
 */
@Component
public class MemoryFileManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryFileManager.class);

    /**
     * 每日笔记目录名
     */
    private static final String MEMORY_DIR = "memory";

    /**
     * 长期记忆文件名
     */
    private static final String LONG_TERM_MEMORY_FILE = "MEMORY.md";

    @Inject(required = false)
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    @Inject
    private MemoryFileConfig config;

    /**
     * 获取记忆目录路径
     */
    private Path getMemoryDir() {
        if (workspaceInfo == null) {
            log.warn("WorkspaceInfo 未注入，使用默认路径");
            return Path.of("./workspace/memory");
        }
        return workspaceInfo.workspace().resolve(MEMORY_DIR);
    }

    /**
     * 获取长期记忆文件路径
     */
    private Path getLongTermMemoryPath() {
        if (workspaceInfo == null) {
            return Path.of("./workspace/MEMORY.md");
        }
        return workspaceInfo.workspace().resolve(LONG_TERM_MEMORY_FILE);
    }

    /**
     * 初始化记忆目录
     */
    private void initMemoryDir() {
        try {
            Path memoryDir = getMemoryDir();
            if (!Files.exists(memoryDir)) {
                Files.createDirectories(memoryDir);
                log.info("创建记忆目录: {}", memoryDir);
            }
        } catch (IOException e) {
            log.error("创建记忆目录失败", e);
        }
    }

    /**
     * 获取今日笔记文件路径
     */
    private Path getTodayNotePath() {
        return getNotePath(LocalDate.now());
    }

    /**
     * 获取昨日笔记文件路径
     */
    private Path getYesterdayNotePath() {
        return getNotePath(LocalDate.now().minusDays(1));
    }

    /**
     * 获取指定日期的笔记文件路径
     */
    private Path getNotePath(LocalDate date) {
        String fileName = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";
        return getMemoryDir().resolve(fileName);
    }

    /**
     * 读取今日笔记
     *
     * @return 笔记内容，如果不存在返回空字符串
     */
    public synchronized String readTodayNote() {
        return readNote(LocalDate.now());
    }

    /**
     * 读取昨日笔记
     *
     * @return 笔记内容，如果不存在返回空字符串
     */
    public synchronized String readYesterdayNote() {
        return readNote(LocalDate.now().minusDays(1));
    }

    /**
     * 读取指定日期的笔记
     */
    private String readNote(LocalDate date) {
        initMemoryDir();
        Path notePath = getNotePath(date);

        if (!Files.exists(notePath)) {
            log.debug("笔记文件不存在: {}", notePath);
            return "";
        }

        try {
            String content = Files.readString(notePath);
            log.debug("读取笔记: {} - {} 字符", notePath.getFileName(), content.length());
            return content;
        } catch (IOException e) {
            log.error("读取笔记失败: {}", notePath, e);
            return "";
        }
    }

    /**
     * 追加内容到今日笔记
     *
     * @param content 要追加的内容
     * @return 操作结果
     */
    public synchronized String appendToTodayNote(String content) {
        return appendToNote(LocalDate.now(), content);
    }

    /**
     * 追加内容到指定日期的笔记
     */
    private synchronized String appendToNote(LocalDate date, String content) {
        initMemoryDir();
        Path notePath = getNotePath(date);

        try {
            String existingContent = "";
            if (Files.exists(notePath)) {
                existingContent = Files.readString(notePath);
            }

            // 检查大小限制
            if (config != null && config.getMaxNoteSize() > 0) {
                if (existingContent.length() + content.length() > config.getMaxNoteSize()) {
                    log.warn("笔记大小超过限制: {} > {}",
                        existingContent.length() + content.length(),
                        config.getMaxNoteSize());
                    return "笔记大小超过限制";
                }
            }

            // 构建新内容
            String newContent = buildNoteContent(existingContent, content);
            Files.writeString(notePath, newContent);
            log.info("追加笔记成功: {} - {} 字符", notePath.getFileName(), content.length());
            return "追加成功";
        } catch (IOException e) {
            log.error("追加笔记失败: {}", notePath, e);
            return "追加失败: " + e.getMessage();
        }
    }

    /**
     * 构建笔记内容
     */
    private String buildNoteContent(String existingContent, String newEntry) {
        StringBuilder sb = new StringBuilder();

        // 如果是空文件，写入标题
        if (existingContent.isEmpty()) {
            LocalDate today = LocalDate.now();
            String title = String.format("# 每日笔记 - %d年%02d月%02d日\n\n> 本文件记录当日发生的重要事件和决策\n",
                today.getYear(), today.getMonthValue(), today.getDayOfMonth());
            sb.append(title);
        } else {
            sb.append(existingContent);
            if (!existingContent.endsWith("\n")) {
                sb.append("\n");
            }
        }

        // 添加时间戳和内容
        String timestamp = LocalDate.now().atTime(java.time.LocalTime.now())
            .format(DateTimeFormatter.ofPattern("HH:mm"));
        sb.append("### ").append(timestamp).append("\n\n");
        sb.append(newEntry).append("\n\n");

        return sb.toString();
    }

    /**
     * 读取长期记忆
     *
     * @return 长期记忆内容，如果不存在返回空字符串
     */
    public synchronized String readLongTermMemory() {
        initMemoryDir();
        Path memoryPath = getLongTermMemoryPath();

        if (!Files.exists(memoryPath)) {
            log.debug("长期记忆文件不存在: {}", memoryPath);
            return "";
        }

        try {
            String content = Files.readString(memoryPath);
            log.debug("读取长期记忆: {} 字符", content.length());
            return content;
        } catch (IOException e) {
            log.error("读取长期记忆失败", e);
            return "";
        }
    }

    /**
     * 追加内容到长期记忆的指定章节
     *
     * @param section 章节名称
     * @param content 要追加的内容
     * @return 操作结果
     */
    public synchronized String appendToLongTermMemory(String section, String content) {
        initMemoryDir();
        Path memoryPath = getLongTermMemoryPath();

        try {
            String existingContent = "";
            if (Files.exists(memoryPath)) {
                existingContent = Files.readString(memoryPath);
            }

            // 构建新内容
            String newContent = buildLongTermMemoryContent(existingContent, section, content);
            Files.writeString(memoryPath, newContent);
            log.info("追加长期记忆成功: section={}, contentLength={}", section, content.length());
            return "追加成功";
        } catch (IOException e) {
            log.error("追加长期记忆失败", e);
            return "追加失败: " + e.getMessage();
        }
    }

    /**
     * 构建长期记忆内容
     */
    private String buildLongTermMemoryContent(String existingContent, String section, String newEntry) {
        StringBuilder sb = new StringBuilder();

        // 如果是空文件，写入标题
        if (existingContent.isEmpty()) {
            sb.append("# 长期记忆\n\n");
            sb.append("> 本文件存储经过提炼的精华记忆\n\n");
            sb.append("---\n\n");
        } else {
            sb.append(existingContent);
            if (!existingContent.endsWith("\n")) {
                sb.append("\n");
            }
        }

        // 添加章节标题和新内容
        sb.append("## ").append(section).append("\n\n");
        sb.append(newEntry).append("\n\n");

        return sb.toString();
    }

    /**
     * 更新长期记忆的全部内容
     *
     * @param content 新的长期记忆内容
     * @return 操作结果
     */
    public synchronized String updateLongTermMemory(String content) {
        initMemoryDir();
        Path memoryPath = getLongTermMemoryPath();

        try {
            Files.writeString(memoryPath, content);
            log.info("更新长期记忆成功: {} 字符", content.length());
            return "更新成功";
        } catch (IOException e) {
            log.error("更新长期记忆失败", e);
            return "更新失败: " + e.getMessage();
        }
    }

    /**
     * 清理过期笔记
     *
     * @param retentionDays 保留天数
     * @return 清理结果
     */
    public synchronized String cleanupOldNotes(int retentionDays) {
        initMemoryDir();
        Path memoryDir = getMemoryDir();

        if (!Files.exists(memoryDir)) {
            log.debug("记忆目录不存在，无需清理");
            return "无需清理";
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
        int deletedCount = 0;

        try {
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(memoryDir)) {
                stream.forEach(files::add);
            }

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                // 跳过非 md 文件
                if (!fileName.endsWith(".md")) {
                    continue;
                }

                // 解析日期
                try {
                    String dateStr = fileName.replace(".md", "");
                    LocalDate fileDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                    // 如果日期早于截止日期，删除文件
                    if (fileDate.isBefore(cutoffDate)) {
                        Files.deleteIfExists(file);
                        deletedCount++;
                        log.info("删除过期笔记: {}", fileName);
                    }
                } catch (Exception e) {
                    log.warn("解析文件名失败，跳过: {}", fileName);
                }
            }

            log.info("清理过期笔记完成: 删除 {} 个文件", deletedCount);
            return "清理完成，删除 " + deletedCount + " 个文件";
        } catch (IOException e) {
            log.error("清理过期笔记失败", e);
            return "清理失败: " + e.getMessage();
        }
    }

    /**
     * 清理过期笔记（使用配置中的保留天数）
     *
     * @return 清理结果
     */
    public synchronized String cleanupOldNotes() {
        int retainDays = config != null ? config.getRetainDays() : 30;
        return cleanupOldNotes(retainDays);
    }

    /**
     * 分块读取文件内容
     * <p>
     * 使用 BufferedReader 按行读取，避免一次性加载大文件导致性能问题
     *
     * @param path      文件路径
     * @param startLine 起始行（从0开始）
     * @param maxLines  最大读取行数
     * @return 读取的内容
     */
    public synchronized String readFileBlock(Path path, int startLine, int maxLines) {
        if (!Files.exists(path)) {
            log.debug("文件不存在: {}", path);
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path)))) {
            List<String> lines = new ArrayList<>();
            int currentLine = 0;
            int readLines = 0;
            String line;

            while ((line = reader.readLine()) != null && readLines < maxLines) {
                if (currentLine >= startLine) {
                    lines.add(line);
                    readLines++;
                }
                currentLine++;
            }

            String content = String.join("\n", lines);
            log.debug("分块读取文件: {} - 行 {} 到 {}, 共 {} 行, {} 字符",
                    path.getFileName(), startLine, startLine + readLines - 1, readLines, content.length());
            return content;
        } catch (IOException e) {
            log.error("分块读取文件失败: {}", path, e);
            return "";
        }
    }

    /**
     * 读取长期记忆的前N行（便捷方法）
     *
     * @param maxLines 最大读取行数
     * @return 读取的内容
     */
    public synchronized String readLongTermMemoryPreview(int maxLines) {
        initMemoryDir();
        Path memoryPath = getLongTermMemoryPath();
        return readFileBlock(memoryPath, 0, maxLines);
    }

    /**
     * 执行备份
     * <p>
     * 使用原子操作：先写临时文件再重命名，防止数据损坏
     *
     * @return 备份结果
     */
    public synchronized String backup() {
        if (config == null || !config.isBackupEnabled()) {
            log.debug("备份功能未启用");
            return "备份功能未启用";
        }

        initMemoryDir();
        Path memoryPath = getLongTermMemoryPath();

        if (!Files.exists(memoryPath)) {
            log.debug("长期记忆文件不存在，无需备份");
            return "无需备份";
        }

        try {
            // 创建备份目录
            Path backupDirPath = getBackupDirPath();
            if (!Files.exists(backupDirPath)) {
                Files.createDirectories(backupDirPath);
                log.info("创建备份目录: {}", backupDirPath);
            }

            // 生成备份文件名：MEMORY_2026-03-12_14-30-00.bak
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupFileName = "MEMORY_" + timestamp + ".bak";
            Path backupPath = backupDirPath.resolve(backupFileName);

            // 原子操作：先写临时文件再重命名
            Path tempPath = backupDirPath.resolve(backupFileName + ".tmp");
            Files.copy(memoryPath, tempPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("备份长期记忆成功: {}", backupPath.getFileName());

            // 清理过期备份
            int maxBackups = config.getMaxBackups();
            cleanupOldBackups(maxBackups);

            return "备份成功: " + backupPath.getFileName();
        } catch (IOException e) {
            log.error("备份长期记忆失败", e);
            return "备份失败: " + e.getMessage();
        }
    }

    /**
     * 清理过期备份
     * <p>
     * 按修改时间排序，保留最新的 N 个备份，删除其他备份文件
     *
     * @param maxBackups 最大备份数量
     * @return 清理结果
     */
    public synchronized String cleanupOldBackups(int maxBackups) {
        Path backupDirPath = getBackupDirPath();

        if (!Files.exists(backupDirPath)) {
            log.debug("备份目录不存在，无需清理");
            return "无需清理";
        }

        try {
            // 获取所有备份文件并按修改时间排序（最新的在前）
            List<Path> backupFiles;
            try (var stream = Files.list(backupDirPath)) {
                backupFiles = stream
                        .filter(path -> path.getFileName().toString().startsWith("MEMORY_") &&
                                path.getFileName().toString().endsWith(".bak"))
                        .sorted((p1, p2) -> {
                            try {
                                return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                            } catch (IOException e) {
                                log.warn("获取文件修改时间失败", e);
                                return 0;
                            }
                        })
                        .collect(Collectors.toList());
            }

            // 删除超过数量限制的备份
            int deletedCount = 0;
            if (backupFiles.size() > maxBackups) {
                for (int i = maxBackups; i < backupFiles.size(); i++) {
                    Path fileToDelete = backupFiles.get(i);
                    Files.deleteIfExists(fileToDelete);
                    deletedCount++;
                    log.info("删除过期备份: {}", fileToDelete.getFileName());
                }
            }

            log.info("清理过期备份完成: 保留 {} 个, 删除 {} 个",
                    Math.min(backupFiles.size(), maxBackups), deletedCount);
            return "清理完成，删除 " + deletedCount + " 个备份";
        } catch (IOException e) {
            log.error("清理过期备份失败", e);
            return "清理失败: " + e.getMessage();
        }
    }

    /**
     * 获取备份目录路径
     */
    private Path getBackupDirPath() {
        if (workspaceInfo == null) {
            return Path.of("./workspace/memory/backups");
        }
        String backupDir = config != null ? config.getBackupDir() : "memory/backups";
        return workspaceInfo.workspace().resolve(backupDir);
    }
}
