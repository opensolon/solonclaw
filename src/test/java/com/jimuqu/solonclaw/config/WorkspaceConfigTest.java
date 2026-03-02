package com.jimuqu.solonclaw.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkspaceConfig 单元测试
 *
 * @author SolonClaw
 */
@DisplayName("WorkspaceConfig 单元测试")
class WorkspaceConfigTest {

    @Nested
    @DisplayName("WorkspaceInfo 记录类测试")
    class WorkspaceInfoRecordTests {

        @Test
        @DisplayName("WorkspaceInfo getter 应返回正确值")
        void gettersShouldReturnCorrectValues() {
            Path workspace = Paths.get("/test/workspace");
            Path mcpConfigFile = Paths.get("/test/mcp.json");
            Path skillsDir = Paths.get("/test/skills");
            Path jobsFile = Paths.get("/test/jobs.json");
            Path jobHistoryFile = Paths.get("/test/job-history.json");
            Path databaseFile = Paths.get("/test/memory.db");
            Path shellWorkspace = Paths.get("/test/workspace/shell");
            Path logsDir = Paths.get("/test/logs");

            WorkspaceConfig.WorkspaceInfo info = new WorkspaceConfig.WorkspaceInfo(
                    workspace,
                    mcpConfigFile,
                    skillsDir,
                    jobsFile,
                    jobHistoryFile,
                    databaseFile,
                    shellWorkspace,
                    logsDir
            );

            assertEquals(workspace, info.workspace());
            assertEquals(mcpConfigFile, info.mcpConfigFile());
            assertEquals(skillsDir, info.skillsDir());
            assertEquals(jobsFile, info.jobsFile());
            assertEquals(jobHistoryFile, info.jobHistoryFile());
            assertEquals(databaseFile, info.databaseFile());
            assertEquals(shellWorkspace, info.shellWorkspace());
            assertEquals(logsDir, info.logsDir());
        }

        @Test
        @DisplayName("WorkspaceInfo 应能处理 null 值")
        void shouldHandleNullValues() {
            WorkspaceConfig.WorkspaceInfo info = new WorkspaceConfig.WorkspaceInfo(
                    null, null, null, null, null, null, null, null
            );

            assertNull(info.workspace());
            assertNull(info.mcpConfigFile());
            assertNull(info.skillsDir());
            assertNull(info.jobsFile());
            assertNull(info.jobHistoryFile());
            assertNull(info.databaseFile());
            assertNull(info.shellWorkspace());
            assertNull(info.logsDir());
        }
    }

    @Nested
    @DisplayName("mkdirs 方法测试")
    class MkdirsTests {

        @Test
        @DisplayName("mkdirs 应不为空方法")
        void mkdirsShouldNotThrow() {
            Path workspace = Paths.get("/test/workspace");
            WorkspaceConfig.WorkspaceInfo info = new WorkspaceConfig.WorkspaceInfo(
                    workspace, Paths.get("/test/mcp.json"), Paths.get("/test/skills"),
                    Paths.get("/test/jobs.json"), Paths.get("/test/job-history.json"),
                    Paths.get("/test/memory.db"), Paths.get("/test/shell"),
                    Paths.get("/test/logs")
            );

            // mkdirs 方法应该存在且可调用
            assertDoesNotThrow(info::mkdirs, "mkdirs 不应抛出异常");
        }

        @Test
        @DisplayName("mkdirs 应处理 null workspace")
        void mkdirsShouldHandleNullWorkspace() {
            WorkspaceConfig.WorkspaceInfo info = new WorkspaceConfig.WorkspaceInfo(
                    null, null, null, null, null, null, null, null
            );

            // workspace 为 null 时，mkdirs 应该抛出异常或安全处理
            assertThrows(NullPointerException.class, info::mkdirs,
                    "null workspace 应抛出 NullPointerException");
        }
    }

    @Nested
    @DisplayName("路径构建测试")
    class PathConstructionTests {

        @Test
        @DisplayName("应能正确构建相对路径")
        void shouldBuildRelativePaths() {
            Path workspace = Paths.get("/test/workspace");
            Path mcpConfigFile = Paths.get("/test/mcp.json");

            WorkspaceConfig.WorkspaceInfo info = new WorkspaceConfig.WorkspaceInfo(
                    workspace, mcpConfigFile, null, null, null, null, null, null
            );

            assertEquals(mcpConfigFile, info.mcpConfigFile());
        }

        @Test
        @DisplayName("应能正确构建绝对路径")
        void shouldBuildAbsolutePaths() {
            Path workspace = Paths.get("/test/workspace").toAbsolutePath();

            WorkspaceConfig.WorkspaceInfo info = new WorkspaceConfig.WorkspaceInfo(
                    workspace, null, null, null, null, null, null, null
            );

            assertEquals(workspace, info.workspace());
        }

        @Test
        @DisplayName("应能正确构建子目录路径")
        void shouldBuildSubdirectoryPaths() {
            Path workspace = Paths.get("/test/workspace");
            Path skillsDir = Paths.get("/test/skills");

            WorkspaceConfig.WorkspaceInfo info = new WorkspaceConfig.WorkspaceInfo(
                    workspace, null, skillsDir, null, null, null, null, null
            );

            assertEquals(skillsDir, info.skillsDir());
        }
    }
}