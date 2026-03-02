package com.jimuqu.solonclaw.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogStats 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("LogStats 单元测试")
class LogStatsTest {

    @Nested
    @DisplayName("默认值测试")
    class DefaultValueTests {

        @Test
        @DisplayName("新实例应初始化空统计")
        void newInstanceOfShouldInitializeEmptyStats() {
            LogStats stats = new LogStats();

            assertEquals(0, stats.getTotalFiles());
            assertEquals(0, stats.getTotalSize());
            assertNotNull(stats.getLevelCounts(), "levelCounts 不应为 null");
            assertTrue(stats.getLevelCounts().isEmpty());
            assertEquals(0, stats.getArchiveFiles());
        }
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("应能设置和获取总文件数")
        void shouldSetAndGetTotalFiles() {
            LogStats stats = new LogStats();
            stats.setTotalFiles(100);

            assertEquals(100, stats.getTotalFiles());
        }

        @Test
        @DisplayName("应能设置和获取总大小")
        void shouldSetAndGetTotalSize() {
            LogStats stats = new LogStats();
            stats.setTotalSize(1024000);

            assertEquals(1024000, stats.getTotalSize());
        }

        @Test
        @DisplayName("应能设置和获取归档文件数")
        void shouldSetAndGetArchiveFiles() {
            LogStats stats = new LogStats();
            stats.setArchiveFiles(50);

            assertEquals(50, stats.getArchiveFiles());
        }

        @Test
        @DisplayName("应能设置和获取级别统计")
        void shouldSetAndGetLevelCounts() {
            LogStats stats = new LogStats();
            java.util.Map<String, Long> levelCounts = new java.util.HashMap<>();
            levelCounts.put("INFO", 100L);
            levelCounts.put("ERROR", 10L);

            stats.setLevelCounts(levelCounts);

            assertEquals(2, stats.getLevelCounts().size());
            assertEquals(100L, stats.getLevelCounts().get("INFO"));
            assertEquals(10L, stats.getLevelCounts().get("ERROR"));
        }

        @Test
        @DisplayName("设置levelCounts应覆盖原有值")
        void settingLevelCountsShouldOverwriteExisting() {
            LogStats stats = new LogStats();
            stats.addLevelCount("INFO", 10L);

            java.util.Map<String, Long> newCounts = new java.util.HashMap<>();
            newCounts.put("ERROR", 5L);
            stats.setLevelCounts(newCounts);

            assertEquals(1, stats.getLevelCounts().size());
            assertTrue(stats.getLevelCounts().containsKey("ERROR"));
            assertFalse(stats.getLevelCounts().containsKey("INFO"));
        }
    }

    @Nested
    @DisplayName("addLevelCount 方法测试")
    class AddLevelCountMethodTests {

        @Test
        @DisplayName("应能添加单个级别统计")
        void shouldAddSingleLevelCount() {
            LogStats stats = new LogStats();
            stats.addLevelCount("INFO", 50L);

            assertEquals(50L, stats.getLevelCounts().get("INFO"));
        }

        @Test
        @DisplayName("应能添加多个级别统计")
        void shouldAddMultipleLevelCounts() {
            LogStats stats = new LogStats();
            stats.addLevelCount("INFO", 100L);
            stats.addLevelCount("ERROR", 10L);
            stats.addLevelCount("WARN", 5L);

            assertEquals(3, stats.getLevelCounts().size());
            assertEquals(100L, stats.getLevelCounts().get("INFO"));
            assertEquals(10L, stats.getLevelCounts().get("ERROR"));
            assertEquals(5L, stats.getLevelCounts().get("WARN"));
        }

        @Test
        @DisplayName("addLevelCount应覆盖已存在的级别")
        void addLevelCountShouldOverwriteExistingLevel() {
            LogStats stats = new LogStats();
            stats.addLevelCount("INFO", 10L);
            stats.addLevelCount("INFO", 20L);

            assertEquals(20L, stats.getLevelCounts().get("INFO"));
            assertEquals(1, stats.getLevelCounts().size());
        }

