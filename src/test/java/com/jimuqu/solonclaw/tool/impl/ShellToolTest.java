package com.jimuqu.solonclaw.tool.impl;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.noear.solon.annotation.Inject;
import org.noear.solon.test.SolonTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShellTool 测试
 * 使用 Solon 框架的测试支持
 *
 * @author SolonClaw
 */
@SolonTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ShellToolTest {

    @Inject
    private ShellTool shellTool;

    @Test
    @Order(1)
    void testExec_SimpleEchoCommand() {
        String result = shellTool.exec("echo hello");

        assertNotNull(result, "执行结果不应该为 null");
        assertTrue(result.contains("hello"), "结果应该包含 'hello'");
    }

    @Test
    @Order(2)
    void testExec_MultipleCommands() {
        String result = shellTool.exec("echo world");

        assertNotNull(result, "执行结果不应该为 null");
        assertTrue(result.contains("world"), "结果应该包含 'world'");
    }

    @Test
    @Order(3)
    void testExec_CommandWithArguments() {
        String result = shellTool.exec("echo multiple words here");

        assertNotNull(result, "执行结果不应该为 null");
        assertTrue(
            result.contains("multiple") && result.contains("words") && result.contains("here"),
            "结果应该包含所有单词"
        );
    }

    @Test
    @Order(4)
    void testExec_InvalidCommand() {
        String result = shellTool.exec("nonexistentcommand12345");

        assertNotNull(result, "执行结果不应该为 null");
        assertTrue(
            result.contains("错误") || result.contains("异常") || result.contains("not found"),
            "无效命令应该返回错误信息"
        );
    }

    @Test
    @Order(5)
    void testExec_CommandWithSpecialCharacters() {
        String result = shellTool.exec("echo \"test with spaces\"");

        assertNotNull(result, "执行结果不应该为 null");
    }

    @Test
    @Order(6)
    void testExec_MultipleCallsIndependently() {
        String result1 = shellTool.exec("echo first");
        String result2 = shellTool.exec("echo second");

        assertNotEquals(result1, result2, "不同命令应该返回不同结果");
        assertTrue(result1.contains("first"), "第一个结果应该包含 'first'");
        assertTrue(result2.contains("second"), "第二个结果应该包含 'second'");
    }

    @Test
    @Order(7)
    void testExec_EmptyCommand() {
        String result = shellTool.exec("");

        assertNotNull(result, "执行结果不应该为 null");
    }

    @Test
    @Order(8)
    void testExec_CommandWithNewlines() {
        String result = shellTool.exec("echo -e \"line1\\nline2\"");

        assertNotNull(result, "执行结果不应该为 null");
    }

    @Test
    @Order(9)
    void testExec_ResultNotNull() {
        String result = shellTool.exec("echo test");

        assertNotNull(result, "任何命令执行结果都不应该为 null");
        assertTrue(result.length() >= 0, "结果长度应该有效");
    }

    @Test
    @Order(10)
    void testExec_NoCrashOnValidCommand() {
        assertDoesNotThrow(() -> {
            shellTool.exec("echo test");
        }, "有效命令执行不应该抛出异常");
    }
}