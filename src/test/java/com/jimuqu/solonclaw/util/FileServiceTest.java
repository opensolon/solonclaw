package com.jimuqu.solonclaw.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileService 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("FileService 单元测试")
class FileServiceTest {

    private FileService fileService;
    private TempTokenService tempTokenService;

    @BeforeEach
    void setUp() {
        tempTokenService = new TempTokenService();
        fileService = new FileService();
        // 手动设置依赖（避免使用 @Inject 在非 Solon 环境中）
        try {
            var field = FileService.class.getDeclaredField("tempTokenService");
            field.setAccessible(true);
            field.set(fileService, tempTokenService);
        } catch (Exception e) {
            fail("设置依赖失败: " + e.getMessage());
        }
    }

    @Nested
    @DisplayName("图片路径提取测试")
    class ImagePathExtractionTests {

        @Test
        @DisplayName("应能从文本中提取所有图片路径")
        void shouldExtractAllImagePaths() {
            String text = "请看这张图片 /tmp/test.png 和另一张 /home/user/image.jpg";

            var paths = fileService.extractImagePaths(text);

            assertEquals(2, paths.size(), "应提取到 2 个图片路径");
            assertTrue(paths.contains("/tmp/test.png"), "应包含第一个图片路径");
            assertTrue(paths.contains("/home/user/image.jpg"), "应包含第二个图片路径");
        }

        @Test
        @DisplayName("空文本应返回空列表")
        void emptyTextShouldReturnEmptyList() {
            assertTrue(fileService.extractImagePaths(null).isEmpty(), "null 应返回空列表");
            assertTrue(fileService.extractImagePaths("").isEmpty(), "空字符串应返回空列表");
            assertTrue(fileService.extractImagePaths("   ").isEmpty(), "纯空格应返回空列表");
        }

        @Test
        @DisplayName("不含图片路径的文本应返回空列表")
        void textWithoutImagesShouldReturnEmptyList() {
            String text = "这是一段普通文本，不包含任何图片路径";

            var paths = fileService.extractImagePaths(text);

            assertTrue(paths.isEmpty(), "应不包含任何图片路径");
        }

        @Test
        @DisplayName("应支持多种图片格式")
        void shouldSupportMultipleImageFormats() {
            String text = "图片: /test.png, /test.jpg, /test.jpeg, /test.gif, /test.webp, /test.svg";

            var paths = fileService.extractImagePaths(text);

            assertEquals(6, paths.size(), "应提取到 6 个图片路径");
        }

        @Test
        @DisplayName("应支持大写扩展名")
        void shouldSupportUppercaseExtensions() {
            String text = "图片: /test.PNG, /test.JPG, /test.GIF";

            var paths = fileService.extractImagePaths(text);

            assertEquals(3, paths.size(), "应支持大写扩展名");
        }

        @Test
        @DisplayName("应忽略不支持格式的文件")
        void shouldIgnoreUnsupportedFormats() {
            String text = "文件: /test.txt, /test.pdf, /image.png, /photo.jpg";

            var paths = fileService.extractImagePaths(text);

            assertEquals(2, paths.size(), "应只提取图片格式");
            assertTrue(paths.stream().allMatch(p -> p.endsWith(".png") || p.endsWith(".jpg")));
        }

        @Test
        @DisplayName("路径开头包含波浪线应被提取")
        void pathStartingWithTildeShouldBeExtracted() {
            String text = "图片: ~/Pictures/test.png";

            var paths = fileService.extractImagePaths(text);

            assertEquals(1, paths.size());
            assertEquals("~/Pictures/test.png", paths.get(0));
        }

        @Test
        @DisplayName("应能提取带有中文的图片路径")
        void shouldExtractPathsWithChinese() {
            String text = "图片: /home/中文文件夹/图片.png";

            var paths = fileService.extractImagePaths(text);

            assertEquals(1, paths.size());
        }
    }

    @Nested
    @DisplayName("图片路径检查测试")
    class ImagePathCheckTests {

        @Test
        @DisplayName("包含图片路径应返回 true")
        void shouldReturnTrueWhenContainsImagePath() {
            String text = "请查看这张图片 /tmp/test.png";

            assertTrue(fileService.containsImagePath(text));
        }

        @Test
        @DisplayName("不包含图片路径应返回 false")
        void shouldReturnFalseWhenNotContainsImagePath() {
            String text = "这是一段普通文本";

            assertFalse(fileService.containsImagePath(text));
        }

        @Test
        @DisplayName("空文本应返回 false")
        void emptyTextShouldReturnFalse() {
            assertFalse(fileService.containsImagePath(null));
            assertFalse(fileService.containsImagePath(""));
        }
    }

    @Nested
    @DisplayName("单图片路径提取测试")
    class SingleImagePathExtractionTests {

        @Test
        @DisplayName("应能提取第一个图片路径")
        void shouldExtractFirstImagePath() {
            String text = "第一张 /first.png 和第二张 /second.jpg";

            String path = fileService.extractImagePath(text);

            assertEquals("/first.png", path);
        }

        @Test
        @DisplayName("未找到图片路径应返回 null")
        void shouldReturnNullWhenNoImagePathFound() {
            String text = "没有图片路径的文本";

            assertNull(fileService.extractImagePath(text));
        }

        @Test
        @DisplayName("空文本应返回 null")
        void emptyTextShouldReturnNull() {
            assertNull(fileService.extractImagePath(null));
            assertNull(fileService.extractImagePath(""));
        }
    }

    @Nested
    @DisplayName("临时访问链接生成测试")
    class TempAccessUrlGenerationTests {

        @Test
        @DisplayName("对于不存在的文件应返回 null")
        void shouldReturnNullForNonExistentFile() {
            String url = fileService.generateTempAccessUrl("/nonexistent/file.png", 300);

            assertNull(url, "不存在的文件应返回 null");
        }

        @Test
        @DisplayName("对于不支持的格式应返回 null")
        void shouldReturnNullForUnsupportedFormat() {
            String url = fileService.generateTempAccessUrl("pom.xml", 300);

            assertNull(url, "不支持的格式应返回 null");
        }
    }

    @Nested
    @DisplayName("内容图片处理测试")
    class ContentImageProcessingTests {

        @Test
        @DisplayName("空内容应保持不变")
        void emptyContentShouldRemainUnchanged() {
            String result = fileService.processImagesInContent(null);
            assertEquals(null, result);

            result = fileService.processImagesInContent("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("不含图片的内容应保持不变")
        void contentWithoutImagesShouldRemainUnchanged() {
            String content = "这是一段普通文本";

            String result = fileService.processImagesInContent(content);

            assertEquals(content, result);
        }

        @Test
        @DisplayName("应正确处理多个相同路径（去重）")
        void shouldHandleDuplicatePaths() {
            // 由于文件不存在，不会生成链接，但逻辑应正常执行
            String content = "图片路径 /tmp/test.png 出现多次 /tmp/test.png";

            String result = fileService.processImagesInContent(content);

            assertEquals(content, result); // 文件不存在，保持原内容
        }
    }
}
