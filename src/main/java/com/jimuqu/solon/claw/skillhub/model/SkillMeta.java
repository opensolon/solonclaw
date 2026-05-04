package com.jimuqu.solon.claw.skillhub.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Skills Hub 统一元数据。 */
@Getter
@Setter
@NoArgsConstructor
public class SkillMeta {
    private String name;
    private String description;
    private String source;
    private String identifier;
    private String trustLevel;
    private String repo;
    private String path;
    private List<String> tags = new ArrayList<String>();
    private Map<String, Object> extra = new LinkedHashMap<String, Object>();
}
