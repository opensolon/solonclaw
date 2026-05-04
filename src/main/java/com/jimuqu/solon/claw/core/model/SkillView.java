package com.jimuqu.solon.claw.core.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 技能全文或支持文件读取结果。 */
@Getter
@Setter
@NoArgsConstructor
public class SkillView {
    /** 对应技能描述。 */
    private SkillDescriptor descriptor;

    /** 被读取的相对文件路径，为空表示 SKILL.md。 */
    private String filePath;

    /** 读取出的内容。 */
    private String content;

    /** 可进一步读取的支持文件列表。 */
    private List<String> linkedFiles = new ArrayList<String>();
}
