package com.jimuqu.solon.claw.web;

import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 技能接口。 */
@Controller
public class DashboardSkillsController {
    private final DashboardSkillsService skillsService;

    public DashboardSkillsController(DashboardSkillsService skillsService) {
        this.skillsService = skillsService;
    }

    @Mapping(value = "/api/skills", method = MethodType.GET)
    public List<Map<String, Object>> skills() throws Exception {
        return skillsService.getSkills();
    }

    @Mapping(value = "/api/skills/view", method = MethodType.GET)
    public Map<String, Object> view(Context context) throws Exception {
        return skillsService.viewSkill(context.param("name"), context.param("filePath"));
    }

    @Mapping(value = "/api/skills/files", method = MethodType.GET)
    public List<Map<String, Object>> files(Context context) throws Exception {
        return skillsService.getSkillFiles(context.param("name"));
    }

    @Mapping(value = "/api/skills/toggle", method = MethodType.PUT)
    public Map<String, Object> toggle(Context context) throws Exception {
        ONode body = ONode.ofJson(context.body());
        return skillsService.toggleSkill(
                body.get("name").getString(), body.get("enabled").getBoolean());
    }

    @Mapping(value = "/api/tools/toolsets", method = MethodType.GET)
    public List<Map<String, Object>> toolsets() {
        return skillsService.getToolsets();
    }
}
