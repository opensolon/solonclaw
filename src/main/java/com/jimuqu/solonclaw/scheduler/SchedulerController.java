package com.jimuqu.solonclaw.scheduler;

import org.noear.solon.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 调度控制器
 * <p>
 * 提供定时任务管理的 HTTP 接口
 *
 * @author SolonClaw
 */
@Controller
@Mapping("/api/jobs")
public class SchedulerController {

    private static final Logger log = LoggerFactory.getLogger(SchedulerController.class);

    @Inject
    private SchedulerService schedulerService;

    /**
     * 获取所有任务
     */
    @Get
    @Mapping
    public Result getJobs() {
        try {
            List<SchedulerService.JobInfo> jobs = schedulerService.getJobs();
            return Result.success("获取成功", Map.of(
                    "total", jobs.size(),
                    "jobs", jobs
            ));
        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个任务
     */
    @Get
    @Mapping("/{name}")
    public Result getJob(String name) {
        try {
            SchedulerService.JobInfo job = schedulerService.getJob(name);
            if (job == null) {
                return Result.error("任务不存在: " + name);
            }
            return Result.success("获取成功", job);
        } catch (Exception e) {
            log.error("获取任务失败: {}", name, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 添加 Cron 任务
     */
    @Post
    @Mapping("/cron")
    public Result addCronJob(@Body JobRequest request) {
        log.info("添加 Cron 任务: name={}, cron={}", request.name(), request.cron());

        try {
            if (request.name() == null || request.name().isEmpty()) {
                return Result.error("任务名称不能为空");
            }
            if (request.cron() == null || request.cron().isEmpty()) {
                return Result.error("Cron 表达式不能为空");
            }
            if (request.command() == null || request.command().isEmpty()) {
                return Result.error("执行命令不能为空");
            }

            boolean success = schedulerService.addCronJob(request.name(), request.cron(), request.command());
            if (success) {
                return Result.success("添加成功", Map.of("name", request.name()));
            } else {
                return Result.error("任务已存在: " + request.name());
            }
        } catch (Exception e) {
            log.error("添加 Cron 任务失败", e);
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 添加固定频率任务
     */
    @Post
    @Mapping("/fixed-rate")
    public Result addFixedRateJob(@Body JobRequest request) {
        log.info("添加固定频率任务: name={}, fixedRate={}", request.name(), request.fixedRate());

        try {
            if (request.name() == null || request.name().isEmpty()) {
                return Result.error("任务名称不能为空");
            }
            if (request.fixedRate() == null || request.fixedRate() <= 0) {
                return Result.error("固定频率必须大于 0");
            }
            if (request.command() == null || request.command().isEmpty()) {
                return Result.error("执行命令不能为空");
            }

            boolean success = schedulerService.addFixedRateJob(request.name(), request.fixedRate(), request.command());
            if (success) {
                return Result.success("添加成功", Map.of("name", request.name()));
            } else {
                return Result.error("任务已存在: " + request.name());
            }
        } catch (Exception e) {
            log.error("添加固定频率任务失败", e);
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 添加一次性任务
     */
    @Post
    @Mapping("/one-time")
    public Result addOneTimeJob(@Body JobRequest request) {
        log.info("添加一次性任务: name={}, delay={}", request.name(), request.delay());

        try {
            if (request.name() == null || request.name().isEmpty()) {
                return Result.error("任务名称不能为空");
            }
            if (request.delay() == null || request.delay() <= 0) {
                return Result.error("延迟时间必须大于 0");
            }
            if (request.command() == null || request.command().isEmpty()) {
                return Result.error("执行命令不能为空");
            }

            boolean success = schedulerService.addOneTimeJob(request.name(), request.delay(), request.command());
            if (success) {
                return Result.success("添加成功", Map.of("name", request.name(), "executeAt", System.currentTimeMillis() + request.delay()));
            } else {
                return Result.error("任务已存在: " + request.name());
            }
        } catch (Exception e) {
            log.error("添加一次性任务失败", e);
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务
     */
    @Delete
    @Mapping("/{name}")
    public Result removeJob(String name) {
        log.info("删除任务: name={}", name);

        try {
            boolean success = schedulerService.removeJob(name);
            if (success) {
                return Result.success("删除成功", Map.of("name", name));
            } else {
                return Result.error("任务不存在: " + name);
            }
        } catch (Exception e) {
            log.error("删除任务失败: {}", name, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 暂停任务
     */
    @Post
    @Mapping("/{name}/pause")
    public Result pauseJob(String name) {
        log.info("暂停任务: name={}", name);

        try {
            boolean success = schedulerService.pauseJob(name);
            if (success) {
                return Result.success("暂停成功", Map.of("name", name));
            } else {
                return Result.error("暂停失败，任务不存在或调度器未初始化");
            }
        } catch (Exception e) {
            log.error("暂停任务失败: {}", name, e);
            return Result.error("暂停失败: " + e.getMessage());
        }
    }

    /**
     * 恢复任务
     */
    @Post
    @Mapping("/{name}/resume")
    public Result resumeJob(String name) {
        log.info("恢复任务: name={}", name);

        try {
            boolean success = schedulerService.resumeJob(name);
            if (success) {
                return Result.success("恢复成功", Map.of("name", name));
            } else {
                return Result.error("恢复失败，任务不存在或调度器未初始化");
            }
        } catch (Exception e) {
            log.error("恢复任务失败: {}", name, e);
            return Result.error("恢复失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务执行历史
     */
    @Get
    @Mapping("/history")
    public Result getJobHistory(@Param(defaultValue = "100") int limit) {
        try {
            List<SchedulerService.JobHistory> history = schedulerService.getJobHistory(limit);
            return Result.success("获取成功", Map.of(
                    "total", history.size(),
                    "history", history
            ));
        } catch (Exception e) {
            log.error("获取任务历史失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定任务的执行历史
     */
    @Get
    @Mapping("/{name}/history")
    public Result getJobHistoryByName(String name, @Param(defaultValue = "50") int limit) {
        try {
            List<SchedulerService.JobHistory> history = schedulerService.getJobHistoryByName(name, limit);
            return Result.success("获取成功", Map.of(
                    "name", name,
                    "total", history.size(),
                    "history", history
            ));
        } catch (Exception e) {
            log.error("获取任务历史失败: {}", name, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 清空任务执行历史
     */
    @Delete
    @Mapping("/history")
    public Result clearJobHistory() {
        log.info("清空任务执行历史");

        try {
            schedulerService.clearJobHistory();
            return Result.success("清空成功");
        } catch (Exception e) {
            log.error("清空任务历史失败", e);
            return Result.error("清空失败: " + e.getMessage());
        }
    }

    /**
     * 任务请求
     */
    public record JobRequest(
            String name,
            String cron,
            Long fixedRate,
            Long delay,
            String command
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