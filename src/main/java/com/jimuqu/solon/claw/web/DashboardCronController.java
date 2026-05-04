package com.jimuqu.solon.claw.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 定时任务接口。 */
@Controller
public class DashboardCronController {
    private final DashboardCronService cronService;

    public DashboardCronController(DashboardCronService cronService) {
        this.cronService = cronService;
    }

    @Mapping(value = "/api/cron/jobs", method = MethodType.GET)
    public List<Map<String, Object>> jobs() throws Exception {
        return cronService.listJobs();
    }

    @Mapping(value = "/api/cron/jobs", method = MethodType.POST)
    public Map<String, Object> create(Context context) throws Exception {
        return DashboardResponse.ok(
                cronService.create(
                        ONode.deserialize(
                                ONode.ofJson(context.body()).toJson(), LinkedHashMap.class)));
    }

    @Mapping(value = "/api/cron/jobs/{id}/pause", method = MethodType.POST)
    public Map<String, Object> pause(String id) throws Exception {
        return DashboardResponse.ok(cronService.pause(id));
    }

    @Mapping(value = "/api/cron/jobs/{id}/resume", method = MethodType.POST)
    public Map<String, Object> resume(String id) throws Exception {
        return DashboardResponse.ok(cronService.resume(id));
    }

    @Mapping(value = "/api/cron/jobs/{id}/trigger", method = MethodType.POST)
    public Map<String, Object> trigger(String id) throws Exception {
        return DashboardResponse.ok(cronService.trigger(id));
    }

    @Mapping(value = "/api/cron/jobs/{id}", method = MethodType.DELETE)
    public Map<String, Object> delete(String id) throws Exception {
        return DashboardResponse.ok(cronService.delete(id));
    }
}
