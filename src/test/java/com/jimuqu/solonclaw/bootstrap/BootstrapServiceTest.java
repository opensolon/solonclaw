package com.jimuqu.solonclaw.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BootstrapService 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("BootstrapService 单元测试")
class BootstrapServiceTest {

    @Nested
    @DisplayName("BootstrapFile 记录类测试")
    class BootstrapFileRecordTests {

        @Test
        @DisplayName("of 方法应创建存在的文件")
        void ofShouldCreateExistingFile() {
            BootstrapFile file = BootstrapFile.of("test.md", "/path/to/test.md", "content");

            assertEquals("test.md", file.name());
            assertEquals("/path/to/test.md", file.path());
            assertEquals("content", file.content());
            assertFalse(file.missing());
            assertTrue(file.hasContent());
        }

        @Test
        @DisplayName("missing 方法应创建缺失的文件")
        void missingShouldCreateMissingFile() {
            BootstrapFile file = BootstrapFile.missing("test.md", "/path/to/test.md");

            assertEquals("test.md", file.name());
            assertEquals("/path/to/test.md", file.path());
            assertNull(file.content());
            assertTrue(file.missing());
            assertFalse(file.hasContent());
        }
    }

    @Nested
    @DisplayName("引导文件加载测试")
    class BootstrapFileLoadingTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("loadBootstrapFiles 应返回所有引导文件")
        void loadBootstrapFilesShouldReturnAllFiles() throws Exception {
            // 创建一些引导文件
            Path soulFile = tempDir.resolve("SOUL.md");
            Path identityFile = tempDir.resolve("IDENTITY.md");
            Files.writeString(soulFile, "我是灵魂内容");
            Files.writeString(identityFile, "我是身份内容");

            BootstrapService service = new BootstrapService();
            // 直接使用反射注入依赖
            setField(service, "config", new BootstrapConfig());

            List<BootstrapFile> files = service.loadBootstrapFiles(tempDir.toString());

            assertEquals(5, files.size()); // AGENTS.md, SOUL.md, USER.md, IDENTITY.md, BOOTSTRAP.md
        }

        @Test
        @DisplayName("loadBootstrapFiles 应正确识别缺失文件")
        void loadBootstrapFilesShouldIdentifyMissingFiles() throws Exception {
            // 不创建任何文件
            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            List<BootstrapFile> files = service.loadBootstrapFiles(tempDir.toString());

            assertEquals(5, files.size());
            assertTrue(files.stream().allMatch(BootstrapFile::missing));
        }

        @Test
        @DisplayName("loadBootstrapFiles 应正确加载存在文件的内容")
        void loadBootstrapFilesShouldLoadContent() throws Exception {
            String content = "这是测试内容";
            Path soulFile = tempDir.resolve("SOUL.md");
            Files.writeString(soulFile, content);

            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            List<BootstrapFile> files = service.loadBootstrapFiles(tempDir.toString());

            BootstrapFile soulFileResult = files.stream()
                    .filter(f -> f.name().equals("SOUL.md"))
                    .findFirst()
                    .orElseThrow();

            assertFalse(soulFileResult.missing());
            assertEquals(content, soulFileResult.content());
        }
    }

    @Nested
    @DisplayName("模板加载测试")
    class TemplateLoadingTests {

        @Test
        @DisplayName("loadTemplate 应能加载 classpath 中的模板")
        void loadTemplateShouldLoadFromClasspath() {
            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            var soulTemplate = service.loadTemplate("soul");
            var identityTemplate = service.loadTemplate("identity");

            assertTrue(soulTemplate.isPresent());
            assertTrue(identityTemplate.isPresent());
            assertTrue(soulTemplate.get().contains("SOUL"));
            assertTrue(identityTemplate.get().contains("IDENTITY"));
        }

        @Test
        @DisplayName("loadTemplate 应返回不存在的模板为空")
        void loadTemplateShouldReturnEmptyForNonExistent() {
            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            var template = service.loadTemplate("non_existent_template_12345");

            assertTrue(template.isEmpty());
        }
    }

    @Nested
    @DisplayName("引导完成检测测试")
    class OnboardingDetectionTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("checkOnboardingComplete 应在 IDENTITY.md 和 USER.md 都存在时返回 true")
        void checkOnboardingCompleteShouldReturnTrueWhenBothFilesExist() throws Exception {
            Files.writeString(tempDir.resolve("IDENTITY.md"), "name: Test");
            Files.writeString(tempDir.resolve("USER.md"), "user: Test");

            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            assertTrue(service.checkOnboardingComplete(tempDir.toString()));
        }

        @Test
        @DisplayName("checkOnboardingComplete 应在缺少任一文件时返回 false")
        void checkOnboardingCompleteShouldReturnFalseWhenAnyFileMissing() throws Exception {
            Files.writeString(tempDir.resolve("IDENTITY.md"), "name: Test");
            // 缺少 USER.md

            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            assertFalse(service.checkOnboardingComplete(tempDir.toString()));
        }

        @Test
        @DisplayName("checkOnboardingComplete 应在所有文件都缺失时返回 false")
        void checkOnboardingCompleteShouldReturnFalseWhenAllMissing() throws Exception {
            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            assertFalse(service.checkOnboardingComplete(tempDir.toString()));
        }

        @Test
        @DisplayName("checkOnboardingComplete 应在 workspaceDir 为空时返回 false")
        void checkOnboardingCompleteShouldReturnFalseWhenEmpty() {
            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            assertFalse(service.checkOnboardingComplete(null));
            assertFalse(service.checkOnboardingComplete(""));
        }
    }

    @Nested
    @DisplayName("Bootstrap Prompt 构建测试")
    class BootstrapPromptTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("buildBootstrapPrompt 应在无文件时返回空字符串")
        void buildBootstrapPromptShouldReturnEmptyWhenNoFiles() throws Exception {
            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            String prompt = service.buildBootstrapPrompt(tempDir.toString());

            assertEquals("", prompt);
        }

        @Test
        @DisplayName("buildBootstrapPrompt 应正确构建提示词")
        void buildBootstrapPromptShouldBuildPrompt() throws Exception {
            Files.writeString(tempDir.resolve("SOUL.md"), "我是灵魂");
            Files.writeString(tempDir.resolve("USER.md"), "用户: 测试");

            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            String prompt = service.buildBootstrapPrompt(tempDir.toString());

            assertTrue(prompt.contains("灵魂与人格"));
            assertTrue(prompt.contains("我是灵魂"));
            assertTrue(prompt.contains("用户信息"));
            assertTrue(prompt.contains("用户: 测试"));
        }
    }

    @Nested
    @DisplayName("BOOTSTRAP.md 检测测试")
    class BootstrapDetectionTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("hasBootstrap 应在 BOOTSTRAP.md 存在时返回 true")
        void hasBootstrapShouldReturnTrueWhenExists() throws Exception {
            Files.writeString(tempDir.resolve("BOOTSTRAP.md"), "引导内容");

            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            assertTrue(service.hasBootstrap(tempDir.toString()));
        }

        @Test
        @DisplayName("hasBootstrap 应在 BOOTSTRAP.md 不存在时返回 false")
        void hasBootstrapShouldReturnFalseWhenNotExists() throws Exception {
            BootstrapService service = new BootstrapService();
            setField(service, "config", new BootstrapConfig());

            assertFalse(service.hasBootstrap(tempDir.toString()));
        }
    }

    // 辅助方法：通过反射设置私有字段
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
