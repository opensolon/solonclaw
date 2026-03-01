package com.jimuqu.solonclaw.skill;

import org.noear.solon.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Skills 控制器
 * <p>
 * 提供技能管理的 HTTP 接口
 *
 * @author SolonClaw
 */
@Controller
@Mapping("/api/skills")
public class SkillsController {

    private static final Logger log = LoggerFactory.getLogger(SkillsController.class);

    @Inject
    private SkillsManager skillsManager;

    /**
     * 获取所有技能
     */
    @Get
    @Mapping
    public Result getSkills() {
        try {
            List<DynamicSkill.SkillConfig> configs = skillsManager.getSkillConfigs();
            return Result.success("获取成功", Map.of(
                    "total", configs.size(),
                    "skills", configs
            ));
        } catch (Exception e) {
            log.error("获取技能列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个技能
     */
    @Get
    @Mapping("/{name}")
    public Result getSkill(String name) {
        try {
            DynamicSkill.SkillConfig config = skillsManager.getSkillConfig(name);
            if (config == null) {
                return Result.error("技能不存在: " + name);
            }
            return Result.success("获取成功", config);
        } catch (Exception e) {
            log.error("获取技能失败: {}", name, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 添加技能
     */
    @Post
    @Mapping
    public Result addSkill(@Body SkillRequest request) {
        log.info("添加技能: name={}", request.name());

        try {
            if (request.name() == null || request.name().isEmpty()) {
                return Result.error("技能名称不能为空");
            }
            if (request.description() == null || request.description().isEmpty()) {
                return Result.error("技能描述不能为空");
            }

            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                    request.name(),
                    request.description(),
                    request.instruction(),
                    request.condition(),
                    request.tools(),
                    true
            );

            boolean success = skillsManager.addSkill(config);
            if (success) {
                return Result.success("添加成功", Map.of("name", request.name()));
            } else {
                return Result.error("技能已存在或创建失败: " + request.name());
            }
        } catch (Exception e) {
            log.error("添加技能失败", e);
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 更新技能
     */
    @Put
    @Mapping("/{name}")
    public Result updateSkill(String name, @Body SkillRequest request) {
        log.info("更新技能: name={}", name);

        try {
            DynamicSkill.SkillConfig config = new DynamicSkill.SkillConfig(
                    request.name() != null ? request.name() : name,
                    request.description(),
                    request.instruction(),
                    request.condition(),
                    request.tools(),
                    request.enabled() != null ? request.enabled() : true
            );

            boolean success = skillsManager.updateSkill(name, config);
            if (success) {
                return Result.success("更新成功", Map.of("name", config.name()));
            } else {
                return Result.error("技能不存在或更新失败: " + name);
            }
        } catch (Exception e) {
            log.error("更新技能失败: {}", name, e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除技能
     */
    @Delete
    @Mapping("/{name}")
    public Result removeSkill(String name) {
        log.info("删除技能: name={}", name);

        try {
            boolean success = skillsManager.removeSkill(name);
            if (success) {
                return Result.success("删除成功", Map.of("name", name));
            } else {
                return Result.error("技能不存在: " + name);
            }
        } catch (Exception e) {
            log.error("删除技能失败: {}", name, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用技能
     */
    @Post
    @Mapping("/{name}/enable")
    public Result enableSkill(String name) {
        log.info("启用技能: name={}", name);

        try {
            boolean success = skillsManager.setSkillEnabled(name, true);
            if (success) {
                return Result.success("启用成功", Map.of("name", name));
            } else {
                return Result.error("技能不存在: " + name);
            }
        } catch (Exception e) {
            log.error("启用技能失败: {}", name, e);
            return Result.error("启用失败: " + e.getMessage());
        }
    }

    /**
     * 禁用技能
     */
    @Post
    @Mapping("/{name}/disable")
    public Result disableSkill(String name) {
        log.info("禁用技能: name={}", name);

        try {
            boolean success = skillsManager.setSkillEnabled(name, false);
            if (success) {
                return Result.success("禁用成功", Map.of("name", name));
            } else {
                return Result.error("技能不存在: " + name);
            }
        } catch (Exception e) {
            log.error("禁用技能失败: {}", name, e);
            return Result.error("禁用失败: " + e.getMessage());
        }
    }

    /**
     * 重新加载所有技能
     */
    @Post
    @Mapping("/reload")
    public Result reloadSkills() {
        log.info("重新加载技能");

        try {
            skillsManager.loadSkills();
            return Result.success("重新加载成功", Map.of(
                    "total", skillsManager.getSkills().size()
            ));
        } catch (Exception e) {
            log.error("重新加载技能失败", e);
            return Result.error("重新加载失败: " + e.getMessage());
        }
    }

    /**
     * 技能请求
     */
    public record SkillRequest(
            String name,
            String description,
            String instruction,
            String condition,
            List<String> tools,
            Boolean enabled
    ) {
    }

    /**
     * 统一响应结果
     */
    public record Result(
            int code,
            String message,
            Object data
    ) {
        public static Result success(String message) {
            return new Result(200, message, null);
        }

        public static Result success(String message, Object data) {
            return new Result(200, message, data);
        }

        public static Result error(String message) {
            return new Result(500, message, null);
        }
    }
}