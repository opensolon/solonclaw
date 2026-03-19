package com.jimuqu.claw.agent.workspace;

import cn.hutool.core.io.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证工作区引导文件驱动的提示词组装逻辑。
 */
class WorkspacePromptServiceTest {
    /**
     * 验证系统提示词会包含工作区中的引导文件和长期记忆文件。
     *
     * @param tempDir 临时工作区
     */
    @Test
    void buildsPromptFromWorkspaceFiles(@TempDir Path tempDir) {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        WorkspacePromptService promptService = new WorkspacePromptService(workspaceService, "基础系统提示");

        FileUtil.writeUtf8String(
                "---\n"
                        + "\n"
                        + "# IDENTITY.md - 我是谁？\n"
                        + "\n"
                        + "- **名称：** Xiaolongxia\n"
                        + "- **生物类型：** AI 助手\n",
                workspaceService.fileInWorkspace(WorkspacePromptService.IDENTITY_FILE)
        );
        FileUtil.writeUtf8String("# AGENTS\n先阅读 SOUL.md 和 USER.md。", workspaceService.fileInWorkspace(WorkspacePromptService.AGENTS_FILE));
        FileUtil.writeUtf8String("# SOUL\n回答简洁但不生硬。", workspaceService.fileInWorkspace(WorkspacePromptService.SOUL_FILE));
        FileUtil.writeUtf8String("# USER\n用户偏好中文回复。", workspaceService.fileInWorkspace(WorkspacePromptService.USER_FILE));
        FileUtil.writeUtf8String("# TOOLS\n记录本地环境备注。", workspaceService.fileInWorkspace(WorkspacePromptService.TOOLS_FILE));
        FileUtil.writeUtf8String("# HEARTBEAT\n", workspaceService.fileInWorkspace(WorkspacePromptService.HEARTBEAT_FILE));
        FileUtil.writeUtf8String("# BOOTSTRAP\n第一次对话用于确定名字和风格。", workspaceService.fileInWorkspace(WorkspacePromptService.BOOTSTRAP_FILE));
        FileUtil.writeUtf8String("# MEMORY\n用户偏好中文回复。", workspaceService.fileInWorkspace(WorkspacePromptService.MEMORY_FILE));
        LocalDate today = LocalDate.now();
        FileUtil.writeUtf8String(
                "# DAILY\n昨天发生了重要事情。",
                workspaceService.fileInWorkspace("memory/" + DateTimeFormatter.ISO_LOCAL_DATE.format(today.minusDays(1)) + ".md")
        );
        FileUtil.writeUtf8String(
                "# DAILY\n今天需要继续跟进。",
                workspaceService.fileInWorkspace("memory/" + DateTimeFormatter.ISO_LOCAL_DATE.format(today) + ".md")
        );

        String prompt = promptService.buildSystemPrompt();

        assertTrue(prompt.contains("基础系统提示"));
        assertTrue(prompt.contains("先阅读 SOUL.md 和 USER.md"));
        assertTrue(prompt.contains("回答简洁但不生硬"));
        assertTrue(prompt.contains("第一次对话用于确定名字和风格"));
        assertTrue(prompt.contains("用户偏好中文回复"));
        assertTrue(prompt.contains("昨天发生了重要事情。"));
        assertTrue(prompt.contains("今天需要继续跟进。"));
        assertEquals("Xiaolongxia", promptService.resolveAgentName());
    }

    /**
     * 验证全新工作区会初始化内置模板，并包含首次对话引导文件。
     *
     * @param tempDir 临时工作区
     */
    @Test
    void createsBundledTemplatesForBrandNewWorkspace(@TempDir Path tempDir) {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        new WorkspacePromptService(workspaceService, null);

        String agents = FileUtil.readUtf8String(workspaceService.fileInWorkspace(WorkspacePromptService.AGENTS_FILE));
        String bootstrap = FileUtil.readUtf8String(workspaceService.fileInWorkspace(WorkspacePromptService.BOOTSTRAP_FILE));
        String memory = FileUtil.readUtf8String(workspaceService.fileInWorkspace(WorkspacePromptService.MEMORY_FILE));

        assertTrue(agents.contains("这个文件夹是你的家。请如此对待。"));
        assertTrue(bootstrap.contains("你刚刚醒来。是时候弄清楚自己是谁了。"));
        assertTrue(memory.contains("这里记录需要长期记住的事实"));
    }

    /**
     * 验证未配置系统提示词时会回退到默认基础提示词。
     *
     * @param tempDir 临时工作区
     */
    @Test
    void fallsBackToDefaultBaseSystemPromptWhenConfigMissing(@TempDir Path tempDir) {
        AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
        WorkspacePromptService promptService = new WorkspacePromptService(workspaceService, "  ");

        String prompt = promptService.buildSystemPrompt();

        assertTrue(prompt.contains("你是在 SolonClaw 内运行的个人助理。"));
        assertTrue(prompt.contains("## 工具使用"));
    }
}
