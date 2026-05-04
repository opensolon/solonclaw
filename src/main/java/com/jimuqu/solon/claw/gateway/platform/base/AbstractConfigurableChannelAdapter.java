package com.jimuqu.solon.claw.gateway.platform.base;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.ChannelStatus;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.service.ChannelAdapter;
import com.jimuqu.solon.claw.core.service.InboundMessageHandler;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 可配置渠道适配器基类，负责处理启用状态、连接状态和基础日志。 */
public abstract class AbstractConfigurableChannelAdapter implements ChannelAdapter {
    /** 渠道日志器。 */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 当前适配器对应的平台。 */
    private final PlatformType platformType;

    /** 动态渠道配置引用。 */
    private final AppConfig.ChannelConfig channelConfig;

    /** 当前连接状态。 */
    private boolean connected;

    /** 当前详情描述。 */
    private String detail;

    /** 渠道 setup 状态。 */
    private String setupState;

    /** 渠道连接模式。 */
    private String connectionMode;

    /** 缺失配置路径。 */
    private final List<String> missingConfig = new ArrayList<String>();

    /** 功能标签。 */
    private final List<String> features = new ArrayList<String>();

    /** 最近一次错误码。 */
    private String lastErrorCode;

    /** 最近一次错误消息。 */
    private String lastErrorMessage;

    /** 入站消息处理器。 */
    private InboundMessageHandler inboundMessageHandler;

    /** 构造基础适配器。 */
    protected AbstractConfigurableChannelAdapter(
            PlatformType platformType, AppConfig.ChannelConfig config) {
        this.platformType = platformType;
        this.channelConfig = config;
        this.detail = isEnabled() ? "configured" : "disabled";
        this.setupState = isEnabled() ? "configured" : "disabled";
        this.connectionMode = "custom";
    }

    /** 返回所属平台。 */
    @Override
    public PlatformType platform() {
        return platformType;
    }

    /** 返回是否启用。 */
    @Override
    public boolean isEnabled() {
        return channelConfig != null && channelConfig.isEnabled();
    }

    /** 建立基础连接状态。 */
    @Override
    public boolean connect() {
        if (!isEnabled()) {
            detail = "disabled";
            return false;
        }

        connected = true;
        detail = "adapter scaffold ready";
        return true;
    }

    /** 断开连接。 */
    @Override
    public void disconnect() {
        connected = false;
    }

    /** 返回当前是否已连接。 */
    @Override
    public boolean isConnected() {
        return connected;
    }

    /** 返回当前详情。 */
    @Override
    public String detail() {
        return detail;
    }

    /** 默认发送实现仅打日志，具体渠道可覆盖。 */
    @Override
    public void send(DeliveryRequest request) throws Exception {
        log.info("[{}:{}] {}", request.getPlatform(), request.getChatId(), request.getText());
    }

    @Override
    public ChannelStatus statusSnapshot() {
        ChannelStatus status = new ChannelStatus(platformType, isEnabled(), connected, detail);
        status.setSetupState(setupState);
        status.setConnectionMode(connectionMode);
        status.setMissingConfig(new ArrayList<String>(missingConfig));
        status.setFeatures(new ArrayList<String>(features));
        status.setLastErrorCode(lastErrorCode);
        status.setLastErrorMessage(lastErrorMessage);
        return status;
    }

    /** 注册入站消息处理器。 */
    @Override
    public void setInboundMessageHandler(InboundMessageHandler inboundMessageHandler) {
        this.inboundMessageHandler = inboundMessageHandler;
    }

    /** 供子类读取当前入站处理器。 */
    protected InboundMessageHandler inboundMessageHandler() {
        return inboundMessageHandler;
    }

    /** 更新连接状态。 */
    protected void setConnected(boolean connected) {
        this.connected = connected;
    }

    /** 更新详情。 */
    protected void setDetail(String detail) {
        this.detail = detail;
    }

    /** 标记渠道连接模式。 */
    protected void setConnectionMode(String connectionMode) {
        this.connectionMode = connectionMode == null ? "custom" : connectionMode;
    }

    /** 标记渠道 setup 状态。 */
    protected void setSetupState(String setupState) {
        this.setupState = setupState == null ? GatewayBehaviorConstants.NONE : setupState;
    }

    /** 覆盖缺失配置项。 */
    protected void setMissingConfig(String... values) {
        missingConfig.clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                missingConfig.add(value.trim());
            }
        }
    }

    /** 覆盖缺失配置项。 */
    protected void setMissingConfig(List<String> values) {
        missingConfig.clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                missingConfig.add(value.trim());
            }
        }
    }

    /** 设置功能标签。 */
    protected void setFeatures(String... values) {
        features.clear();
        if (values != null) {
            features.addAll(Arrays.asList(values));
        }
    }

    /** 返回功能标签快照。 */
    protected List<String> features() {
        return Collections.unmodifiableList(features);
    }

    /** 清理最近一次错误。 */
    protected void clearLastError() {
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /** 记录最近一次错误。 */
    protected void setLastError(String code, String message) {
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
    }
}
