package com.jimuqu.solon.claw.core.model;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 渠道运行状态快照。 */
@Getter
@Setter
@NoArgsConstructor
public class ChannelStatus {
    /** 所属平台。 */
    private PlatformType platform;

    /** 是否已启用。 */
    private boolean enabled;

    /** 是否已经建立连接。 */
    private boolean connected;

    /** 连接详情说明。 */
    private String detail;

    /** 渠道配置与接入准备状态。 */
    private String setupState;

    /** 当前渠道连接模式。 */
    private String connectionMode;

    /** 缺失的关键配置路径。 */
    private List<String> missingConfig = new ArrayList<String>();

    /** 当前渠道已实现的能力标签。 */
    private List<String> features = new ArrayList<String>();

    /** 最近一次错误码。 */
    private String lastErrorCode;

    /** 最近一次错误消息。 */
    private String lastErrorMessage;

    public ChannelStatus(PlatformType platform, boolean enabled, boolean connected, String detail) {
        this.platform = platform;
        this.enabled = enabled;
        this.connected = connected;
        this.detail = detail;
    }
}
