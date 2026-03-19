package com.jimuqu.claw.agent.job;

import com.jimuqu.claw.agent.model.route.ReplyTarget;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 定义一个可持久化的定时任务。
 */
@Data
@NoArgsConstructor
public class JobDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String mode;
    private String scheduleValue;
    private long initialDelay;
    private String zone;
    private boolean enabled = true;
    private String prompt;
    private String sessionKey;
    private ReplyTarget replyTarget;
    private long createdAt;
    private long updatedAt;
}
