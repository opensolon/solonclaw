package com.jimuqu.solon.claw.skillhub.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Browse/search 结果页。 */
@Getter
@Setter
@NoArgsConstructor
public class SkillBrowseResult {
    private List<SkillMeta> items = new ArrayList<SkillMeta>();
    private int total;
    private int page;
    private int pageSize;
    private List<String> timedOutSources = new ArrayList<String>();
}
