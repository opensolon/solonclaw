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
     * 获取原始日志文本
     */
    @Get
    @Mapping("/raw")
    @Produces("text/plain;charset=utf-8")
    public String getRawLogs(@Param(required = false) Integer lines) {
        int maxLines = (lines != null && lines > 0) ? lines : 500;
        return unifiedLogger.getLogStore().getRawLogs(maxLines);
    }

    /**
     * 清空日志
     */
    @Delete
    @Mapping("")
    public Result clearLogs() {
        try {
            unifiedLogger.getLogStore().clearLogs(null);
            return Result.success("清空日志成功");
        } catch (Exception e) {
            return Result.error("清空日志失败: " + e.getMessage());
        }
    }
}
