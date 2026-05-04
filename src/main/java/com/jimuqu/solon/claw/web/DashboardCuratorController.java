package com.jimuqu.solon.claw.web;

import java.util.Map;
import java.util.LinkedHashMap;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard Curator endpoints. */
@Controller
public class DashboardCuratorController {
    private final DashboardCuratorService curatorService;

    public DashboardCuratorController(DashboardCuratorService curatorService) {
        this.curatorService = curatorService;
    }

    @Mapping(value = "/api/hermes/curator", method = MethodType.GET)
    public Map<String, Object> list(Context context) throws Exception {
        return DashboardResponse.ok(curatorService.list(context.paramAsInt("limit", 20)));
    }

    @Mapping(value = "/api/hermes/curator/run", method = MethodType.POST)
    public Map<String, Object> run(Context context) throws Exception {
        return DashboardResponse.ok(
                curatorService.run(Boolean.parseBoolean(context.param("force"))));
    }

    @Mapping(value = "/api/hermes/curator/{reportId}", method = MethodType.GET)
    public Map<String, Object> detail(String reportId) throws Exception {
        return DashboardResponse.ok(curatorService.detail(reportId));
    }

    @Mapping(value = "/api/hermes/curator/improvements", method = MethodType.GET)
    public Map<String, Object> improvements(Context context) throws Exception {
        return DashboardResponse.ok(curatorService.improvements(context.paramAsInt("limit", 20)));
    }

    @Mapping(value = "/api/hermes/curator/apply", method = MethodType.POST)
    public Map<String, Object> apply(Context context) throws Exception {
        Map<String, Object> body =
                ONode.deserialize(ONode.ofJson(context.body()).toJson(), LinkedHashMap.class);
        return DashboardResponse.ok(
                curatorService.apply(read(body, "skill"), read(body, "suggestion")));
    }

    @Mapping(value = "/api/hermes/curator/ignore", method = MethodType.POST)
    public Map<String, Object> ignore(Context context) throws Exception {
        Map<String, Object> body =
                ONode.deserialize(ONode.ofJson(context.body()).toJson(), LinkedHashMap.class);
        return DashboardResponse.ok(
                curatorService.ignore(read(body, "skill"), read(body, "suggestion")));
    }

    private String read(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
