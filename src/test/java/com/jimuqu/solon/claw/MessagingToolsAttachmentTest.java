package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.tool.runtime.MessagingTools;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MessagingToolsAttachmentTest {
    @Test
    void shouldDeliverMediaPathsAsAttachments() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools =
                new MessagingTools(
                        env.deliveryService,
                        "MEMORY:chat-1:user-1",
                        new AttachmentCacheService(env.appConfig),
                        env.appConfig);

        Path tempDir = new File(env.appConfig.getRuntime().getCacheDir(), "tool-media").toPath();
        Files.createDirectories(tempDir);
        File image = tempDir.resolve("demo.png").toFile();
        File voice = tempDir.resolve("note.silk").toFile();
        Files.write(image.toPath(), new byte[] {1, 2, 3});
        Files.write(voice.toPath(), new byte[] {4, 5, 6});

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            tools.sendMessage(
                    null, null, "请发送附件", Arrays.asList("demo.png", voice.getAbsolutePath()), null);
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        DeliveryRequest request = env.memoryChannelAdapter.getLastRequest();
        assertThat(request.getText()).isEqualTo("请发送附件");
        assertThat(request.getAttachments()).hasSize(2);
        assertThat(request.getAttachments().get(0).getKind()).isEqualTo("image");
        assertThat(request.getAttachments().get(0).getLocalPath()).endsWith("demo.png");
        assertThat(request.getAttachments().get(1).getKind()).isEqualTo("voice");
        assertThat(request.getAttachments().get(1).getLocalPath()).endsWith("note.silk");
    }

    @Test
    void shouldAllowTextOnlyWithoutAttachments() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools =
                new MessagingTools(
                        env.deliveryService,
                        "MEMORY:chat-1:user-1",
                        new AttachmentCacheService(env.appConfig),
                        env.appConfig);

        tools.sendMessage(null, null, "纯文本", Collections.<String>emptyList(), null);

        DeliveryRequest request = env.memoryChannelAdapter.getLastRequest();
        assertThat(request.getText()).isEqualTo("纯文本");
        assertThat(request.getAttachments()).isEmpty();
    }

    @Test
    void shouldPassChannelExtrasJson() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools =
                new MessagingTools(
                        env.deliveryService,
                        "MEMORY:chat-1:user-1",
                        new AttachmentCacheService(env.appConfig),
                        env.appConfig);

        tools.sendMessage(
                null,
                null,
                "发卡片",
                Collections.<String>emptyList(),
                "{\"mode\":\"ai_card\",\"cardTemplateId\":\"tpl-1\",\"cardData\":{\"title\":\"demo\"}}");

        DeliveryRequest request = env.memoryChannelAdapter.getLastRequest();
        assertThat(request.getPlatform().name()).isEqualTo("MEMORY");
        assertThat(request.getChannelExtras()).containsEntry("mode", "ai_card");
        assertThat(request.getChannelExtras()).containsEntry("cardTemplateId", "tpl-1");
        assertThat(((Map<?, ?>) request.getChannelExtras().get("cardData")).get("title"))
                .isEqualTo("demo");
    }

    @Test
    void shouldResolveGeneratedPdfFromRuntimeCachePdfDir() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools =
                new MessagingTools(
                        env.deliveryService,
                        "MEMORY:chat-1:user-1",
                        new AttachmentCacheService(env.appConfig),
                        env.appConfig);

        File pdfDir = new File(env.appConfig.getRuntime().getCacheDir(), "pdf");
        assertThat(pdfDir.mkdirs() || pdfDir.isDirectory()).isTrue();
        File pdf = new File(pdfDir, "solon_research_summary.pdf");
        Files.write(pdf.toPath(), new byte[] {1, 2, 3});

        tools.sendMessage(
                null,
                null,
                "发送报告",
                Collections.singletonList("/app/solon_research_summary.pdf"),
                null);

        DeliveryRequest request = env.memoryChannelAdapter.getLastRequest();
        assertThat(request.getAttachments()).hasSize(1);
        assertThat(request.getAttachments().get(0).getLocalPath()).isEqualTo(pdf.getAbsolutePath());
        assertThat(request.getAttachments().get(0).getMimeType()).isEqualTo("application/pdf");
    }

    @Test
    void shouldImportGeneratedPdfFromRuntimeRootIntoMediaCache() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools =
                new MessagingTools(
                        env.deliveryService,
                        "MEMORY:chat-1:user-1",
                        new AttachmentCacheService(env.appConfig),
                        env.appConfig);

        File pdf = new File(env.appConfig.getRuntime().getHome(), "jimuqu_agent_report.pdf");
        Files.write(pdf.toPath(), new byte[] {1, 2, 3});

        tools.sendMessage(
                null,
                null,
                "发送报告",
                Collections.singletonList(pdf.getAbsolutePath()),
                null);

        DeliveryRequest request = env.memoryChannelAdapter.getLastRequest();
        assertThat(request.getAttachments()).hasSize(1);
        assertThat(request.getAttachments().get(0).getLocalPath())
                .contains(File.separator + "cache" + File.separator + "media" + File.separator);
        assertThat(request.getAttachments().get(0).getOriginalName())
                .endsWith("jimuqu_agent_report.pdf");
        assertThat(request.getAttachments().get(0).getMimeType()).isEqualTo("application/pdf");
    }

    @Test
    void shouldRejectRuntimeInternalFilesAsAttachments() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools =
                new MessagingTools(
                        env.deliveryService,
                        "MEMORY:chat-1:user-1",
                        new AttachmentCacheService(env.appConfig),
                        env.appConfig);

        File config = new File(env.appConfig.getRuntime().getHome(), "config.yml");
        Files.write(config.toPath(), "secret: value".getBytes("UTF-8"));

        assertThatThrownBy(
                        () ->
                                tools.sendMessage(
                                        null,
                                        null,
                                        "发送配置",
                                        Collections.singletonList(config.getAbsolutePath()),
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside runtime cache");
    }
}
