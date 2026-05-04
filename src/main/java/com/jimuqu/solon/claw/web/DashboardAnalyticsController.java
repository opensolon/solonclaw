package com.jimuqu.solon.claw.web;

import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 分析接口。 */
@Controller
public class DashboardAnalyticsController {
    private final DashboardAnalyticsService analyticsService;

    public DashboardAnalyticsController(DashboardAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Mapping(value = "/api/analytics/usage", method = MethodType.GET)
    public Map<String, Object> usage(int days) throws Exception {
        return analyticsService.getUsage(days);
    }
}
