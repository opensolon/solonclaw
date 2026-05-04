package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillImportResult;
import java.io.File;

/** 技能导入服务接口。 */
public interface SkillImportService {
    SkillImportResult processPendingImports(boolean force) throws Exception;

    HubInstallRecord installBundle(
            SkillBundle bundle, String category, boolean force, File sourceArtifact)
            throws Exception;
}
