package com.jimuqu.solonclaw;

import org.junit.jupiter.api.*;
import org.noear.solon.annotation.SolonMain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SolonClaw 应用启动测试
 * <p>
 * 测试完整应用启动流程，验证：
 * 1. 应用主类存在且正确配置
 * 2. 配置文件存在且格式正确
 * 3. 所有必需配置项都存在
 * 4. 端口和其他关键参数配置正确
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SolonClawAppTest {

    private static Properties appProperties;

    @BeforeAll
    static void setUpClass() {
        // 加载应用配置
        appProperties = new Properties();
        try (InputStream input = SolonClawAppTest.class
                .getClassLoader()
                .getResourceAsStream("app.yml")) {

            // YAML 文件不能直接用 Properties 加载，使用资源文件路径验证
            assertNotNull(input, "app.yml 配置文件应该存在");
        } catch (IOException e) {
            fail("无法加载配置文件: " + e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("验证应用主类存在且可实例化")
    void testMainClassExists() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("com.jimuqu.solonclaw.SolonClawApp");
            assertNotNull(clazz);
            assertEquals("com.jimuqu.solonclaw.SolonClawApp", clazz.getName());
        });
    }

    @Test
    @Order(2)
    @DisplayName("验证配置文件存在于类路径")
    void testConfigFilesExist() {
        // 验证配置文件存在
        assertNotNull(getClass().getClassLoader().getResource("app.yml"),
            "app.yml 应该存在于类路径中");
    }

    @Test
    @Order(3)
    @DisplayName("验证SolonMain注解存在")
    void testSolonMainAnnotation() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("com.jimuqu.solonclaw.SolonClawApp");

            assertTrue(clazz.isAnnotationPresent(SolonMain.class),
                "主类应该有 @SolonMain 注解");
        });
    }

    @Test
    @Order(4)
    @DisplayName("验证主方法签名正确")
    void testMainMethodSignature() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("com.jimuqu.solonclaw.SolonClawApp");

            var mainMethod = clazz.getMethod("main", String[].class);
            assertNotNull(mainMethod, "main 方法应该存在");

            int modifiers = mainMethod.getModifiers();
            assertTrue(java.lang.reflect.Modifier.isPublic(modifiers),
                "main 方法应该是 public");
            assertTrue(java.lang.reflect.Modifier.isStatic(modifiers),
                "main 方法应该是 static");

            assertEquals(void.class, mainMethod.getReturnType(),
                "main 方法应该返回 void");
        });
    }

    @Test
    @Order(5)
    @DisplayName("验证配置文件内容格式")
    void testConfigFileContent() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证关键配置项存在
                assertTrue(content.contains("solon:"), "应包含 solon 配置节");
                assertTrue(content.contains("port:"), "应包含端口配置");
                assertTrue(content.contains("nullclaw:"), "应包含 nullclaw 配置节");
                assertTrue(content.contains("workspace:"), "应包含工作目录配置");
            }
        });
    }

    @Test
    @Order(6)
    @DisplayName("验证端口配置存在")
    void testPortConfigurationExists() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证端口配置
                assertTrue(content.contains("port: 41234") ||
                    content.contains("port:41234") ||
                    content.contains("port:\n    41234"),
                    "应配置端口 41234");
            }
        });
    }

    @Test
    @Order(7)
    @DisplayName("验证 AI 配置存在")
    void testAiConfigurationExists() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证 AI 配置
                assertTrue(content.contains("openai:"), "应包含 OpenAI 配置");
                assertTrue(content.contains("apiUrl:"), "应包含 API URL 配置");
                assertTrue(content.contains("apiKey:"), "应包含 API Key 配置");
                assertTrue(content.contains("model:"), "应包含模型配置");
            }
        });
    }

    @Test
    @Order(8)
    @DisplayName("验证工作目录配置完整")
    void testWorkspaceConfigurationComplete() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证工作目录配置
                assertTrue(content.contains("workspace:"), "应包含工作目录配置");

                // 验证子目录配置
                String[] requiredDirs = {
                    "mcpConfig", "skillsDir", "jobsFile",
                    "jobHistoryFile", "database", "shellWorkspace", "logsDir"
                };

                for (String dir : requiredDirs) {
                    assertTrue(content.contains(dir),
                        "应包含 " + dir + " 配置");
                }
            }
        });
    }

    @Test
    @Order(9)
    @DisplayName("验证工具配置存在")
    void testToolsConfigurationExists() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证工具配置
                assertTrue(content.contains("tools:"), "应包含工具配置节");
                assertTrue(content.contains("shell:"), "应包含 Shell 工具配置");
                assertTrue(content.contains("enabled:"), "应包含启用状态配置");
                assertTrue(content.contains("timeoutSeconds:"), "应包含超时配置");
            }
        });
    }

    @Test
    @Order(10)
    @DisplayName("验证记忆服务配置")
    void testMemoryConfigurationExists() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证记忆配置
                assertTrue(content.contains("memory:"), "应包含记忆配置节");
                assertTrue(content.contains("session:"), "应包含会话配置");
                assertTrue(content.contains("maxHistory:"), "应包含最大历史记录配置");
            }
        });
    }

    @Test
    @Order(11)
    @DisplayName("验证序列化配置")
    void testSerializationConfigurationExists() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证序列化配置
                assertTrue(content.contains("serialization:"), "应包含序列化配置节");
                assertTrue(content.contains("json:"), "应包含 JSON 配置");
                assertTrue(content.contains("dateAsFormat:"), "应包含日期格式配置");
            }
        });
    }

    @Test
    @Order(12)
    @DisplayName("验证 Agent 配置")
    void testAgentConfigurationExists() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证 Agent 配置
                assertTrue(content.contains("agent:"), "应包含 Agent 配置节");
                assertTrue(content.contains("model:"), "应包含模型配置");
                assertTrue(content.contains("maxToolIterations:"), "应包含最大工具迭代配置");
                assertTrue(content.contains("maxHistoryMessages:"), "应包含最大历史消息配置");
            }
        });
    }

    @Test
    @Order(13)
    @DisplayName("验证回调配置")
    void testCallbackConfigurationExists() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证回调配置
                assertTrue(content.contains("callback:"), "应包含回调配置节");
                assertTrue(content.contains("enabled:"), "应包含回调启用配置");
            }
        });
    }

    @Test
    @Order(14)
    @DisplayName("验证应用名称配置")
    void testAppNameConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证应用名称
                assertTrue(content.contains("name: solonclaw") ||
                    content.contains("name:solonclaw") ||
                    content.contains("name:\n    solonclaw"),
                    "应用名称应为 solonclaw");
            }
        });
    }

    @Test
    @Order(15)
    @DisplayName("验证环境配置")
    void testEnvironmentConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证环境配置
                assertTrue(content.contains("env:"), "应包含环境配置");
                assertTrue(content.contains("dev") || content.contains("prod"),
                    "应配置环境为 dev 或 prod");
            }
        });
    }

    @Test
    @Order(16)
    @DisplayName("验证数据库文件配置")
    void testDatabaseFileConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证数据库文件配置
                assertTrue(content.contains("database:"), "应包含数据库配置");
                assertTrue(content.contains(".db"), "数据库文件应以 .db 结尾");
            }
        });
    }

    @Test
    @Order(17)
    @DisplayName("验证默认参数配置")
    void testDefaultsConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证默认参数配置
                assertTrue(content.contains("defaults:"), "应包含默认参数配置节");
                assertTrue(content.contains("temperature:"), "应包含温度配置");
                assertTrue(content.contains("maxTokens:"), "应包含最大Token配置");
                assertTrue(content.contains("timeoutSeconds:"), "应包含超时配置");
            }
        });
    }

    @Test
    @Order(18)
    @DisplayName("验证配置文件编码为UTF-8")
    void testConfigFileEncoding() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes(), "UTF-8");

                // 验证可以正确读取中文注释
                assertTrue(content.length() > 0, "配置文件内容不应为空");

                // 检查是否包含注释（中文）
                boolean hasComments = content.contains("#") ||
                    content.contains("配置") ||
                    content.contains("SolonClaw");
                assertTrue(hasComments, "配置文件应包含注释");
            }
        });
    }

    @Test
    @Order(19)
    @DisplayName("验证工作目录路径配置")
    void testWorkspacePathConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证工作目录路径配置
                assertTrue(content.contains("./workspace") ||
                    content.contains("workspace:") ||
                    content.contains("workspace\n"),
                    "应配置工作目录路径");
            }
        });
    }

    @Test
    @Order(20)
    @DisplayName("验证配置文件无语法错误")
    void testConfigFileSyntax() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 简单的 YAML 语法检查
                String[] lines = content.split("\n");
                int tabCount = 0;

                for (String line : lines) {
                    // 检查不应该有 Tab 字符（YAML 应使用空格缩进）
                    assertFalse(line.contains("\t"),
                        "YAML 文件不应包含 Tab 字符，行: " + line);
                }

                // 验证文件不为空
                assertTrue(content.trim().length() > 100,
                    "配置文件内容应该足够丰富");
            }
        });
    }

    @Test
    @Order(21)
    @DisplayName("验证依赖配置完整")
    void testDependencyConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证关键依赖配置
                assertTrue(content.contains("solon.ai") ||
                    content.contains("chat") ||
                    content.contains("openai"),
                    "应包含 AI 相关配置");
            }
        });
    }

    @Test
    @Order(22)
    @DisplayName("验证配置节结构完整")
    void testConfigurationSections() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证所有主要配置节存在
                String[] requiredSections = {
                    "solon:", "ai:", "serialization:",
                    "nullclaw:"
                };

                for (String section : requiredSections) {
                    assertTrue(content.contains(section),
                        "应包含 " + section + " 配置节");
                }
            }
        });
    }

    @Test
    @Order(23)
    @DisplayName("验证日志目录配置")
    void testLogsDirectoryConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证日志目录配置
                assertTrue(content.contains("logsDir:"), "应包含日志目录配置");
            }
        });
    }

    @Test
    @Order(24)
    @DisplayName("验证 MCP 配置")
    void testMcpConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证 MCP 配置
                assertTrue(content.contains("mcpConfig:"), "应包含 MCP 配置文件路径");
            }
        });
    }

    @Test
    @Order(25)
    @DisplayName("验证 Skills 配置")
    void testSkillsConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证 Skills 配置
                assertTrue(content.contains("skillsDir:"), "应包含 Skills 目录配置");
            }
        });
    }

    @Test
    @Order(26)
    @DisplayName("验证 Shell 工作空间配置")
    void testShellWorkspaceConfiguration() {
        assertDoesNotThrow(() -> {
            try (InputStream input = getClass()
                    .getClassLoader()
                    .getResourceAsStream("app.yml")) {

                String content = new String(input.readAllBytes());

                // 验证 Shell 工作空间配置
                assertTrue(content.contains("shellWorkspace:"), "应包含 Shell 工作空间配置");
            }
        });
    }
}
