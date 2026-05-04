package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.support.constants.AgentSettingConstants;

/** 聊天窗口中间态显示设置服务。 */
public class DisplaySettingsService {
    private final AppConfig appConfig;
    private final GlobalSettingRepository globalSettingRepository;

    public DisplaySettingsService(
            AppConfig appConfig, GlobalSettingRepository globalSettingRepository) {
        this.appConfig = appConfig;
        this.globalSettingRepository = globalSettingRepository;
    }

    /** 解析渠道默认工具进度模式。 */
    public String resolveToolProgress(PlatformType platform) {
        String configured =
                channelConfig(platform) == null ? null : channelConfig(platform).getToolProgress();
        if (StrUtil.isNotBlank(configured)) {
            return normalizeToolProgress(configured);
        }
        return normalizeToolProgress(appConfig.getDisplay().getToolProgress());
    }

    /** 当前来源键是否展示 reasoning。 */
    public boolean isReasoningVisible(String sourceKey, PlatformType platform) {
        if (globalSettingRepository != null && StrUtil.isNotBlank(sourceKey)) {
            try {
                String stored =
                        globalSettingRepository.get(
                                AgentSettingConstants.DISPLAY_REASONING_VISIBLE_PREFIX + sourceKey);
                if (StrUtil.isNotBlank(stored)) {
                    return parseBoolean(stored, false);
                }
            } catch (Exception ignored) {
                // best effort
            }
        }
        return appConfig.getDisplay().isShowReasoning();
    }

    /** 持久化当前来源键的 reasoning 展示设置。 */
    public void setReasoningVisible(String sourceKey, boolean visible) throws Exception {
        if (globalSettingRepository == null || StrUtil.isBlank(sourceKey)) {
            return;
        }
        globalSettingRepository.set(
                AgentSettingConstants.DISPLAY_REASONING_VISIBLE_PREFIX + sourceKey,
                visible ? "true" : "false");
    }

    /** 返回 reasoning 显示状态说明。 */
    public String describeReasoning(String sourceKey, PlatformType platform) {
        return isReasoningVisible(sourceKey, platform) ? "show" : "hide";
    }

    /** 预览长度。 */
    public int toolPreviewLength() {
        return Math.max(20, appConfig.getDisplay().getToolPreviewLength());
    }

    /** 进度/思考节流毫秒数。 */
    public int progressThrottleMs() {
        return Math.max(0, appConfig.getDisplay().getProgressThrottleMs());
    }

    /** 钉钉长任务进度卡模板 ID。 */
    public String dingtalkProgressCardTemplateId() {
        AppConfig.ChannelConfig config = channelConfig(PlatformType.DINGTALK);
        return config == null ? "" : StrUtil.nullToEmpty(config.getProgressCardTemplateId()).trim();
    }

    private AppConfig.ChannelConfig channelConfig(PlatformType platform) {
        if (platform == null) {
            return null;
        }
        if (platform == PlatformType.FEISHU) {
            return appConfig.getChannels().getFeishu();
        }
        if (platform == PlatformType.DINGTALK) {
            return appConfig.getChannels().getDingtalk();
        }
        if (platform == PlatformType.WECOM) {
            return appConfig.getChannels().getWecom();
        }
        if (platform == PlatformType.WEIXIN) {
            return appConfig.getChannels().getWeixin();
        }
        return null;
    }

    private String normalizeToolProgress(String value) {
        String normalized = StrUtil.nullToEmpty(value).trim().toLowerCase();
        if ("new".equals(normalized) || "all".equals(normalized) || "verbose".equals(normalized)) {
            return normalized;
        }
        return "off";
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (StrUtil.isBlank(value)) {
            return fallback;
        }
        String normalized = value.trim();
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "show".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized);
    }
}
