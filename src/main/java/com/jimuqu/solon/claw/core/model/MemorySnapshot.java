package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 长期记忆快照。 */
@Getter
@Setter
@NoArgsConstructor
public class MemorySnapshot {
    /** MEMORY.md 当前内容。 */
    private String memoryText;

    /** USER.md 当前内容。 */
    private String userText;

    /** 当日 memory/YYYY-MM-DD.md 内容。 */
    private String dailyMemoryText;
}
