package com.jimuqu.solon.claw.bootstrap;

import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;

/** 健康检查控制器。 */
@Controller
public class HealthController {
    /**
     * 返回服务存活状态。
     *
     * @return 健康检查响应
     */
    @Mapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("service", "solon-claw");
        return result;
    }
}
