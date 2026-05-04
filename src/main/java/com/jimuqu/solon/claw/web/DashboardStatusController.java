package com.jimuqu.solon.claw.web;

import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 状态接口。 */
@Controller
public class DashboardStatusController {
    private final DashboardStatusService statusService;
    private final DashboardAuthService authService;

    public DashboardStatusController(
            DashboardStatusService statusService, DashboardAuthService authService) {
        this.statusService = statusService;
        this.authService = authService;
    }

    @Mapping(value = "/api/status", method = MethodType.GET)
    public Map<String, Object> status(Context context) throws Exception {
        return DashboardResponse.ok(statusService.getStatus(authService.isAuthorized(context)));
    }

    @Mapping(value = "/api/model/info", method = MethodType.GET)
    public Map<String, Object> modelInfo(Context context) {
        return DashboardResponse.ok(statusService.getModelInfo(authService.isAuthorized(context)));
    }
}
