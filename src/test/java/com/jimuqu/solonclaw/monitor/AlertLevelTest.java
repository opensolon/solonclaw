package com.jimuqu.solonclaw.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlertLevel 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("AlertLevel 单元测试")
class AlertLevelTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValueTests {

        @Test
        @DisplayName("应包含所有定义的告警级别")
        void shouldContainAllDefinedLevels() {
            AlertLevel[] levels = AlertLevel.values();
            assertEquals(4, levels.length, "应包含 4 个告警级别");
        }

        @Test
        @DisplayName("INFO 级别应返回正确描述")
        void infoLevelShouldReturnCorrectDescription() {
            assertEquals("信息", AlertLevel.INFO.getDescription());
        }

        @Test
        @DisplayName("WARNING 级别应返回正确描述")
        void warningLevelShouldReturnCorrectDescription() {
            assertEquals("警告", AlertLevel.WARNING.getDescription());
        }

        @Test
        @DisplayName("CRITICAL 级别应返回正确描述")
        void criticalLevelShouldReturnCorrectDescription() {
            assertEquals("严重", AlertLevel.CRITICAL.getDescription());
        }

        @Test
        @DisplayName("EMERGENCY 级别应返回正确描述")
        void emergencyLevelShouldReturnCorrectDescription() {
            assertEquals("紧急", AlertLevel.EMERGENCY.getDescription());
        }

        @Test
        @DisplayName("枚举声明顺序应正确")
        void enumOrderShouldBeCorrect() {
            AlertLevel[] levels = AlertLevel.values();
            assertEquals(AlertLevel.INFO, levels[0]);
            assertEquals(AlertLevel.WARNING, levels[1]);
            assertEquals(AlertLevel.CRITICAL, levels[2]);
            assertEquals(AlertLevel.EMERGENCY, levels[3]);
        }
    }

    @Nested
    @DisplayName("枚举valueOf测试")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf 应返回正确的枚举值")
        void valueOfShouldReturnCorrectEnum() {
            assertEquals(AlertLevel.INFO, AlertLevel.valueOf("INFO"));
            assertEquals(AlertLevel.WARNING, AlertLevel.valueOf("WARNING"));
            assertEquals(AlertLevel.CRITICAL, AlertLevel.valueOf("CRITICAL"));
            assertEquals(AlertLevel.EMERGENCY, AlertLevel.valueOf("EMERGENCY"));
        }

        @Test
        @DisplayName("valueOf 对于无效值应抛出异常")
        void valueOfShouldThrowExceptionForInvalidValue() {
            assertThrows(IllegalArgumentException.class, () -> AlertLevel.valueOf("INVALID"));
        }
    }
}
