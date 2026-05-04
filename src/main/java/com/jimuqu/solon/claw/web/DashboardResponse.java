package com.jimuqu.solon.claw.web;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DashboardResponse {
    private DashboardResponse() {}

    public static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("data", data == null ? new LinkedHashMap<String, Object>() : data);
        return result;
    }

    public static Map<String, Object> error(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", false);
        result.put("code", code == null ? "ERROR" : code);
        result.put("error", message == null ? "" : message);
        return result;
    }
}
