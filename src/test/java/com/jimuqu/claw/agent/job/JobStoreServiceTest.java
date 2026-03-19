package com.jimuqu.claw.agent.job;

import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobStoreServiceTest {
    @Test
    void persistsJobsIntoWorkspaceJson(@TempDir Path tempDir) {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        JobStoreService storeService = new JobStoreService(workspaceService);

        JobDefinition definition = new JobDefinition();
        definition.setName("demo");
        definition.setMode("once_delay");
        definition.setScheduleValue("1000");
        definition.setPrompt("hello");
        definition.setSessionKey("dingtalk:private:demo");
        definition.setReplyTarget(new ReplyTarget(ChannelType.DINGTALK, ConversationType.PRIVATE, "cid", "uid"));
        definition.setEnabled(true);
        definition.setCreatedAt(1L);
        definition.setUpdatedAt(2L);

        storeService.save(definition);

        JobDefinition saved = storeService.get("demo");
        assertNotNull(saved);
        assertEquals("once_delay", saved.getMode());
        assertEquals("1000", saved.getScheduleValue());
        assertEquals("hello", saved.getPrompt());
        assertTrue(storeService.getJobsFile().exists());
    }
}

