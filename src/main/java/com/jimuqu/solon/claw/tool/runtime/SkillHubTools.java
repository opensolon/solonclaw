package com.jimuqu.solon.claw.tool.runtime;

import com.jimuqu.solon.claw.core.service.SkillHubService;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** Skills Hub 工具集合。 */
@RequiredArgsConstructor
public class SkillHubTools {
    private final SkillHubService skillHubService;

    @ToolMapping(
            name = "skills_hub_search",
            description = "Search remote skill sources and return normalized metadata.")
    public String search(
            @Param(name = "query", description = "搜索词") String query,
            @Param(name = "source", description = "来源过滤，可选", required = false) String source,
            @Param(name = "limit", description = "结果条数，默认 10", required = false) Integer limit)
            throws Exception {
        return ONode.serialize(
                skillHubService.search(
                        query,
                        source == null ? "all" : source,
                        limit == null ? 10 : limit.intValue()));
    }

    @ToolMapping(
            name = "skills_hub_inspect",
            description = "Inspect a remote skill identifier without installing it.")
    public String inspect(@Param(name = "identifier", description = "来源标识符") String identifier)
            throws Exception {
        return ONode.serialize(skillHubService.inspect(identifier));
    }

    @ToolMapping(
            name = "skills_hub_install",
            description =
                    "Install a skill from a remote source into the local runtime skills directory.")
    public String install(
            @Param(name = "identifier", description = "来源标识符") String identifier,
            @Param(name = "category", description = "可选安装分类", required = false) String category,
            @Param(name = "force", description = "是否强制安装", required = false) Boolean force)
            throws Exception {
        return ONode.serialize(
                skillHubService.install(
                        identifier, category, force != null && force.booleanValue()));
    }

    @ToolMapping(name = "skills_hub_list", description = "List hub-installed skills.")
    public String list() throws Exception {
        return ONode.serialize(skillHubService.listInstalled());
    }

    @ToolMapping(
            name = "skills_hub_check",
            description = "Check hub-installed skills for upstream updates.")
    public String check(@Param(name = "name", description = "可选技能名", required = false) String name)
            throws Exception {
        return ONode.serialize(skillHubService.check(name));
    }

    @ToolMapping(
            name = "skills_hub_update",
            description = "Update hub-installed skills from their upstream sources.")
    public String update(
            @Param(name = "name", description = "可选技能名", required = false) String name,
            @Param(name = "force", description = "是否允许覆盖 caution 安装限制", required = false)
                    Boolean force)
            throws Exception {
        return ONode.serialize(skillHubService.update(name, force != null && force.booleanValue()));
    }

    @ToolMapping(
            name = "skills_hub_audit",
            description = "Audit installed hub skills with the local skills guard.")
    public String audit(@Param(name = "name", description = "可选技能名", required = false) String name)
            throws Exception {
        return ONode.serialize(skillHubService.audit(name));
    }

    @ToolMapping(name = "skills_hub_uninstall", description = "Uninstall a hub-installed skill.")
    public String uninstall(@Param(name = "name", description = "技能名") String name)
            throws Exception {
        return ONode.serialize(
                java.util.Collections.singletonMap("message", skillHubService.uninstall(name)));
    }

    @ToolMapping(
            name = "skills_hub_tap",
            description = "Manage GitHub taps for the skills hub. action supports list/add/remove.")
    public String tap(
            @Param(name = "action", description = "list/add/remove") String action,
            @Param(name = "repo", description = "owner/repo", required = false) String repo,
            @Param(name = "path", description = "repo 内 skills 根路径，可选", required = false)
                    String path)
            throws Exception {
        if ("list".equalsIgnoreCase(action)) {
            return ONode.serialize(skillHubService.listTaps());
        }
        if ("add".equalsIgnoreCase(action)) {
            return ONode.serialize(
                    java.util.Collections.singletonMap(
                            "message", skillHubService.addTap(repo, path)));
        }
        if ("remove".equalsIgnoreCase(action)) {
            return ONode.serialize(
                    java.util.Collections.singletonMap("message", skillHubService.removeTap(repo)));
        }
        return new ONode().set("success", false).set("error", "Unsupported tap action").toJson();
    }

    @RequiredArgsConstructor
    public static class SearchTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_search",
                description = "Search remote skill sources and return normalized metadata.")
        public String search(
                @Param(name = "query", description = "搜索词") String query,
                @Param(name = "source", description = "来源过滤，可选", required = false) String source,
                @Param(name = "limit", description = "结果条数，默认 10", required = false) Integer limit)
                throws Exception {
            return delegate.search(query, source, limit);
        }
    }

    @RequiredArgsConstructor
    public static class InspectTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_inspect",
                description = "Inspect a remote skill identifier without installing it.")
        public String inspect(@Param(name = "identifier", description = "来源标识符") String identifier)
                throws Exception {
            return delegate.inspect(identifier);
        }
    }

    @RequiredArgsConstructor
    public static class InstallTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_install",
                description =
                        "Install a skill from a remote source into the local runtime skills directory.")
        public String install(
                @Param(name = "identifier", description = "来源标识符") String identifier,
                @Param(name = "category", description = "可选安装分类", required = false) String category,
                @Param(name = "force", description = "是否强制安装", required = false) Boolean force)
                throws Exception {
            return delegate.install(identifier, category, force);
        }
    }

    @RequiredArgsConstructor
    public static class ListTool {
        private final SkillHubTools delegate;

        @ToolMapping(name = "skills_hub_list", description = "List hub-installed skills.")
        public String list() throws Exception {
            return delegate.list();
        }
    }

    @RequiredArgsConstructor
    public static class CheckTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_check",
                description = "Check hub-installed skills for upstream updates.")
        public String check(
                @Param(name = "name", description = "可选技能名", required = false) String name)
                throws Exception {
            return delegate.check(name);
        }
    }

    @RequiredArgsConstructor
    public static class UpdateTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_update",
                description = "Update hub-installed skills from their upstream sources.")
        public String update(
                @Param(name = "name", description = "可选技能名", required = false) String name,
                @Param(name = "force", description = "是否允许覆盖 caution 安装限制", required = false)
                        Boolean force)
                throws Exception {
            return delegate.update(name, force);
        }
    }

    @RequiredArgsConstructor
    public static class AuditTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_audit",
                description = "Audit installed hub skills with the local skills guard.")
        public String audit(
                @Param(name = "name", description = "可选技能名", required = false) String name)
                throws Exception {
            return delegate.audit(name);
        }
    }

    @RequiredArgsConstructor
    public static class UninstallTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_uninstall",
                description = "Uninstall a hub-installed skill.")
        public String uninstall(@Param(name = "name", description = "技能名") String name)
                throws Exception {
            return delegate.uninstall(name);
        }
    }

    @RequiredArgsConstructor
    public static class TapTool {
        private final SkillHubTools delegate;

        @ToolMapping(
                name = "skills_hub_tap",
                description =
                        "Manage GitHub taps for the skills hub. action supports list/add/remove.")
        public String tap(
                @Param(name = "action", description = "list/add/remove") String action,
                @Param(name = "repo", description = "owner/repo", required = false) String repo,
                @Param(name = "path", description = "repo 内 skills 根路径，可选", required = false)
                        String path)
                throws Exception {
            return delegate.tap(action, repo, path);
        }
    }
}
