package com.jimuqu.solon.claw.tool.runtime;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.context.LocalSkillService;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.SkillDescriptor;
import com.jimuqu.solon.claw.core.model.SkillView;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.support.constants.SkillConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** Hermes 风格 skills 工具集合。 */
@RequiredArgsConstructor
public class SkillTools {
    /** 本地技能目录服务。 */
    private final LocalSkillService localSkillService;

    /** checkpoint 服务。 */
    private final CheckpointService checkpointService;

    /** 会话仓储。 */
    private final SessionRepository sessionRepository;

    /** 当前来源键。 */
    private final String sourceKey;

    /** 当前运行冻结的 Agent scope。 */
    private final AgentRuntimeScope agentScope;

    public SkillTools(
            LocalSkillService localSkillService,
            CheckpointService checkpointService,
            SessionRepository sessionRepository,
            String sourceKey) {
        this(localSkillService, checkpointService, sessionRepository, sourceKey, null);
    }

    @ToolMapping(
            name = "skills_list",
            description = "List available skills. Optional category filter.")
    public String skillsList(
            @Param(name = "category", description = "可选分类名", required = false) String category)
            throws Exception {
        try {
            List<SkillDescriptor> skills = localSkillService.listSkills(category, agentScope);
            List<SkillDescriptor> visible = new ArrayList<SkillDescriptor>();
            for (SkillDescriptor descriptor : skills) {
                if (localSkillService.isVisible(sourceKey, descriptor.canonicalName())) {
                    visible.add(descriptor);
                }
            }
            return ONode.serialize(visible);
        } catch (Exception e) {
            return toolError(e.getMessage());
        }
    }

    @ToolMapping(
            name = "skill_view",
            description = "Load full SKILL.md or a supporting file from a skill directory.")
    public String skillView(
            @Param(name = "name", description = "技能名或 category/name") String name,
            @Param(name = "filePath", description = "可选支持文件相对路径", required = false) String filePath)
            throws Exception {
        try {
            SkillView view = localSkillService.viewSkill(name, filePath, agentScope);
            return ONode.serialize(view);
        } catch (Exception e) {
            return toolError(e.getMessage());
        }
    }

    @ToolMapping(
            name = "skill_manage",
            description =
                    "Create, patch, edit, delete or manage supporting files for a local skill.")
    public String skillManage(
            @Param(name = "action", description = "create、edit、patch、delete、write_file、remove_file")
                    String action,
            @Param(name = "name", description = "技能名或 category/name") String name,
            @Param(name = "category", description = "create 时可选分类", required = false)
                    String category,
            @Param(name = "content", description = "create/edit 时的主文件内容", required = false)
                    String content,
            @Param(name = "oldText", description = "patch 时要匹配的旧文本", required = false)
                    String oldText,
            @Param(name = "newText", description = "patch 时替换后的新文本", required = false)
                    String newText,
            @Param(name = "filePath", description = "支持文件相对路径", required = false) String filePath,
            @Param(name = "fileContent", description = "write_file 时写入的内容", required = false)
                    String fileContent)
            throws Exception {
        try {
            if (SkillConstants.ACTION_CREATE.equalsIgnoreCase(action)) {
                checkpoint(
                        Collections.singletonList(
                                localSkillService.resolveSkillMainFile(name, category)));
                return ONode.serialize(localSkillService.createSkill(name, category, content));
            }
            if (SkillConstants.ACTION_EDIT.equalsIgnoreCase(action)) {
                checkpoint(skillFiles(name));
                return ONode.serialize(localSkillService.editSkill(name, content));
            }
            if (SkillConstants.ACTION_PATCH.equalsIgnoreCase(action)) {
                checkpoint(skillFiles(name));
                return localSkillService.patchSkill(name, oldText, newText, filePath);
            }
            if (SkillConstants.ACTION_DELETE.equalsIgnoreCase(action)) {
                checkpoint(skillFiles(name));
                return localSkillService.deleteSkill(name);
            }
            if (SkillConstants.ACTION_WRITE_FILE.equalsIgnoreCase(action)) {
                checkpoint(skillFiles(name));
                return localSkillService.writeSkillFile(name, filePath, fileContent);
            }
            if (SkillConstants.ACTION_REMOVE_FILE.equalsIgnoreCase(action)) {
                checkpoint(skillFiles(name));
                return localSkillService.removeSkillFile(name, filePath);
            }
            return toolError("Unsupported skill_manage action");
        } catch (Exception e) {
            return toolError(e.getMessage());
        }
    }

    /** 收集技能目录中的全部文件，用于 checkpoint。 */
    private List<File> skillFiles(String nameOrPath) throws Exception {
        SkillView view = localSkillService.viewSkill(nameOrPath, null, agentScope);
        File skillDir = FileUtil.file(view.getDescriptor().getSkillDir());
        List<File> files = FileUtil.loopFiles(skillDir);
        if (files.isEmpty()) {
            files.add(FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME));
        }
        return files;
    }

    /** 创建 checkpoint。 */
    private void checkpoint(List<File> files) throws Exception {
        if (checkpointService == null) {
            return;
        }
        SessionRecord session =
                sessionRepository == null ? null : sessionRepository.getBoundSession(sourceKey);
        checkpointService.createCheckpoint(
                sourceKey, session == null ? null : session.getSessionId(), files);
    }

    /** 统一工具错误返回。 */
    private String toolError(String message) {
        return new ONode()
                .set("success", false)
                .set("error", StrUtil.nullToDefault(message, "unknown error"))
                .toJson();
    }

    /** `skills_list` 单工具暴露对象。 */
    @RequiredArgsConstructor
    public static class SkillsListTool {
        private final SkillTools delegate;

        @ToolMapping(
                name = "skills_list",
                description = "List available skills. Optional category filter.")
        public String skillsList(
                @Param(name = "category", description = "可选分类名", required = false) String category)
                throws Exception {
            return delegate.skillsList(category);
        }
    }

    /** `skill_view` 单工具暴露对象。 */
    @RequiredArgsConstructor
    public static class SkillViewTool {
        private final SkillTools delegate;

        @ToolMapping(
                name = "skill_view",
                description = "Load full SKILL.md or a supporting file from a skill directory.")
        public String skillView(
                @Param(name = "name", description = "技能名或 category/name") String name,
                @Param(name = "filePath", description = "可选支持文件相对路径", required = false)
                        String filePath)
                throws Exception {
            return delegate.skillView(name, filePath);
        }
    }

    /** `skill_manage` 单工具暴露对象。 */
    @RequiredArgsConstructor
    public static class SkillManageTool {
        private final SkillTools delegate;

        @ToolMapping(
                name = "skill_manage",
                description =
                        "Create, patch, edit, delete or manage supporting files for a local skill.")
        public String skillManage(
                @Param(
                                name = "action",
                                description = "create、edit、patch、delete、write_file、remove_file")
                        String action,
                @Param(name = "name", description = "技能名或 category/name") String name,
                @Param(name = "category", description = "create 时可选分类", required = false)
                        String category,
                @Param(name = "content", description = "create/edit 时的主文件内容", required = false)
                        String content,
                @Param(name = "oldText", description = "patch 时要匹配的旧文本", required = false)
                        String oldText,
                @Param(name = "newText", description = "patch 时替换后的新文本", required = false)
                        String newText,
                @Param(name = "filePath", description = "支持文件相对路径", required = false)
                        String filePath,
                @Param(name = "fileContent", description = "write_file 时写入的内容", required = false)
                        String fileContent)
                throws Exception {
            return delegate.skillManage(
                    action, name, category, content, oldText, newText, filePath, fileContent);
        }
    }
}
