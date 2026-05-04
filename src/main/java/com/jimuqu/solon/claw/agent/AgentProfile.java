package com.jimuqu.solon.claw.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentProfile {
    private String agentName;
    private String displayName;
    private String description;
    private String rolePrompt;
    private String defaultModel;
    private String model;
    private String allowedToolsJson;
    private String skillsJson;
    private String memory;
    private boolean enabled = true;
    private long lastUsedAt;
    private long createdAt;
    private long updatedAt;

    public String getDefaultModel() {
        if (StrUtil.isNotBlank(defaultModel)) {
            return defaultModel;
        }
        return model;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
        this.model = defaultModel;
    }

    public String getModel() {
        return getDefaultModel();
    }

    public void setModel(String model) {
        setDefaultModel(model);
    }
}
