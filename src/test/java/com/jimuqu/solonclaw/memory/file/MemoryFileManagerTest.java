package com.jimuqu.solonclaw.memory.file;

import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryFileManager 单元测试
 */
class MemoryFileManagerTest {

    @TempDir
    Path tempDir;

    private MemoryFileManager manager;
    private MemoryFileConfig config;
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    @BeforeEach
    void setUp() {
        config = new MemoryFileConfig();
        config.setEnabled(true);
        config.setRetainDays(30);
        config.setAutoAppendEvents(true);
        config.setMaxNoteSize(102400);

        manager = new MemoryFileManager();

        // 创建 WorkspaceInfo
        workspaceInfo = new WorkspaceConfig.WorkspaceInfo(
            tempDir,
            tempDir.resolve("skills"),
            tempDir.resolve("jobs.json"),
            tempDir.resolve("job-history.json"),
            tempDir.resolve("memory.db"),
            tempDir.resolve("workspace"),
            tempDir.resolve("logs"),
            tempDir.resolve("autonomous-state.json")
        );

        // 通过反射注入测试所需的字段
        setField(manager, "workspaceInfo", workspaceInfo);
        setField(manager, "config", config);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testAppendToTodayNote() {
        // 追加内容
        String result = manager.appendToTodayNote("测试事件：这是一个测试");
        assertEquals("追加成功", result);

        // 验证内容
        String content = manager.readTodayNote();
        assertTrue(content.contains("测试事件：这是一个测试"));
    }

    @Test
    void testReadTodayNote() {
        // 先写入内容
        manager.appendToTodayNote("测试内容");

        // 读取
        String content = manager.readTodayNote();
        assertTrue(content.contains("测试内容"));
    }

    @Test
    void testReadYesterdayNote() {
        // 读取不存在的昨日笔记
        String content = manager.readYesterdayNote();
        assertEquals("", content);
    }

    @Test
    void testReadLongTermMemory() {
        // 读取不存在的长期记忆
        String content = manager.readLongTermMemory();
        assertEquals("", content);
    }

    @Test
    void testAppendToLongTermMemory() {
        // 追加内容
        String result = manager.appendToLongTermMemory("用户偏好", "喜欢使用中文");
        assertEquals("追加成功", result);

        // 验证内容
        String content = manager.readLongTermMemory();
        assertTrue(content.contains("用户偏好"));
        assertTrue(content.contains("喜欢使用中文"));
    }

    @Test
    void testUpdateLongTermMemory() {
        // 先追加内容
        manager.appendToLongTermMemory("测试", "测试内容");

        // 更新长期记忆
        String newContent = "# 长期记忆\n\n> 这是一个新的长期记忆";
        String result = manager.updateLongTermMemory(newContent);
        assertEquals("更新成功", result);

        // 验证
        String content = manager.readLongTermMemory();
        assertEquals(newContent, content);
    }

    @Test
    void testCleanupOldNotes() {
        // 创建一些过期的笔记文件
        Path memoryDir = tempDir.resolve("memory");
        Path todayNote = memoryDir.resolve("2026-03-11.md");
        Path oldNote = memoryDir.resolve("2026-02-04.md");
        Path recentNote = memoryDir.resolve("2026-02-19.md");

        try {
            Files.createDirectories(memoryDir);

            // 今天的笔记
            Files.writeString(todayNote, "# 今日笔记");

            // 35天前的笔记（应该被清理）
            Files.writeString(oldNote, "# 旧笔记");

            // 20天前的笔记（应该保留）
            Files.writeString(recentNote, "# 最近的笔记");

        } catch (Exception e) {
            fail("创建测试文件失败: " + e.getMessage());
        }

        // 清理 30 天前的笔记
        String result = manager.cleanupOldNotes(30);
        assertTrue(result.contains("删除 1 个文件"));

        // 验证
        assertTrue(Files.exists(todayNote));
        assertFalse(Files.exists(oldNote));
        assertTrue(Files.exists(recentNote));
    }

    @Test
    void testMaxNoteSize() {
        // 先创建笔记
        manager.appendToTodayNote("初始内容");

        // 设置较小的最大大小（按字符计算）
        config.setMaxNoteSize(10);

        // 追加内容超过限制（原有内容 + 新内容 > 10）
        String longContent = "这是一段很长的内容";
        String result = manager.appendToTodayNote(longContent);
        assertEquals("笔记大小超过限制", result);
    }

    @Test
    void testReadFileBlock() {
        // 创建一个大文件
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("第 ").append(i).append(" 行内容\n");
        }
        Path testFile = tempDir.resolve("test-block.txt");
        try {
            Files.writeString(testFile, sb.toString());
        } catch (Exception e) {
            fail("创建测试文件失败: " + e.getMessage());
        }

        // 测试分块读取
        String block1 = manager.readFileBlock(testFile, 0, 10);
        assertTrue(block1.contains("第 0 行内容"));
        assertTrue(block1.contains("第 9 行内容"));
        assertFalse(block1.contains("第 10 行内容"));

        // 测试从中间开始读取
        String block2 = manager.readFileBlock(testFile, 50, 5);
        assertTrue(block2.contains("第 50 行内容"));
        assertTrue(block2.contains("第 54 行内容"));
        assertFalse(block2.contains("第 49 行内容"));
        assertFalse(block2.contains("第 55 行内容"));

        // 测试读取超过文件末尾
        String block3 = manager.readFileBlock(testFile, 95, 10);
        assertTrue(block3.contains("第 95 行内容"));
        assertTrue(block3.contains("第 99 行内容"));
    }

