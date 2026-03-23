package com.jimuqu.claw.agent.runtime;

import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.registry.ActiveTaskEntry;
import com.jimuqu.claw.agent.runtime.registry.ActiveTaskRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActiveTaskRegistryTest {

    @Test
    void registersUpdatesAndCompletesActiveTask(@TempDir Path tempDir) {
        ActiveTaskRegistry registry = new ActiveTaskRegistry(new File(tempDir.toFile(), "runs"));

        AgentRun childRun = new AgentRun();
        childRun.setRunId("child-1");
        childRun.setParentRunId("parent-1");
        childRun.setParentSessionKey("session-1");
        childRun.setSessionKey("session-1:subtask:child-1");
        childRun.setTaskTitle("调研 Solon");
        childRun.setTaskDescription("阅读 README 并总结核心模块");
        childRun.setStatus(RunStatus.QUEUED);
        childRun.setCreatedAt(123L);

        registry.register("session-1", childRun);
        registry.updateStatus("child-1", RunStatus.RUNNING);
        registry.updateProgress("child-1", "信息收集", "已读取 README");

        List<ActiveTaskEntry> tasks = registry.getActiveTasks("session-1");
        assertEquals(1, tasks.size());
        assertEquals("调研 Solon", tasks.get(0).getTaskTitle());
        assertEquals(RunStatus.RUNNING, tasks.get(0).getStatus());
        assertEquals("信息收集", tasks.get(0).getLatestPhase());
        assertEquals("已读取 README", tasks.get(0).getLatestProgressDetail());
        assertTrue(tasks.get(0).getLatestProgressAt() > 0);

        registry.requestCancel("child-1");
        assertTrue(registry.isCancelRequested("child-1"));

        registry.markCompleted("child-1", RunStatus.SUCCEEDED);
        assertNull(registry.getEntry("child-1"));
        assertTrue(registry.getActiveTasks("session-1").isEmpty());
    }
}
