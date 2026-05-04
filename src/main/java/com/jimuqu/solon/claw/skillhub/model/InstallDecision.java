package com.jimuqu.solon.claw.skillhub.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 安装决策结果。 */
@Getter
@Setter
@NoArgsConstructor
public class InstallDecision {
    private boolean allowed;
    private boolean requiresConfirmation;
    private String reason;
}
