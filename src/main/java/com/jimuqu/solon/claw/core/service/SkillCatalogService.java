package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.SkillDescriptor;
import com.jimuqu.solon.claw.core.model.SkillView;
import java.util.List;

/** 技能目录与读取服务接口。 */
public interface SkillCatalogService {
    /** 列出技能。 */
    List<SkillDescriptor> listSkills(String category) throws Exception;

    /** 查看技能或支持文件。 */
    SkillView viewSkill(String nameOrPath, String filePath) throws Exception;

    /** 生成技能索引摘要，用于渐进披露提示词。 */
    String renderSkillIndexPrompt(String sourceKey) throws Exception;

    /** 判断技能是否对当前来源键可见。 */
    boolean isVisible(String sourceKey, String canonicalName) throws Exception;

    /** 设置技能可见性。 */
    void setVisible(String sourceKey, String canonicalName, boolean visible) throws Exception;
}
