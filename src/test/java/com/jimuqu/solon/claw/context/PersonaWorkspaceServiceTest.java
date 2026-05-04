package com.jimuqu.solon.claw.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.constants.ContextFileConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonaWorkspaceServiceTest {
    @TempDir Path tempDir;

    @Test
    void readsAndWritesManagedFiles() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());

        assertThat(service.orderedKeys())
                .containsExactly(
                        ContextFileConstants.KEY_AGENTS,
                        ContextFileConstants.KEY_SOUL,
                        ContextFileConstants.KEY_IDENTITY,
                        ContextFileConstants.KEY_USER,
                        ContextFileConstants.KEY_TOOLS,
                        ContextFileConstants.KEY_HEARTBEAT,
                        ContextFileConstants.KEY_MEMORY,
                        ContextFileConstants.KEY_MEMORY_TODAY);
        assertThat(service.exists(ContextFileConstants.KEY_AGENTS)).isTrue();
        assertThat(service.read(ContextFileConstants.KEY_AGENTS)).contains("# AGENTS.md - 你的工作区");
        assertThat(service.read(ContextFileConstants.KEY_SOUL)).contains("# SOUL.md - 你是谁");
        assertThat(service.read(ContextFileConstants.KEY_IDENTITY))
                .contains("# IDENTITY.md - 我是谁？");
        assertThat(service.read(ContextFileConstants.KEY_USER)).contains("# USER.md - 关于你的用户");
        assertThat(service.read(ContextFileConstants.KEY_TOOLS)).contains("# TOOLS.md - 本地笔记");
        assertThat(service.read(ContextFileConstants.KEY_HEARTBEAT)).contains("跳过心跳轮询");
        assertThat(service.read(ContextFileConstants.KEY_MEMORY)).isEmpty();
        assertThat(service.read(ContextFileConstants.KEY_MEMORY_TODAY))
                .contains("# " + LocalDate.now().toString());

        service.write(ContextFileConstants.KEY_AGENTS, "# AGENTS\n");

        assertThat(service.exists(ContextFileConstants.KEY_AGENTS)).isTrue();
        assertThat(service.read(ContextFileConstants.KEY_AGENTS)).isEqualTo("# AGENTS\n");
        assertThat(service.file(ContextFileConstants.KEY_AGENTS).getName())
                .isEqualTo(ContextFileConstants.FILE_AGENTS);
    }

    @Test
    void doesNotOverwriteExistingFilesWhenRecreated() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());
        service.write(ContextFileConstants.KEY_SOUL, "custom soul");

        PersonaWorkspaceService reloaded = new PersonaWorkspaceService(appConfig());

        assertThat(reloaded.read(ContextFileConstants.KEY_SOUL)).isEqualTo("custom soul");
    }

    @Test
    void restoresFileBackToTemplate() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());
        service.write(ContextFileConstants.KEY_USER, "custom user");

        service.restoreTemplate(ContextFileConstants.KEY_USER);

        assertThat(service.read(ContextFileConstants.KEY_USER)).contains("# USER.md - 关于你的用户");
        assertThat(service.read(ContextFileConstants.KEY_USER)).doesNotContain("custom user");
    }

    @Test
    void rejectsUnknownFileKeys() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());

        assertThatThrownBy(
                        new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
                            @Override
                            public void call() {
                                service.read("unknown");
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void continuesWhenWorkspaceCannotBeSeeded() throws IOException {
        File blocker = new File(tempDir.toFile(), "runtime-blocker");
        Files.write(blocker.toPath(), new byte[0]);
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(blocker.getAbsolutePath());
        config.getRuntime().setContextDir(new File(tempDir.toFile(), "context").getAbsolutePath());

        final PersonaWorkspaceService[] holder = new PersonaWorkspaceService[1];
        assertThatCode(
                        new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
                            @Override
                            public void call() {
                                holder[0] = new PersonaWorkspaceService(config);
                            }
                        })
                .doesNotThrowAnyException();

        assertThat(holder[0].exists(ContextFileConstants.KEY_AGENTS)).isFalse();
        assertThat(holder[0].read(ContextFileConstants.KEY_AGENTS)).contains("# AGENTS.md - 你的工作区");
    }

    private AppConfig appConfig() {
        AppConfig config = new AppConfig();
        File runtimeDir = new File(tempDir.toFile(), "runtime");
        File contextDir = new File(runtimeDir, "context");
        config.getRuntime().setHome(runtimeDir.getAbsolutePath());
        config.getRuntime().setContextDir(contextDir.getAbsolutePath());
        return config;
    }
}
