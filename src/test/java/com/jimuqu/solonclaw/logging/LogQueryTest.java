package com.jimuqu.solonclaw.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogQuery 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("LogQuery 单元测试")
class LogQueryTest {

    @Nested
    @DisplayName("默认值测试")
    class DefaultValueTests {

        @Test
        @DisplayName("新实例应初始化空集合")
        void newInstanceOfShouldInitializeEmptyCollections() {
            LogQuery query = new LogQuery();

            assertTrue(query.getLevels().isEmpty());
            assertTrue(query.getSources().isEmpty());
            assertTrue(query.getCategories().isEmpty());
        }

        @Test
        @DisplayName("默认页面应为第1页")
        void defaultPageShouldBeOne() {
            LogQuery query = new LogQuery();
            assertEquals(1, query.getPage());
        }

        @Test
        @DisplayName("默认每页大小应为100")
        void defaultPageSizeShouldBe100() {
            LogQuery query = new LogQuery();
            assertEquals(100, query.getPageSize());
        }

        @Test
        @DisplayName("默认最大文件数应为30")
        void defaultMaxFilesShouldBe30() {
            LogQuery query = new LogQuery();
            assertEquals(30, query.getMaxFiles());
        }

        @Test
        @DisplayName("默认时间范围应为null")
        void defaultTimeRangeShouldBeNull() {
            LogQuery query = new LogQuery();
            assertNull(query.getStartTime());
            assertNull(query.getEndTime());
        }

        @Test
        @DisplayName("默认字符串条件应为null")
        void defaultStringConditionsShouldBeNull() {
            LogQuery query = new LogQuery();
            assertNull(query.getTraceId());
            assertNull(query.getSessionId());
            assertNull(query.getKeyword());
        }
    }

    @Nested
    @DisplayName("设置器测试")
    class SetterTests {

        @Test
        @DisplayName("应能设置日志级别")
        void shouldSetLevels() {
            LogQuery query = new LogQuery();
            Set<LogLevel> levels = Set.of(LogLevel.INFO, LogLevel.ERROR);

            query.setLevels(levels);

            assertEquals(levels, query.getLevels());
        }

        @Test
        @DisplayName("应能设置来源")
        void shouldSetSources() {
            LogQuery query = new LogQuery();
            Set<String> sources = Set.of("Agent", "Gateway");

            query.setSources(sources);

            assertEquals(sources, query.getSources());
        }

        @Test
        @DisplayName("应能设置分类")
        void shouldSetCategories() {
            LogQuery query = new LogQuery();
            Set<String> categories = Set.of("API", "MEMORY");

            query.setCategories(categories);

            assertEquals(categories, query.getCategories());
        }

        @Test
        @DisplayName("应能设置TranceId")
        void shouldSetTraceId() {
            LogQuery query = new LogQuery();
            query.setTraceId("trace-123");

            assertEquals("trace-123", query.getTraceId());
        }

        @Test
        @DisplayName("应能设置SessionId")
        void shouldSetSessionId() {
            LogQuery query = new LogQuery();
            query.setSessionId("session-456");

            assertEquals("session-456", query.getSessionId());
        }

        @Test
        @DisplayName("应能设置关键词")
        void shouldSetKeyword() {
            LogQuery query = new LogQuery();
            query.setKeyword("错误");

            assertEquals("错误", query.getKeyword());
        }

        @Test
        @DisplayName("应能设置页码")
        void shouldSetPage() {
            LogQuery query = new LogQuery();
            query.setPage(5);

            assertEquals(5, query.getPage());
        }

        @Test
        @DisplayName("应能设置每页大小")
        void shouldSetPageSize() {
            LogQuery query = new LogQuery();
            query.setPageSize(50);

            assertEquals(50, query.getPageSize());
        }

        @Test
        @DisplayName("应能设置最大文件数")
        void shouldSetMaxFiles() {
            LogQuery query = new LogQuery();
            query.setMaxFiles(100);

            assertEquals(100, query.getMaxFiles());
        }

        @Test
        @DisplayName("应能设置时间范围")
        void shouldSetTimeRange() {
            LogQuery query = new LogQuery();
            java.time.LocalDateTime start = java.time.LocalDateTime.of(2024, 1, 1, 0, 0);
            java.time.LocalDateTime end = java.time.LocalDateTime.of(2024, 12, 31, 23, 59);

            query.setStartTime(start);
            query.setEndTime(end);

            assertEquals(start, query.getStartTime());
            assertEquals(end, query.getEndTime());
        }
    }

