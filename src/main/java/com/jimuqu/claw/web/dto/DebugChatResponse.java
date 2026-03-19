package com.jimuqu.claw.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述调试页提交消息后的响应体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebugChatResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 新创建的运行任务标识。 */
    private String runId;
    /** 所属内部会话键。 */
    private String sessionKey;
    /** 当前运行状态。 */
    private String status;
}
