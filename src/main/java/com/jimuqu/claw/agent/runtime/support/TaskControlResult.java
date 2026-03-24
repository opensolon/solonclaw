package com.jimuqu.claw.agent.runtime.support;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子任务控制结果。
 */
@Data
@NoArgsConstructor
public class TaskControlResult {
    private String code;
    private boolean success;
    private String message;
}
