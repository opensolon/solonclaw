package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.context.AsyncSkillLearningService;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.support.FakeLlmGateway;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.tool.runtime.MemoryTools;
import com.jimuqu.solon.claw.tool.runtime.SkillTools;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

public class MemoryAndSkillsTest {
    @Test
    void shouldSupportCategorizedSkillsAndDefaultVisibility() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.localSkillService.createSkill("root-skill", null, skill("root-skill", "root skill"));
        env.localSkillService.createSkill("deploy", "ops", skill("deploy", "ops skill"));

        assertThat(env.localSkillService.listSkillNames()).contains("root-skill", "ops/deploy");
        assertThat(env.localSkillService.viewSkill("ops/deploy", null).getContent())
                .contains("ops skill");
        assertThat(env.localSkillService.renderSkillIndexPrompt("MEMORY:room:user"))
                .contains("ops/deploy");

        env.localSkillService.disable("MEMORY:room:user", "root-skill");
        assertThat(env.localSkillService.renderSkillIndexPrompt("MEMORY:room:user"))
                .doesNotContain("root-skill");
    }

    @Test
    void shouldRefreshMemorySnapshotOnNextTurn() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FakeLlmGateway fake = (FakeLlmGateway) env.llmGateway;

        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
        env.send("admin-chat", "admin-user", "first round");
        assertThat(fake.lastSystemPrompt).doesNotContain("冻结测试记忆");

        env.memoryService.add("memory", "冻结测试记忆");
        env.send("admin-chat", "admin-user", "second round");
        assertThat(fake.lastSystemPrompt).contains("冻结测试记忆");
    }

    @Test
    void shouldSyncSuccessfulTurnsIntoTodayMemory() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.memoryManager.syncTurn("MEMORY:daily-room:daily-user", "记住今天的排查结论", "已经确认会写入今日记忆");

        String today = env.memoryService.read("today");
        assertThat(today).contains("# ");
        assertThat(today).contains("MEMORY:daily-room:daily-user");
        assertThat(today).contains("记住今天的排查结论");
        assertThat(today).contains("已经确认会写入今日记忆");
    }

    @Test
    void shouldUpdateTodayMemoryAfterGatewayReply() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.send("memory-chat", "memory-user", "hello");
        env.send("memory-chat", "memory-user", "/pairing claim-admin");
        env.send("memory-chat", "memory-user", "检查记忆文件自动维护");

        String today = env.memoryService.read("today");
        assertThat(today).contains("MEMORY:memory-chat:memory-user");
        assertThat(today).contains("检查记忆文件自动维护");
        assertThat(today).contains("echo:检查记忆文件自动维护");
    }

    @Test
    void shouldUpdateTodayMemoryWhenOrchestratorIsCalledDirectly() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.conversationOrchestrator.handleIncoming(
                env.message("dashboard", "direct-session", "dashboard 直接调用也要维护记忆"));

        String today = env.memoryService.read("today");
        assertThat(today).contains("MEMORY:dashboard:direct-session");
        assertThat(today).contains("dashboard 直接调用也要维护记忆");
        assertThat(today).contains("echo:dashboard 直接调用也要维护记忆");
    }

    @Test
    void shouldUpdateTodayMemoryWhenPendingRunResumes() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.conversationOrchestrator.handleIncoming(
                env.message("approval-room", "approval-user", "执行需要审批的清理命令"));
        GatewayReply reply =
                env.conversationOrchestrator.resumePending(
                        "MEMORY:approval-room:approval-user");

        String today = env.memoryService.read("today");
        assertThat(reply.getContent()).contains("echo:resume");
        assertThat(today).contains("MEMORY:approval-room:approval-user");
        assertThat(today).contains("执行需要审批的清理命令");
        assertThat(today).contains("echo:resume");
    }

    @Test
    void shouldRejectTransientMemoryEntries() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        String response = env.memoryService.add("memory", "本会话临时 rollback TODO，稍后再删");

        assertThat(response).contains("不会写入长期记忆");
        assertThat(env.memoryService.read("memory")).isBlank();
    }

    @Test
    void shouldReturnRecoverableSkillToolErrorsInsteadOfThrowing() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.localSkillService.createSkill("demo-skill", null, skill("demo-skill", "demo"));
        SkillTools tools =
                new SkillTools(
                        env.localSkillService,
                        env.checkpointService,
                        env.sessionRepository,
                        "MEMORY:room:user");

        String missingSkill = tools.skillView("missing-skill", null);
        String invalidPath = tools.skillView("demo-skill", "../outside.txt");

        assertThat(missingSkill).contains("\"success\":false");
        assertThat(missingSkill).contains("Skill not found");
        assertThat(invalidPath).contains("\"success\":false");
        assertThat(invalidPath).contains("Invalid skill file path");
    }

    @Test
    void shouldFilterPromptAndSkillToolsByAgentSkills() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.localSkillService.createSkill("global-skill", null, skill("global-skill", "global"));
        File agentSkillDir =
                FileUtil.file(
                        env.appConfig.getRuntime().getHome(),
                        "agents",
                        "coder",
                        "skills",
                        "agent-only");
        FileUtil.mkdir(agentSkillDir);
        FileUtil.writeUtf8String(
                skill("agent-only", "agent local"), FileUtil.file(agentSkillDir, "SKILL.md"));

        env.agentProfileService.createAgent("coder", "你是代码助手。");
        env.send("skill-room", "skill-user", "hello");
        env.send("skill-room", "skill-user", "/pairing claim-admin");
        env.send("skill-room", "skill-user", "/agent skills coder agent-only");
        env.send("skill-room", "skill-user", "/agent coder");
        AgentRuntimeScope scope =
                env.agentRuntimeService.resolve(
                        env.sessionRepository.getBoundSession("MEMORY:skill-room:skill-user"));

        String prompt =
                env.localSkillService.renderSkillIndexPrompt("MEMORY:skill-room:skill-user", scope);
        SkillTools tools =
                new SkillTools(
                        env.localSkillService,
                        env.checkpointService,
                        env.sessionRepository,
                        "MEMORY:skill-room:skill-user",
                        scope);
        String listed = tools.skillsList(null);
        String viewed = tools.skillView("agent-only", null);
        String hidden = tools.skillView("global-skill", null);

        assertThat(prompt).contains("agent-only").doesNotContain("global-skill");
        assertThat(listed).contains("agent-only").doesNotContain("global-skill");
        assertThat(viewed).contains("agent local");
        assertThat(hidden).contains("\"success\":false").contains("Skill not found");
    }

    @Test
    void shouldReturnStructuredJsonFromMemoryTool() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MemoryTools tools = new MemoryTools(env.memoryService);

        String addResult = tools.memory("add", "memory", "长期偏好：输出中文", null);
        String readResult = tools.memory("read", "memory", null, null);
        String todayResult = tools.memory("add", "today", "今日完成记忆写入验证", null);

        assertThat(addResult).contains("\"success\":true");
        assertThat(addResult).contains("\"action\":\"add\"");
        assertThat(readResult).contains("\"success\":true");
        assertThat(readResult).contains("\"content\"");
        assertThat(readResult).contains("长期偏好：输出中文");
        assertThat(todayResult).contains("\"success\":true");
        assertThat(env.memoryService.read("today")).contains("今日完成记忆写入验证");
    }

    @Test
    void shouldPatchExistingLearnedSkillInsteadOfSkipping() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getLearning().setToolCallThreshold(1);
        env.localSkillService.createSkill(
                "repeatable-task", null, skill("repeatable-task", "demo"));

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room:user");
        session.setTitle("repeatable task");
        session.setCompressedSummary("已经验证的流程摘要");
        session.setNdjson(
                MessageSupport.toNdjson(
                        java.util.Collections.singletonList(
                                ChatMessage.ofTool("tool output", "tool", "1"))));
        env.sessionRepository.save(session);

        AsyncSkillLearningService learningService =
                new AsyncSkillLearningService(
                        env.appConfig,
                        env.sessionRepository,
                        env.memoryService,
                        env.localSkillService,
                        env.checkpointService,
                        env.llmGateway);
        GatewayMessage message = env.message("room", "user", "确认最终验证步骤");
        GatewayReply reply = GatewayReply.ok("done");
        learningService.schedulePostReplyLearning(session, message, reply);

        String content = waitSkillContent(env, "repeatable-task");
        assertThat(content).contains("已经验证的流程摘要");
        assertThat(content).contains("当前任务验证点");
    }

    @Test
    void shouldAutoWriteModelSummarizedSkillWithoutConfirmation() throws Exception {
        TestEnvironment env = TestEnvironment.withLlm(new SkillSummaryGateway());
        env.appConfig.getLearning().setToolCallThreshold(1);

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room:user");
        session.setTitle("model skill task");
        session.setCompressedSummary("已经完成发布前检查、补丁提交和 Maven 验证。");
        session.setNdjson(
                MessageSupport.toNdjson(
                        java.util.Collections.singletonList(
                                ChatMessage.ofTool("mvn -q test passed", "shell", "1"))));
        env.sessionRepository.save(session);

        AsyncSkillLearningService learningService =
                new AsyncSkillLearningService(
                        env.appConfig,
                        env.sessionRepository,
                        env.memoryService,
                        env.localSkillService,
                        env.checkpointService,
                        env.llmGateway);
        learningService.schedulePostReplyLearning(
                session, env.message("room", "user", "把发布步骤沉淀为 skill"), GatewayReply.ok("done"));

        String content = waitSkillContent(env, "model-skill-task", "模型总结出的发布流程");
        assertThat(content).contains("name: model-skill-task");
        assertThat(content).contains("模型总结出的发布流程");
        assertThat(content).contains("不需要人工确认");
        assertThat(content).doesNotContain("参考下述已验证流程");
        assertThat(content).doesNotContain("```");
    }

    private String waitSkillContent(TestEnvironment env, String name) throws Exception {
        return waitSkillContent(env, name, "当前任务验证点");
    }

    private String waitSkillContent(TestEnvironment env, String name, String expected)
            throws Exception {
        long deadline = System.currentTimeMillis() + 2000L;
        String content = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                content = env.localSkillService.viewSkill(name, null).getContent();
                if (content.contains(expected)) {
                    return content;
                }
            } catch (Exception ignored) {
                // 异步学习尚未创建 skill。
            }
            Thread.sleep(50L);
        }
        if (content.length() > 0) {
            return content;
        }
        return env.localSkillService.viewSkill(name, null).getContent();
    }

    private String skill(String name, String description) {
        return "---\nname: "
                + name
                + "\ndescription: "
                + description
                + "\n---\n\n# Steps\n- example\n";
    }

    private static class SkillSummaryGateway implements LlmGateway {
        @Override
        public LlmResult chat(
                SessionRecord session,
                String systemPrompt,
                String userMessage,
                List<Object> toolObjects)
                throws Exception {
            String content =
                    "---\n"
                            + "name: ignored\n"
                            + "description: ignored\n"
                            + "---\n\n"
                            + "# 触发条件\n"
                            + "- 遇到需要把已验证工程流程沉淀为 skill 的任务时使用。\n\n"
                            + "# 执行步骤\n"
                            + "1. 读取会话摘要和工具结果。\n"
                            + "2. 提炼模型总结出的发布流程。\n"
                            + "3. 直接写入 SKILL.md，不需要人工确认。\n\n"
                            + "# 已验证流程\n"
                            + "- 模型总结出的发布流程包含测试、提交和后续 UI 删除入口。\n\n"
                            + "# Pitfalls\n"
                            + "- 不要把一次性寒暄或完整提示词写进 skill。\n\n"
                            + "# Verification\n"
                            + "- 检查 SKILL.md 没有代码围栏，且包含可复用步骤。\n";
            LlmResult result = new LlmResult();
            result.setAssistantMessage(ChatMessage.ofAssistant(content));
            result.setRawResponse(content);
            result.setNdjson("");
            return result;
        }

        @Override
        public LlmResult resume(
                SessionRecord session, String systemPrompt, List<Object> toolObjects)
                throws Exception {
            return chat(session, systemPrompt, "", toolObjects);
        }
    }
}
