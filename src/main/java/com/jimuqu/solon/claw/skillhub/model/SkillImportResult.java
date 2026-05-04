package com.jimuqu.solon.claw.skillhub.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 自动导入结果。 */
@Getter
@Setter
@NoArgsConstructor
public class SkillImportResult {
    private int installedCount;
    private int blockedCount;
    private int archivedCount;
    private List<String> messages = new ArrayList<String>();
}
