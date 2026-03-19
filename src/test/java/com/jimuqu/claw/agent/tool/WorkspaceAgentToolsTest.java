package com.jimuqu.claw.agent.tool;

import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceAgentToolsTest {
    @Test
    void readsWritesAndEditsWithinWorkspace(@TempDir Path tempDir) throws Exception {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        WorkspaceAgentTools tools = new WorkspaceAgentTools(workspaceService);

        String writeResult = tools.writeFile("notes/test.txt", "hello");
        assertTrue(writeResult.contains("已写入文件"));

        String readResult = tools.readFile("notes/test.txt");
        assertTrue(readResult.contains("hello"));

        String editResult = tools.editFile("notes/test.txt", "hello", "world");
        assertTrue(editResult.contains("已修改文件"));

        String edited = new String(Files.readAllBytes(tempDir.resolve("notes").resolve("test.txt")), StandardCharsets.UTF_8);
        assertTrue(edited.contains("world"));
    }

    @Test
    void rejectsPathsOutsideWorkspace(@TempDir Path tempDir) {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        WorkspaceAgentTools tools = new WorkspaceAgentTools(workspaceService);

        assertThrows(IllegalArgumentException.class, () -> tools.writeFile("..\\escape.txt", "x"));
    }
}
