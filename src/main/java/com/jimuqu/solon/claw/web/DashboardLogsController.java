package com.jimuqu.solon.claw.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 日志接口。 */
@Controller
public class DashboardLogsController {
    private final DashboardLogsService logsService;

    public DashboardLogsController(DashboardLogsService logsService) {
        this.logsService = logsService;
    }

    @Mapping(value = "/api/logs", method = MethodType.GET)
    public Map<String, Object> logs(Context context) {
        String file = context.param("file");
        int lines = context.paramAsInt("lines", 100);
        String level = context.param("level");
        String component = context.param("component");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("file", file == null ? "agent" : file);
        result.put("lines", logsService.read(file, lines, level, component));
        return result;
    }
}
