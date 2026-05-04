package com.jimuqu.solon.claw.skillhub.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 技能扫描单条发现。 */
@Getter
@Setter
@NoArgsConstructor
public class Finding {
    private String patternId;
    private String severity;
    private String category;
    private String file;
    private int line;
    private String match;
    private String description;
}
