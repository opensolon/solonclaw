package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.skillhub.model.InstallDecision;
import com.jimuqu.solon.claw.skillhub.model.ScanResult;
import java.io.File;

/** 技能安全扫描服务。 */
public interface SkillGuardService {
    ScanResult scanSkill(File skillPath, String source) throws Exception;

    InstallDecision shouldAllowInstall(ScanResult result, boolean force);

    String formatReport(ScanResult result);
}
