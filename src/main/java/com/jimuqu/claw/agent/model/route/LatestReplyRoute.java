package com.jimuqu.claw.agent.model.route;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 保存最近一次可用于外发的会话路由信息。
 */
@Data
@NoArgsConstructor
public class LatestReplyRoute implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 最近一次路由命中的内部会话键。 */
    private String sessionKey;
    /** 最近一次可外发的回复目标。 */
    private ReplyTarget replyTarget;
}
