package com.jimuqu.solonclaw.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpServerStatus 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("McpServerStatus 单元测试")
class McpServerStatusTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValueTests {

        @Test
        @DisplayName("应包含所有定义的状态")
        void shouldContainAllDefinedStatuses() {
            McpServerStatus[] statuses = McpServerStatus.values();
            assertEquals(5, statuses.length, "应包含 5 个状态");
        }

        @Test
        @DisplayName("STOPPED 状态应返回正确的代码和描述")
        void stoppedStatusShouldReturnCorrectCodeAndDescription() {
            assertEquals("stopped", McpServerStatus.STOPPED.getCode());
            assertEquals("已停止", McpServerStatus.STOPPED.getDescription());
        }

        @Test
        @DisplayName("STARTING 状态应返回正确的代码和描述")
        void startingStatusShouldReturnCorrectCodeAndDescription() {
            assertEquals("starting", McpServerStatus.STARTING.getCode());
            assertEquals("启动中", McpServerStatus.STARTING.getDescription());
        }

        @Test
        @DisplayName("INITIALIZED 状态应返回正确的代码和描述")
        void initializedStatusShouldReturnCorrectCodeAndDescription() {
            assertEquals("initialized", McpServerStatus.INITIALIZED.getCode());
            assertEquals("已初始化", McpServerStatus.INITIALIZED.getDescription());
        }

        @Test
        @DisplayName("RUNNING 状态应返回正确的代码和描述")
        void runningStatusShouldReturnCorrectCodeAndDescription() {
            assertEquals("running", McpServerStatus.RUNNING.getCode());
            assertEquals("运行中", McpServerStatus.RUNNING.getDescription());
        }

        @Test
        @DisplayName("ERROR 状态应返回正确的代码和描述")
        void errorStatusShouldReturnCorrectCodeAndDescription() {
            assertEquals("error", McpServerStatus.ERROR.getCode());
            assertEquals("错误", McpServerStatus.ERROR.getDescription());
        }

        @Test
        @DisplayName("枚举声明顺序应正确")
        void enumOrderShouldBeCorrect() {
            McpServerStatus[] statuses = McpServerStatus.values();
            assertEquals(McpServerStatus.STOPPED, statuses[0]);
            assertEquals(McpServerStatus.STARTING, statuses[1]);
            assertEquals(McpServerStatus.INITIALIZED, statuses[2]);
            assertEquals(McpServerStatus.RUNNING, statuses[3]);
            assertEquals(McpServerStatus.ERROR, statuses[4]);
        }
    }

    @Nested
    @DisplayName("枚举valueOf测试")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf 应返回正确的枚举值")
        void valueOfShouldReturnCorrectEnum() {
            assertEquals(McpServerStatus.STOPPED, McpServerStatus.valueOf("STOPPED"));
            assertEquals(McpServerStatus.STARTING, McpServerStatus.valueOf("STARTING"));
            assertEquals(McpServerStatus.INITIALIZED, McpServerStatus.valueOf("INITIALIZED"));
            assertEquals(McpServerStatus.RUNNING, McpServerStatus.valueOf("RUNNING"));
            assertEquals(McpServerStatus.ERROR, McpServerStatus.valueOf("ERROR"));
        }

        @Test
        @DisplayName("valueOf 对于无效值应抛出异常")
        void valueOfShouldThrowExceptionForInvalidValue() {
            assertThrows(IllegalArgumentException.class, () -> McpServerStatus.valueOf("INVALID"));
        }
    }

    @Nested
    @DisplayName("状态码唯一性测试")
    class CodeUniquenessTests {

        @Test
        @DisplayName("所有状态码应唯一")
        void allCodesShouldBeUnique() {
            McpServerStatus[] statuses = McpServerStatus.values();
            long uniquecodes = java.util.Arrays.stream(statuses)
                    .map(McpServerStatus::getCode)
                    .distinct()
                    .count();
            assertEquals(statuses.length, uniquecodes, "所有状态码应唯一");
        }
    }
}
