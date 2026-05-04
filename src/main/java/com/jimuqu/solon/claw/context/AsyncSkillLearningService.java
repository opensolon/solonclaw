package com.jimuqu.solon.claw.context;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.SkillDescriptor;
import com.jimuqu.solon.claw.core.model.SkillView;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.core.service.MemoryService;
import com.jimuqu.solon.claw.core.service.SkillLearningService;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.support.BoundedExecutorFactory;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.SecretRedactor;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.chat.message.AssistantMessage;

/** 主回复后的异步学习闭环服务。 */
@RequiredArgsConstructor
public class AsyncSkillLearningService implements SkillLearningService {
    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final MemoryService memoryService;
    private final LocalSkillService localSkillService;
    private final CheckpointService checkpointService;
    private final LlmGateway llmGateway;
    private final SqliteDatabase database;
    private final ExecutorService executorService =
            BoundedExecutorFactory.fixed("async-skill-learning", 1, 64);

    public AsyncSkillLearningService(
            AppConfig appConfig,
            SessionRepository sessionRepository,
            MemoryService memoryService,
            LocalSkillService localSkillService,
            CheckpointService checkpointService,
            LlmGateway llmGateway) {
        this(
                appConfig,
                sessionRepository,
                memoryService,
                localSkillService,
                checkpointService,
                llmGateway,
                null);
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    @Override
    public void schedulePostReplyLearning(
            final SessionRecord session, final GatewayMessage message, final GatewayReply reply)
            throws Exception {
        if (!appConfig.getLearning().isEnabled()
                || session == null
                || reply == null
                || reply.isError()) {
            return;
        }

        executorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int toolMessages = countToolMessages(session);
                            boolean hasRecentCheckpoint =
                                    checkpointService.hasRecentCheckpoint(
                                            message.sourceKey(),
                                            Math.max(
                                                    session.getLastLearningAt(),
                                                    session.getUpdatedAt() - 60_000L));

                            if (toolMessages < appConfig.getLearning().getToolCallThreshold()
                                    && !hasRecentCheckpoint) {
                                return;
                            }
                            runLearning(session, message, toolMessages, hasRecentCheckpoint);
                        } catch (Exception ignored) {
                            // 学习失败不影响主回复。
                        }
                    }
                });
    }

    private void runLearning(
            SessionRecord session,
            GatewayMessage message,
            int toolMessages,
            boolean hasRecentCheckpoint)
            throws Exception {
        if (toolMessages >= appConfig.getLearning().getToolCallThreshold()) {
            learnSkill(session, message, hasRecentCheckpoint);
        } else if (hasRecentCheckpoint) {
            learnSkill(session, message, true);
        }

        session.setLastLearningAt(System.currentTimeMillis());
        sessionRepository.save(session);
    }

    private void learnSkill(
            SessionRecord session, GatewayMessage message, boolean hasRecentCheckpoint)
            throws Exception {
        String decision = classifyImprovement(session, message, hasRecentCheckpoint);
        String skillName = inferSkillName(session);
        SkillDescriptor descriptor = findSkill(skillName);
        if (descriptor != null && ("new_skill".equals(decision) || "update_loaded_skill".equals(decision))) {
            decision = "update_existing_skill";
        }
        if ("no_change".equals(decision) || "memory_only".equals(decision)) {
            writeImprovementReport(session, null, decision, "Rubric decision: " + decision, Collections.<String>emptyList(), false);
            return;
        }
        if (descriptor == null) {
            localSkillService.createSkill(
                    skillName, null, buildSkillContent(session, message, hasRecentCheckpoint));
            writeImprovementReport(
                    session,
                    skillName,
                    "new_skill",
                    "Created a new skill after rubric/class-first evaluation.",
                    Collections.singletonList("SKILL.md"),
                    false);
        } else {
            patchExistingSkill(skillName, session, message, hasRecentCheckpoint);
            writeImprovementReport(
                    session,
                    skillName,
                    "update_existing_skill",
                    "Updated existing skill after rubric/class-first evaluation.",
                    Collections.singletonList("SKILL.md"),
                    false);
        }
    }

    private String classifyImprovement(
            SessionRecord session, GatewayMessage message, boolean hasRecentCheckpoint) {
        if (llmGateway == null) {
            return hasRecentCheckpoint ? "update_existing_skill" : "new_skill";
        }
        try {
            SessionRecord rubricSession = new SessionRecord();
            rubricSession.setSessionId(
                    "skill-rubric-"
                            + StrUtil.blankToDefault(session.getSessionId(), "session")
                            + "-"
                            + System.currentTimeMillis());
            rubricSession.setSourceKey(session.getSourceKey());
            LlmResult result =
                    llmGateway.chat(
                            rubricSession,
                            "你是 SolonClaw 的 self-improvement rubric 分类器。只输出一个类别。",
                            "请从以下类别中选择一个：no_change, new_skill, update_loaded_skill, update_existing_skill, memory_only。\n"
                                    + "用户请求："
                                    + StrUtil.blankToDefault(message == null ? "" : message.getText(), "")
                                    + "\n工具消息数量满足阈值，checkpoint="
                                    + hasRecentCheckpoint
                                    + "\n会话摘要："
                                    + SecretRedactor.redact(
                                            StrUtil.blankToDefault(session.getCompressedSummary(), ""), 2000),
                            Collections.emptyList());
            String text = extractAssistantText(result).trim().toLowerCase();
            String firstToken = text.split("[\\s,，。:：]+", 2)[0];
            for (String candidate :
                    new String[] {
                        "no_change",
                        "new_skill",
                        "update_loaded_skill",
                        "update_existing_skill",
                        "memory_only"
                    }) {
                if (candidate.equals(firstToken) || candidate.equals(text)) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return hasRecentCheckpoint ? "update_existing_skill" : "new_skill";
    }

    private int countToolMessages(SessionRecord session) throws Exception {
        int count = 0;
        try {
            for (org.noear.solon.ai.chat.message.ChatMessage chatMessage :
                    MessageSupport.loadMessages(session.getNdjson())) {
                if (chatMessage.getRole() == org.noear.solon.ai.chat.ChatRole.TOOL) {
                    count++;
                }
            }
        } catch (Exception ignored) {
            return 0;
        }
        return count;
    }

    private SkillDescriptor findSkill(String skillName) throws Exception {
        List<SkillDescriptor> skills = localSkillService.listSkills(null);
        for (SkillDescriptor descriptor : skills) {
            if (descriptor.getName().equals(skillName)) {
                return descriptor;
            }
        }
        return null;
    }

    private String inferSkillName(SessionRecord session) {
        String base = StrUtil.blankToDefault(session.getTitle(), "learned-workflow").toLowerCase();
        base = base.replaceAll("[^a-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        base = base.replaceAll("^-+", "").replaceAll("-+$", "");
        return StrUtil.blankToDefault(base, "learned-workflow");
    }

    private String buildSkillContent(
            SessionRecord session, GatewayMessage message, boolean hasRecentCheckpoint) {
        String modelContent = summarizeSkillWithModel(session, message, hasRecentCheckpoint, null);
        if (StrUtil.isNotBlank(modelContent)) {
            return modelContent;
        }
        return buildFallbackSkillContent(session, message, hasRecentCheckpoint);
    }

    private String buildFallbackSkillContent(
            SessionRecord session, GatewayMessage message, boolean hasRecentCheckpoint) {
        String name = inferSkillName(session);
        String description = StrUtil.blankToDefault(session.getTitle(), "从复杂任务中沉淀出的可复用流程。");
        String progress =
                StrUtil.blankToDefault(
                        session.getCompressedSummary(), replySafeExcerpt(session.getNdjson()));
        String nextStep =
                StrUtil.blankToDefault(message == null ? "" : message.getText(), "参考当前任务上下文继续执行。");

        StringBuilder buffer = new StringBuilder();
        buffer.append("---\n");
        buffer.append("name: ").append(name).append("\n");
        buffer.append("description: ").append(description).append("\n");
        buffer.append("---\n\n");
        buffer.append("# 触发条件\n");
        buffer.append("- 当遇到与本技能相似的复杂任务时使用。\n\n");
        buffer.append("# 执行步骤\n");
        buffer.append("1. 先确认当前任务目标与上下文是否匹配。\n");
        buffer.append("2. 参考下述已验证流程执行。\n");
        buffer.append("3. 结束后根据结果继续补充技能内容。\n\n");
        buffer.append("# 已验证流程\n");
        buffer.append(progress).append("\n\n");
        buffer.append("# Pitfalls\n");
        buffer.append("- 如上下文差异较大，先重新检查输入条件再复用。\n\n");
        buffer.append("# Verification\n");
        buffer.append("- 核对输出是否满足当前用户要求：").append(nextStep).append("\n");
        if (hasSessionHints(session, hasRecentCheckpoint)) {
            buffer.append("- 当前流程涉及结构化文件修改，执行前先确认 checkpoint 策略。\n");
        }
        return buffer.toString();
    }

    private void patchExistingSkill(
            String skillName,
            SessionRecord session,
            GatewayMessage message,
            boolean hasRecentCheckpoint)
            throws Exception {
        SkillView view = localSkillService.viewSkill(skillName, null);
        String modelContent =
                summarizeSkillWithModel(session, message, hasRecentCheckpoint, view.getContent());
        if (StrUtil.isNotBlank(modelContent)) {
            localSkillService.editSkill(skillName, modelContent);
            return;
        }

        String content = view.getContent();
        String progressBullet =
                "- "
                        + StrUtil.blankToDefault(
                                session.getCompressedSummary(),
                                replySafeExcerpt(session.getNdjson()));
        String pitfallBullet = "- 当上下文与历史流程不完全一致时，先重新核对输入条件与依赖。";
        String verificationBullet =
                "- 当前任务验证点：" + StrUtil.blankToDefault(message.getText(), "继续核对结果是否满足用户要求。");
        if (hasRecentCheckpoint) {
            verificationBullet = verificationBullet + " 执行前确认 checkpoint 策略。";
        }

        boolean updated = false;
        if (!content.contains(progressBullet)) {
            updated |= patchOrAppend(skillName, "# 已验证流程\n", "# 已验证流程\n" + progressBullet + "\n");
        }
        if (!content.contains(pitfallBullet)) {
            updated |=
                    patchOrAppend(skillName, "# Pitfalls\n", "# Pitfalls\n" + pitfallBullet + "\n");
        }
        if (!content.contains(verificationBullet)) {
            updated |=
                    patchOrAppend(
                            skillName,
                            "# Verification\n",
                            "# Verification\n" + verificationBullet + "\n");
        }

        SkillView refreshed = localSkillService.viewSkill(skillName, null);
        if (!updated
                || !refreshed.getContent().contains(progressBullet)
                || !refreshed.getContent().contains(pitfallBullet)
                || !refreshed.getContent().contains(verificationBullet)) {
            localSkillService.editSkill(
                    skillName,
                    appendMissingSections(
                            refreshed.getContent(),
                            progressBullet,
                            pitfallBullet,
                            verificationBullet));
        }
    }

    private boolean patchOrAppend(String skillName, String header, String replacement) {
        try {
            localSkillService.patchSkill(skillName, header, replacement, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String appendMissingSections(
            String content,
            String progressBullet,
            String pitfallBullet,
            String verificationBullet) {
        String updated = content;
        if (!updated.contains(progressBullet)) {
            updated = appendSection(updated, "# 已验证流程", progressBullet);
        }
        if (!updated.contains(pitfallBullet)) {
            updated = appendSection(updated, "# Pitfalls", pitfallBullet);
        }
        if (!updated.contains(verificationBullet)) {
            updated = appendSection(updated, "# Verification", verificationBullet);
        }
        return updated;
    }

    private String appendSection(String content, String header, String bullet) {
        if (content.contains(header)) {
            return content.replace(header, header + "\n" + bullet);
        }
        return content + "\n\n" + header + "\n" + bullet + "\n";
    }

    /** 判断是否需要额外补充会话提示。 */
    private boolean hasSessionHints(SessionRecord session, boolean hasRecentCheckpoint) {
        return hasRecentCheckpoint && session != null && StrUtil.isNotBlank(session.getTitle());
    }

    private String summarizeSkillWithModel(
            SessionRecord session,
            GatewayMessage message,
            boolean hasRecentCheckpoint,
            String existingContent) {
        if (llmGateway == null) {
            return null;
        }
        try {
            String skillName = inferSkillName(session);
            String description = StrUtil.blankToDefault(session.getTitle(), "从复杂任务中沉淀出的可复用流程。");
            SessionRecord learningSession = new SessionRecord();
            learningSession.setSessionId(
                    "skill-learning-"
                            + StrUtil.blankToDefault(session.getSessionId(), "session")
                            + "-"
                            + System.currentTimeMillis());
            learningSession.setSourceKey(session.getSourceKey());

            LlmResult result =
                    llmGateway.chat(
                            learningSession,
                            "你是 SolonClaw 的技能沉淀器。只输出可直接写入 SKILL.md 的 Markdown，不要寒暄。",
                            buildLearningPrompt(
                                    session,
                                    message,
                                    hasRecentCheckpoint,
                                    existingContent,
                                    skillName,
                                    description),
                            Collections.emptyList());
            String raw = extractAssistantText(result);
            return normalizeModelSkillContent(raw, skillName, description);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildLearningPrompt(
            SessionRecord session,
            GatewayMessage message,
            boolean hasRecentCheckpoint,
            String existingContent,
            String skillName,
            String description) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请把这次任务沉淀成一个可复用 skill。要求：\n");
        prompt.append("- 输出完整 SKILL.md。\n");
        prompt.append("- frontmatter 必须包含 name 和 description。\n");
        prompt.append("- name 固定为：").append(skillName).append("\n");
        prompt.append("- description 使用一句中文业务描述。\n");
        prompt.append("- 正文必须包含：# 触发条件、# 执行步骤、# 已验证流程、# Pitfalls、# Verification。\n");
        prompt.append("- 内容要总结可复用流程，不要记录一次性聊天寒暄。\n");
        prompt.append("- 如涉及文件、命令、工具、checkpoint，要保留具体经验和注意事项。\n");
        prompt.append("- 不要输出代码围栏，不要输出解释文字。\n\n");
        if (StrUtil.isNotBlank(existingContent)) {
            prompt.append("现有 SKILL.md，需要基于新任务更新而不是丢失旧经验：\n");
            prompt.append(SecretRedactor.redact(existingContent, 6000)).append("\n\n");
        }
        prompt.append("建议描述：").append(description).append("\n");
        prompt.append("本轮用户请求：")
                .append(StrUtil.blankToDefault(message == null ? "" : message.getText(), ""))
                .append("\n");
        prompt.append("是否涉及 checkpoint：").append(hasRecentCheckpoint).append("\n\n");
        prompt.append("会话压缩摘要：\n");
        prompt.append(
                        SecretRedactor.redact(
                                StrUtil.blankToDefault(session.getCompressedSummary(), "无"), 6000))
                .append("\n\n");
        prompt.append("会话消息摘录：\n");
        prompt.append(replySafeExcerpt(session.getNdjson(), 6000));
        return prompt.toString();
    }

    private String normalizeModelSkillContent(String raw, String skillName, String description) {
        String content = stripMarkdownFence(StrUtil.nullToEmpty(raw).trim());
        if (StrUtil.isBlank(content)) {
            return null;
        }

        String body = content;
        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end >= 0) {
                body = content.substring(end + "\n---".length()).trim();
            }
        }
        if (StrUtil.isBlank(body)) {
            return null;
        }
        if (!isStructuredSkillBody(body)) {
            return null;
        }

        StringBuilder normalized = new StringBuilder();
        normalized.append("---\n");
        normalized.append("name: ").append(skillName).append("\n");
        normalized
                .append("description: ")
                .append(StrUtil.blankToDefault(description, "从复杂任务中沉淀出的可复用流程。").replace('\n', ' '))
                .append("\n");
        normalized.append("---\n\n");
        normalized.append(body);
        if (!body.endsWith("\n")) {
            normalized.append('\n');
        }
        return SecretRedactor.redact(normalized.toString(), 20000);
    }

    private boolean isStructuredSkillBody(String body) {
        String normalized = "\n" + StrUtil.nullToEmpty(body).replace("\r\n", "\n").trim() + "\n";
        return normalized.contains("\n# 触发条件\n")
                && normalized.contains("\n# 执行步骤\n")
                && normalized.contains("\n# 已验证流程\n")
                && normalized.contains("\n# Pitfalls\n")
                && normalized.contains("\n# Verification\n");
    }

    private String stripMarkdownFence(String content) {
        String value = StrUtil.nullToEmpty(content).trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return value.substring(firstLineEnd + 1, lastFence).trim();
        }
        return value;
    }

    private String extractAssistantText(LlmResult result) {
        if (result == null) {
            return "";
        }
        AssistantMessage message = result.getAssistantMessage();
        if (message == null) {
            return StrUtil.nullToEmpty(result.getRawResponse());
        }
        if (StrUtil.isNotBlank(message.getResultContent())) {
            return message.getResultContent();
        }
        if (StrUtil.isNotBlank(message.getContent())) {
            return message.getContent();
        }
        return StrUtil.nullToEmpty(result.getRawResponse());
    }

    private String replySafeExcerpt(String ndjson) {
        return replySafeExcerpt(ndjson, 400);
    }

    private String replySafeExcerpt(String ndjson, int limit) {
        String normalized = StrUtil.nullToEmpty(ndjson).replace('\n', ' ').trim();
        if (normalized.length() <= limit) {
            return SecretRedactor.redact(normalized, limit);
        }
        return SecretRedactor.redact(normalized.substring(0, limit) + "...", limit);
    }

    private void writeImprovementReport(
            SessionRecord session,
            String skillName,
            String action,
            String summary,
            List<String> changedFiles,
            boolean needsReview) {
        try {
            File reportDir =
                    cn.hutool.core.io.FileUtil.file(
                            appConfig.getRuntime().getLogsDir(), "skill-improvements");
            cn.hutool.core.io.FileUtil.mkdir(reportDir);
            Map<String, Object> report = new LinkedHashMap<String, Object>();
            report.put("sessionId", session == null ? null : session.getSessionId());
            report.put("runId", null);
            report.put("skillName", skillName);
            report.put("action", action);
            report.put("summary", summary);
            report.put("changedFiles", changedFiles);
            report.put("needsReview", Boolean.valueOf(needsReview));
            report.put("createdAt", Long.valueOf(System.currentTimeMillis()));
            saveImprovement(report);
            cn.hutool.core.io.FileUtil.writeUtf8String(
                    org.noear.snack4.ONode.serialize(report),
                    cn.hutool.core.io.FileUtil.file(
                            reportDir,
                            System.currentTimeMillis()
                                    + "-"
                                    + StrUtil.blankToDefault(skillName, "memory")
                                    + ".json"));
        } catch (Exception ignored) {
        }
    }

    private void saveImprovement(Map<String, Object> report) {
        if (database == null || report == null) {
            return;
        }
        Connection connection = null;
        try {
            connection = database.openConnection();
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into skill_improvements (improvement_id, session_id, run_id, skill_name, action, summary, changed_files_json, evidence_json, needs_review, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, IdSupport.newId());
            statement.setString(2, asString(report.get("sessionId")));
            statement.setString(3, asString(report.get("runId")));
            statement.setString(4, StrUtil.blankToDefault(asString(report.get("skillName")), "memory"));
            statement.setString(5, StrUtil.blankToDefault(asString(report.get("action")), "unknown"));
            statement.setString(6, asString(report.get("summary")));
            statement.setString(7, org.noear.snack4.ONode.serialize(report.get("changedFiles")));
            statement.setString(8, org.noear.snack4.ONode.serialize(report));
            statement.setInt(9, Boolean.TRUE.equals(report.get("needsReview")) ? 1 : 0);
            statement.setLong(10, asLong(report.get("createdAt")));
            statement.executeUpdate();
            statement.close();
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
