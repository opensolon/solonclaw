package com.jimuqu.solonclaw.logging;

import com.jimuqu.solonclaw.common.Result;
import org.noear.solon.annotation.*;

/**
 * 日志控制器
 */
@Controller
@Mapping("/api/logs")
public class LogController {
    private final UnifiedLogger unifiedLogger;

    public LogController(UnifiedLogger unifiedLogger) {
        this.unifiedLogger = unifiedLogger;
    }

    /**
     * 获取日志列表
     */
    @Get
    @Mapping("")
    public Result getLogs(
            @Param(required = false) String levels,
            @Param(required = false) String sources,
            @Param(required = false) String sessionId,
            @Param(required = false) String keyword,
            @Param(required = false) Integer page,
            @Param(required = false) Integer pageSize
    ) {
        try {
            LogQuery query = new LogQuery();

            // 解析级别
            if (levels != null && !levels.isEmpty()) {
                for (String levelStr : levels.split(",")) {
                    try {
                        query.addLevel(LogLevel.valueOf(levelStr.trim()));
                    } catch (IllegalArgumentException e) {
                        // ignore invalid level
                    }
                }
            }

            // 解析来源
            if (sources != null && !sources.isEmpty()) {
                for (String source : sources.split(",")) {
                    query.addSource(source.trim());
                }
            }

            // 其他条件
            if (sessionId != null) {
                query.setSessionId(sessionId);
            }
            if (keyword != null) {
                query.setKeyword(keyword);
            }
            if (page != null) {
                query.setPage(page);
            }
            if (pageSize != null) {
                query.setPageSize(pageSize);
            }

            java.util.List<LogEntry> logs = unifiedLogger.getLogStore().queryLogs(query);
            return Result.success("获取日志成功", logs);
        } catch (Exception e) {
            return Result.error("获取日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取日志统计
     */
    @Get
    @Mapping("/stats")
    public Result getStats() {
        try {
            LogStats stats = unifiedLogger.getLogStore().getStats();
            return Result.success("获取统计成功", stats);
        } catch (Exception e) {
            return Result.error("获取统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取日志级别列表
     */
    @Get
    @Mapping("/levels")
    public Result getLevels() {
        try {
            java.util.List<java.util.Map<String, Object>> levels = new java.util.ArrayList<>();
            for (LogLevel level : LogLevel.values()) {
                java.util.Map<String, Object> levelInfo = new java.util.HashMap<>();
                levelInfo.put("code", level.getCode());
                levelInfo.put("priority", level.getPriority());
                levelInfo.put("description", level.getDescription());
                levels.add(levelInfo);
            }
            return Result.success("获取级别列表成功", levels);
        } catch (Exception e) {
            return Result.error("获取级别列表失败: " + e.getMessage());
        }
    }

    /**
     * 清空日志
     */
    @Delete
    @Mapping("")
    public Result clearLogs(@Param(required = false) String beforeDate) {
        try {
            java.time.LocalDateTime before = null;
            if (beforeDate != null && !beforeDate.isEmpty()) {
                before = java.time.LocalDateTime.parse(beforeDate);
            }
            unifiedLogger.getLogStore().clearLogs(before);
            return Result.success("清空日志成功");
        } catch (Exception e) {
            return Result.error("清空日志失败: " + e.getMessage());
        }
    }

    /**
     * 写入测试日志
     */
    @Post
    @Mapping("/test")
    public Result writeTestLog() {
        try {
            unifiedLogger.info("Test", "test-session", "这是一条测试日志");
            unifiedLogger.userChat("test-session", "用户消息");
            unifiedLogger.agentThink("test-session", "Agent 思考过程");
            unifiedLogger.decision("test-session", "做出决策", java.util.Map.of("action", "tool_call"));
            unifiedLogger.action("test-session", "执行操作", java.util.Map.of("tool", "shell"));
            unifiedLogger.reflection("test-session", "反省总结");
            unifiedLogger.error("Test", "test-session", "错误信息", new Exception("测试异常"));
            return Result.success("写入测试日志成功");
        } catch (Exception e) {
            return Result.error("写入测试日志失败: " + e.getMessage());
        }
    }
}