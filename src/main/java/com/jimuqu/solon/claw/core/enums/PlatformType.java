package com.jimuqu.solon.claw.core.enums;

/** 支持的消息平台枚举。 */
public enum PlatformType {
    /** 内存网关与测试专用平台。 */
    MEMORY,

    /** 飞书平台。 */
    FEISHU,

    /** 钉钉平台。 */
    DINGTALK,

    /** 企业微信平台。 */
    WECOM,

    /** 微信平台。 */
    WEIXIN,

    /** QQ Bot 平台。 */
    QQBOT,

    /** 腾讯元宝平台。 */
    YUANBAO;

    /**
     * 按名称解析平台，无法识别时返回 null。
     *
     * @param name 平台名
     * @return 平台枚举
     */
    public static PlatformType fromName(String name) {
        if (name == null) {
            return null;
        }

        String normalized = name.trim().toUpperCase();
        for (PlatformType value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }

        return null;
    }
}
