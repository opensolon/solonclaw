package com.jimuqu.solon.claw.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard MCP endpoints. */
@Controller
public class DashboardMcpController {
    private final DashboardMcpService mcpService;

    public DashboardMcpController(DashboardMcpService mcpService) {
        this.mcpService = mcpService;
    }

    @Mapping(value = "/api/hermes/mcp", method = MethodType.GET)
    public Map<String, Object> list() throws Exception {
        return DashboardResponse.ok(mcpService.list());
    }

    @Mapping(value = "/api/hermes/mcp", method = MethodType.POST)
    public Map<String, Object> save(Context context) throws Exception {
        return DashboardResponse.ok(
                mcpService.save(
                        ONode.deserialize(
                                ONode.ofJson(context.body()).toJson(), LinkedHashMap.class)));
    }

    @Mapping(value = "/api/hermes/mcp/{serverId}/check", method = MethodType.POST)
    public Map<String, Object> check(String serverId) throws Exception {
        return DashboardResponse.ok(mcpService.check(serverId));
    }

    @Mapping(value = "/api/hermes/mcp/{serverId}", method = MethodType.DELETE)
    public Map<String, Object> delete(String serverId) throws Exception {
        return DashboardResponse.ok(mcpService.delete(serverId));
    }
}
