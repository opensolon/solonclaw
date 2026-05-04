package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard Agents 管理接口。 */
@Controller
public class DashboardAgentController {
    private final DashboardAgentService agentService;

    public DashboardAgentController(DashboardAgentService agentService) {
        this.agentService = agentService;
    }

    @Mapping(value = "/api/agents", method = MethodType.GET)
    public Map<String, Object> agents(Context context) throws Exception {
        return DashboardResponse.ok(agentService.list(context.param("session_id")));
    }

    @Mapping(value = "/api/agents", method = MethodType.POST)
    public Map<String, Object> create(Context context) throws Exception {
        return DashboardResponse.ok(agentService.create(body(context)));
    }

    @Mapping(value = "/api/agents/{name}", method = MethodType.GET)
    public Map<String, Object> get(String name, Context context) throws Exception {
        return DashboardResponse.ok(agentService.get(name, context.param("session_id")));
    }

    @Mapping(value = "/api/agents/{name}", method = MethodType.PUT)
    public Map<String, Object> update(String name, Context context) throws Exception {
        return DashboardResponse.ok(agentService.update(name, body(context)));
    }

    @Mapping(value = "/api/agents/{name}/activate", method = MethodType.POST)
    public Map<String, Object> activate(String name, Context context) throws Exception {
        return DashboardResponse.ok(agentService.activate(name, body(context)));
    }

    @Mapping(value = "/api/agents/{name}", method = MethodType.DELETE)
    public Map<String, Object> delete(String name) throws Exception {
        return DashboardResponse.ok(agentService.delete(name));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(Context context) throws Exception {
        String raw = context.body();
        if (StrUtil.isBlank(raw)) {
            return new LinkedHashMap<String, Object>();
        }
        return ONode.deserialize(ONode.ofJson(raw).toJson(), LinkedHashMap.class);
    }
}