    @Test
    void testReadLongTermMemoryPreview() {
        // 创建一个包含多行的长期记忆
        StringBuilder sb = new StringBuilder();
        sb.append("# 长期记忆\n\n");
        for (int i = 0; i < 50; i++) {
            sb.append("内容行 ").append(i).append("\n");
        }
        manager.updateLongTermMemory(sb.toString());

        // 测试读取前 10 行
        String preview = manager.readLongTermMemoryPreview(10);
        assertTrue(preview.contains("# 长期记忆"));
        assertTrue(preview.contains("内容行 0"));
        assertFalse(preview.contains("内容行 20")); // 不应该包含后面的内容

        // 验证完整内容确实存在
        String fullContent = manager.readLongTermMemory();
        assertTrue(fullContent.contains("内容行 20"));
    }

    @Test
    void testBackup() {
        // 启用备份
        config.setBackupEnabled(true);
        config.setMaxBackups(5);
        config.setBackupDir("memory/backups");

        // 创建长期记忆
        manager.appendToLongTermMemory("测试章节", "测试备份内容");

        // 执行备份
        String result = manager.backup();
        assertTrue(result.contains("备份成功"));

        // 验证备份文件存在
        try {
            Path backupDir = tempDir.resolve("memory/backups");
            List<Path> backupFiles = Files.list(backupDir)
                    .filter(p -> p.getFileName().toString().startsWith("MEMORY_"))
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .collect(Collectors.toList());

            assertEquals(1, backupFiles.size(), "应该有 1 个备份文件");

            // 验证备份文件内容
            Path backupFile = backupFiles.get(0);
            String backupContent = Files.readString(backupFile);
            assertTrue(backupContent.contains("测试备份内容"));

        } catch (Exception e) {
            fail("验证备份文件失败: " + e.getMessage());
        }
    }

    @Test
    void testBackupDisabled() {
        // 禁用备份
        config.setBackupEnabled(false);

        // 创建长期记忆
        manager.appendToLongTermMemory("测试章节", "不应该备份的内容");

        // 执行备份
        String result = manager.backup();
        assertEquals("备份功能未启用", result);

        // 验证没有备份文件
        try {
            Path backupDir = tempDir.resolve("memory/backups");
            if (Files.exists(backupDir)) {
                long count = Files.list(backupDir).count();
                assertEquals(0, count, "不应该有备份文件");
            }
        } catch (Exception e) {
            fail("验证备份目录失败: " + e.getMessage());
        }
    }

    @Test
    void testCleanupOldBackups() {
        // 启用备份，设置最大备份数为 3
        config.setBackupEnabled(true);
        config.setMaxBackups(3);
        config.setBackupDir("memory/backups");

        // 创建长期记忆
        manager.appendToLongTermMemory("测试章节", "测试清理备份");

        // 创建备份目录
        Path backupDir = tempDir.resolve("memory/backups");
        try {
            Files.createDirectories(backupDir);

            // 手动创建 5 个备份文件（模拟多次备份）
            for (int i = 0; i < 5; i++) {
                String timestamp = String.format("2026-03-12_%02d-00-00", i);
                Path backupFile = backupDir.resolve("MEMORY_" + timestamp + ".bak");
                Files.writeString(backupFile, "# 长期记忆\n测试内容 " + i);
                // 添加短暂延迟以确保文件修改时间不同
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 验证创建了 5 个备份文件
            long countBefore = Files.list(backupDir)
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .count();
            assertEquals(5, countBefore, "应该创建了 5 个备份文件");

        } catch (Exception e) {
            fail("创建测试备份文件失败: " + e.getMessage());
        }

        // 手动调用清理方法（保留最新的 3 个）
        String result = manager.cleanupOldBackups(3);
        assertTrue(result.contains("删除 2 个备份"));

        // 验证只保留最新的 3 个备份
        try {
            long countAfter = Files.list(backupDir)
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .count();

            assertEquals(3, countAfter, "应该只保留 3 个备份文件");

        } catch (Exception e) {
            fail("验证备份数量失败: " + e.getMessage());
        }
    }

    @Test
    void testBackupWhenNoMemoryFile() {
        // 不创建长期记忆文件
        config.setBackupEnabled(true);

        // 执行备份
        String result = manager.backup();
        assertEquals("无需备份", result);
    }
}
