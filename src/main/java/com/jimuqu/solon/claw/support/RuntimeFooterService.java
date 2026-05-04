package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.AgentRunOutcome;
import java.io.File;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 最终回复运行态 footer 渲染。 */
@RequiredArgsConstructor
public class RuntimeFooterService {
    private static final String SEPARATOR = " · ";

    private final AppConfig appConfig;

    public String appendFooter(String reply, PlatformType platform, AgentRunOutcome outcome) {
        String footer = buildFooter(platform, outcome);
        if (StrUtil.isBlank(footer)) {
            return StrUtil.nullToEmpty(reply);
        }
        String base = StrUtil.nullToEmpty(reply).trim();
        if (base.length() == 0) {
            return footer;
        }
        return base + "\n\n" + footer;
    }

    public String buildFooter(PlatformType platform, AgentRunOutcome outcome) {
        if (outcome == null || !isEnabled(platform)) {
            return "";
        }
        List<String> fields = appConfig.getDisplay().getRuntimeFooter().getFields();
        StringBuilder buffer = new StringBuilder();
        for (String field : fields) {
            String value = resolveField(field, outcome);
            if (StrUtil.isBlank(value)) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append(SEPARATOR);
            }
            buffer.append(value);
        }
        if (buffer.length() == 0) {
            return "";
        }
        return "—— " + buffer.toString();
    }

    private boolean isEnabled(PlatformType platform) {
        Boolean platformOverride = platformOverride(platform);
        if (platformOverride != null) {
            return platformOverride.booleanValue();
        }
        return appConfig.getDisplay().getRuntimeFooter().isEnabled();
    }

    private Boolean platformOverride(PlatformType platform) {
        if (platform == null) {
            return null;
        }
        if (platform == PlatformType.FEISHU) {
            return appConfig.getChannels().getFeishu().getRuntimeFooterEnabled();
        }
        if (platform == PlatformType.DINGTALK) {
            return appConfig.getChannels().getDingtalk().getRuntimeFooterEnabled();
        }
        if (platform == PlatformType.WECOM) {
            return appConfig.getChannels().getWecom().getRuntimeFooterEnabled();
        }
        if (platform == PlatformType.WEIXIN) {
            return appConfig.getChannels().getWeixin().getRuntimeFooterEnabled();
        }
        if (platform == PlatformType.QQBOT) {
            return appConfig.getChannels().getQqbot().getRuntimeFooterEnabled();
        }
        if (platform == PlatformType.YUANBAO) {
            return appConfig.getChannels().getYuanbao().getRuntimeFooterEnabled();
        }
        return null;
    }

    private String resolveField(String rawField, AgentRunOutcome outcome) {
        String field = StrUtil.nullToEmpty(rawField).trim();
        if ("model".equals(field)) {
            return shortModel(outcome.getModel());
        }
        if ("provider".equals(field)) {
            return StrUtil.nullToEmpty(outcome.getProvider()).trim();
        }
        if ("context_pct".equals(field)) {
            if (outcome.getContextWindowTokens() <= 0 || outcome.getContextEstimateTokens() < 0) {
                return "";
            }
            int percent =
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    (int)
                                            Math.round(
                                                    (outcome.getContextEstimateTokens() * 100.0D)
                                                            / outcome.getContextWindowTokens())));
            return percent + "%";
        }
        if ("cwd".equals(field)) {
            return compactCwd(outcome.getCwd());
        }
        if ("tokens".equals(field)
                && outcome.getResult() != null
                && outcome.getResult().getTotalTokens() > 0) {
            return outcome.getResult().getTotalTokens() + " tokens";
        }
        return "";
    }

    private String shortModel(String model) {
        String value = StrUtil.nullToEmpty(model).trim();
        int slash = value.lastIndexOf('/');
        return slash >= 0 && slash < value.length() - 1 ? value.substring(slash + 1) : value;
    }

    private String compactCwd(String cwd) {
        String value = StrUtil.nullToEmpty(cwd).trim();
        if (value.length() == 0) {
            return "";
        }
        try {
            String home = System.getProperty("user.home");
            String absolute = new File(value).getAbsolutePath();
            if (StrUtil.isNotBlank(home) && absolute.startsWith(home)) {
                return "~" + absolute.substring(home.length());
            }
            return absolute;
        } catch (Exception ignored) {
            return value;
        }
    }
}
