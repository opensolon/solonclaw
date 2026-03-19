package com.jimuqu.claw.agent.model.route;

/**
 * 保存最近一次可用于外发的会话路由信息。
 */
public class LatestReplyRoute {
    /** 最近一次路由命中的内部会话键。 */
    private String sessionKey;
    /** 最近一次可外发的回复目标。 */
    private ReplyTarget replyTarget;

    /**
     * 返回内部会话键。
     *
     * @return 内部会话键
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * 设置内部会话键。
     *
     * @param sessionKey 内部会话键
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    /**
     * 返回回复目标。
     *
     * @return 回复目标
     */
    public ReplyTarget getReplyTarget() {
        return replyTarget;
    }

    /**
     * 设置回复目标。
     *
     * @param replyTarget 回复目标
     */
    public void setReplyTarget(ReplyTarget replyTarget) {
        this.replyTarget = replyTarget;
    }
}

