package com.jimuqu.claw.config;

import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.skills.cli.CliSkillProvider;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolonClawConfigTest {
    @Test
    void cliSandboxModeDefaultsToTrue() {
        SolonClawProperties properties = new SolonClawProperties();

        assertTrue(properties.getAgent().getTools().isSandboxMode());
    }

    @Test
    void cliSkillProviderAppliesSandboxMode(@TempDir Path tempDir) throws Exception {
        SolonClawConfig config = new SolonClawConfig();
        SolonClawProperties properties = new SolonClawProperties();
        properties.getAgent().getTools().setSandboxMode(false);

        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        CliSkillProvider skillProvider = config.cliSkillProvider(workspaceService, properties);

        Field sandboxModeField = skillProvider.getTerminalSkill().getClass().getDeclaredField("sandboxMode");
        sandboxModeField.setAccessible(true);

        assertFalse((Boolean) sandboxModeField.get(skillProvider.getTerminalSkill()));
    }

    @Test
    void reactLoggingInterceptorBeanCreated() {
        SolonClawConfig config = new SolonClawConfig();

        ReActInterceptor interceptor = config.reActLoggingInterceptor();

        Assertions.assertNotNull(interceptor);
    }
}
