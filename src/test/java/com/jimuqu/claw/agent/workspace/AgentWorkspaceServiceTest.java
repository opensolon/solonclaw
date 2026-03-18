package com.jimuqu.claw.agent.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证工作区路径解析规则。
 */
class AgentWorkspaceServiceTest {
    /**
     * 验证相对路径会收敛到工作区目录下。
     *
     * @param tempDir 临时目录
     */
    @Test
    void resolvesRelativePathWithinWorkspace(@TempDir Path tempDir) {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());

        File runtimeDir = workspaceService.resolveWithinWorkspace("./runtime", "runtime");

        assertEquals(tempDir.resolve("runtime").toFile().getAbsolutePath(), runtimeDir.getAbsolutePath());
        assertTrue(runtimeDir.exists());
    }

    /**
     * 验证绝对路径会按原样返回。
     *
     * @param tempDir 临时目录
     */
    @Test
    void keepsAbsolutePathAsIs(@TempDir Path tempDir) {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        File absolute = tempDir.resolve("external-runtime").toFile();

        File runtimeDir = workspaceService.resolveWithinWorkspace(absolute.getAbsolutePath(), "runtime");

        assertEquals(absolute.getAbsolutePath(), runtimeDir.getAbsolutePath());
        assertTrue(runtimeDir.exists());
    }
}
