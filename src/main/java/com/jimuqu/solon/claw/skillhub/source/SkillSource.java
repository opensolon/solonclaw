package com.jimuqu.solon.claw.skillhub.source;

import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import java.util.List;

/** Skills Hub 来源抽象。 */
public interface SkillSource {
    List<SkillMeta> search(String query, int limit) throws Exception;

    SkillBundle fetch(String identifier) throws Exception;

    SkillMeta inspect(String identifier) throws Exception;

    String sourceId();

    String trustLevelFor(String identifier);
}