        @Test
        @DisplayName("应能添加零值")
        void shouldAddZeroValue() {
            LogStats stats = new LogStats();
            stats.addLevelCount("INFO", 0L);

            assertEquals(0L, stats.getLevelCounts().get("INFO"));
        }

        @Test
        @DisplayName("应能添加负数")
        void shouldAddNegativeValue() {
            LogStats stats = new LogStats();
            stats.addLevelCount("INFO", -1L);

            assertEquals(-1L, stats.getLevelCounts().get("INFO"));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能设置零值")
        void shouldAllowZeroValues() {
            LogStats stats = new LogStats();
            stats.setTotalFiles(0);
            stats.setTotalSize(0);
            stats.setArchiveFiles(0);

            assertEquals(0, stats.getTotalFiles());
            assertEquals(0, stats.getTotalSize());
            assertEquals(0, stats.getArchiveFiles());
        }

        @Test
        @DisplayName("应能设置负数")
        void shouldAllowNegativeValues() {
            LogStats stats = new LogStats();
            stats.setTotalFiles(-1);
            stats.setTotalSize(-100);
            stats.setArchiveFiles(-5);

            assertEquals(-1, stats.getTotalFiles());
            assertEquals(-100, stats.getTotalSize());
            assertEquals(-5, stats.getArchiveFiles());
        }

        @Test
        @DisplayName("应能设置大数值")
        void shouldAllowLargeValues() {
            LogStats stats = new LogStats();
            stats.setTotalFiles(Integer.MAX_VALUE);
            stats.setTotalSize(Long.MAX_VALUE);
            stats.setArchiveFiles(Integer.MAX_VALUE);

            assertEquals(Integer.MAX_VALUE, stats.getTotalFiles());
            assertEquals(Long.MAX_VALUE, stats.getTotalSize());
            assertEquals(Integer.MAX_VALUE, stats.getArchiveFiles());
        }

        @Test
        @DisplayName("levelCounts应能设置null")
        void levelCountsShouldAllowNull() {
            LogStats stats = new LogStats();
            assertDoesNotThrow(() -> stats.setLevelCounts(null));
            assertNull(stats.getLevelCounts());
        }

        @Test
        @DisplayName("levelCounts应能设置空Map")
        void levelCountsShouldAllowEmptyMap() {
            LogStats stats = new LogStats();
            stats.addLevelCount("INFO", 10L);
            stats.setLevelCounts(new java.util.HashMap<>());

            assertTrue(stats.getLevelCounts().isEmpty());
        }
    }

    @Nested
    @DisplayName("完整统计场景测试")
    class CompleteStatsScenarioTests {

        @Test
        @DisplayName("应能构建完整的统计信息")
        void shouldBuildCompleteStatistics() {
            LogStats stats = new LogStats();
            stats.setTotalFiles(100);
            stats.setTotalSize(1024000);
            stats.setArchiveFiles(20);
            stats.addLevelCount("INFO", 80L);
            stats.addLevelCount("USER_CHAT", 15L);
            stats.addLevelCount("ERROR", 5L);

            assertEquals(100, stats.getTotalFiles(), "总文件数应为100");
            assertEquals(1024000, stats.getTotalSize(), "总大小应为1024000");
            assertEquals(20, stats.getArchiveFiles(), "归档文件数应为20");
            assertEquals(3, stats.getLevelCounts().size(), "级别统计应有3项");
            assertEquals(100L,
                    stats.getLevelCounts().values().stream().mapToLong(Long::longValue).sum(),
                    "所有级别总和应为100");
        }

        @Test
        @DisplayName("应能更新现有统计信息")
        void shouldUpdateExistingStatistics() {
            LogStats stats = new LogStats();
            stats.setTotalFiles(100);
            stats.addLevelCount("INFO", 50L);

            // 更新统计
            stats.setTotalFiles(150);
            stats.addLevelCount("ERROR", 10L);
            stats.setArchiveFiles(30);

            assertEquals(150, stats.getTotalFiles());
            assertEquals(2, stats.getLevelCounts().size());
            assertEquals(30, stats.getArchiveFiles());
        }
    }
}
