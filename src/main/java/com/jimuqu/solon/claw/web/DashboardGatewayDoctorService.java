package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.ChannelStatus;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Dashboard 网关 doctor 聚合服务。 */
public class DashboardGatewayDoctorService {
    private final AppConfig appConfig;
    private final DeliveryService deliveryService;
    private final com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
            gatewayRuntimeRefreshService;

    public DashboardGatewayDoctorService(
            AppConfig appConfig,
            DeliveryService deliveryService,
            com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
                    gatewayRuntimeRefreshService) {
        this.appConfig = appConfig;
        this.deliveryService = deliveryService;
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
    }

    public Map<String, Object> doctor() throws Exception {
        gatewayRuntimeRefreshService.refreshIfNeeded();
        List<Map<String, Object>> platforms = new ArrayList<Map<String, Object>>();
        List<ChannelStatus> statuses = deliveryService.statuses();
        for (String platform : Arrays.asList("feishu", "dingtalk", "wecom", "weixin")) {
            ChannelStatus status = findStatus(statuses, platform);
            if (status != null) {
                platforms.add(toDoctorItem(status));
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("generated_at", isoNow());
        result.put("runtime_home", appConfig.getRuntime().getHome());
        result.put("platforms", platforms);
        return result;
    }

    private ChannelStatus findStatus(List<ChannelStatus> statuses, String platformName) {
        for (ChannelStatus status : statuses) {
            if (status.getPlatform() != null
                    && platformName.equalsIgnoreCase(status.getPlatform().name())) {
                return status;
            }
        }
        return null;
    }

    private Map<String, Object> toDoctorItem(ChannelStatus status) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put(
                "platform",
                status.getPlatform() == null ? null : status.getPlatform().name().toLowerCase());
        item.put("enabled", status.isEnabled());
        item.put("connected", status.isConnected());
        item.put("detail", status.getDetail());
        item.put("setup_state", status.getSetupState());
        item.put("connection_mode", status.getConnectionMode());
        item.put("missing_config", status.getMissingConfig());
        item.put("features", status.getFeatures());
        item.put("last_error_code", status.getLastErrorCode());
        item.put("last_error_message", status.getLastErrorMessage());
        item.put("next_step", nextStep(status));
        return item;
    }

    private String nextStep(ChannelStatus status) {
        if (!status.isEnabled()) {
            return "在配置中启用该渠道。";
        }
        if (status.getMissingConfig() != null && !status.getMissingConfig().isEmpty()) {
            return "补齐缺失配置：" + join(status.getMissingConfig());
        }
        if ("connected".equalsIgnoreCase(status.getSetupState()) || status.isConnected()) {
            return "渠道已连通，可直接开始使用。";
        }
        if (StrUtil.isNotBlank(status.getLastErrorMessage())) {
            return "修复最近一次连接错误后重试。";
        }
        if ("disabled".equalsIgnoreCase(status.getSetupState())) {
            return "渠道当前未启用。";
        }
        return "配置已就绪，等待网关连接。";
    }

    private String join(List<String> values) {
        StringBuilder buffer = new StringBuilder();
        for (String value : values) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(value.trim());
        }
        return buffer.length() == 0 ? GatewayBehaviorConstants.NONE : buffer.toString();
    }

    private String isoNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }
}
