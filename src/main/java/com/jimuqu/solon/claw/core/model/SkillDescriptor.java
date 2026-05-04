package com.jimuqu.solon.claw.core.model;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 技能目录中的最小元数据描述。 */
@Getter
@Setter
@NoArgsConstructor
public class SkillDescriptor {
    /** 技能名。 */
    private String name;

    /** 分类名，为空表示根目录技能。 */
    private String category;

    /** 技能描述。 */
    private String description;

    /** 技能目录绝对路径。 */
    private String skillDir;

    /** 目录中可见的支持文件相对路径列表。 */
    private List<String> linkedFiles = new ArrayList<String>();

    /** 技能来源。 */
    private String source;

    /** 来源标识符。 */
    private String identifier;

    /** 信任级别。 */
    private String trustLevel;

    /** 标签。 */
    private List<String> tags = new ArrayList<String>();

    /** 平台限制。 */
    private List<String> platforms = new ArrayList<String>();

    /** 原始 metadata。 */
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    /** setup 状态。 */
    private String setupState;

    /** 返回用于展示/定位的规范名。 */
    public String canonicalName() {
        return StrUtil.isBlank(category) ? name : category + "/" + name;
    }
}