    @Nested
    @DisplayName("链式调用测试")
    class ChainedCallTests {

        @Test
        @DisplayName("addLevel 应支持链式调用")
        void addLevelShouldSupportChainedCalls() {
            LogQuery query = new LogQuery();

            LogQuery result = query.addLevel(LogLevel.INFO)
                    .addLevel(LogLevel.ERROR)
                    .addLevel(LogLevel.USER_CHAT);

            assertSame(query, result, "应返回同一实例");
            assertTrue(query.getLevels().contains(LogLevel.INFO));
            assertTrue(query.getLevels().contains(LogLevel.ERROR));
            assertTrue(query.getLevels().contains(LogLevel.USER_CHAT));
        }

        @Test
        @DisplayName("addSource 应支持链式调用")
        void addSourceShouldSupportChainedCalls() {
            LogQuery query = new LogQuery();

            LogQuery result = query.addSource("Agent")
                    .addSource("Gateway");

            assertSame(query, result, "应返回同一实例");
            assertTrue(query.getSources().contains("Agent"));
            assertTrue(query.getSources().contains("Gateway"));
        }

        @Test
        @DisplayName("addCategory 应支持链式调用")
        void addCategoryShouldSupportChainedCalls() {
            LogQuery query = new LogQuery();

            LogQuery result = query.addCategory("API")
                    .addCategory("MEMORY");

            assertSame(query, result, "应返回同一实例");
            assertTrue(query.getCategories().contains("API"));
            assertTrue(query.getCategories().contains("MEMORY"));
        }

        @Test
        @DisplayName("setPage 应支持链式调用")
        void setPageShouldSupportChainedCalls() {
            LogQuery query = new LogQuery();
            query.setPage(2).setPageSize(50);

            assertEquals(2, query.getPage());
            assertEquals(50, query.getPageSize());
        }

        @Test
        @DisplayName("setTimeRange 应支持链式调用")
        void setTimeRangeShouldSupportChainedCalls() {
            LogQuery query = new LogQuery();
            java.time.LocalDateTime start = java.time.LocalDateTime.now();

            query.setStartTime(start).setEndTime(start.plusHours(1));

            assertEquals(start, query.getStartTime());
            assertEquals(start.plusHours(1), query.getEndTime());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应能设置页码为0")
        void shouldAllowPageAsZero() {
            LogQuery query = new LogQuery();
            query.setPage(0);
            assertEquals(0, query.getPage());
        }

        @Test
        @DisplayName("应能设置每页大小为0")
        void shouldAllowPageSizeAsZero() {
            LogQuery query = new LogQuery();
            query.setPageSize(0);
            assertEquals(0, query.getPageSize());
        }

        @Test
        @DisplayName("应能设置最大文件数为0")
        void shouldAllowMaxFilesAsZero() {
            LogQuery query = new LogQuery();
            query.setMaxFiles(0);
            assertEquals(0, query.getMaxFiles());
        }

        @Test
        @DisplayName("应能设置空集合")
        void shouldAllowEmptyCollections() {
            LogQuery query = new LogQuery();
            query.setLevels(Set.of());
            query.setSources(Set.of());
            query.setCategories(Set.of());

            assertTrue(query.getLevels().isEmpty());
            assertTrue(query.getSources().isEmpty());
            assertTrue(query.getCategories().isEmpty());
        }

        @Test
        @DisplayName("应能设置null集合")
        void shouldAllowNullCollections() {
            LogQuery query = new LogQuery();
            assertDoesNotThrow(() -> query.setLevels(null));
            assertDoesNotThrow(() -> query.setSources(null));
            assertDoesNotThrow(() -> query.setCategories(null));
        }

        @Test
        @DisplayName("应能设置空字符串")
        void shouldAllowEmptyString() {
            LogQuery query = new LogQuery();
            query.setTraceId("");
            query.setSessionId("");
            query.setKeyword("");

            assertEquals("", query.getTraceId());
            assertEquals("", query.getSessionId());
            assertEquals("", query.getKeyword());
        }
    }
}