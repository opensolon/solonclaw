package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 子代理委托任务。 */
@Getter
@Setter
@NoArgsConstructor
public class DelegationTask {
    /** 任务名称。 */
    private String name;

    /** 委托目标。 */
    private String prompt;

    /** 可选短上下文。 */
    private String context;

    /** 允许子代理使用的工具名列表。 */
    private java.util.List<String> allowedTools;

    /** 期望输出格式说明。 */
    private String expectedOutput;

    /** 可写入范围说明。 */
    private String writeScope;
}
