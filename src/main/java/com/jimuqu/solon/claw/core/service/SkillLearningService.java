package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;

/** 任务后自动学习闭环服务接口。 */
public interface SkillLearningService {
    /** 在主回复发送后异步触发学习闭环。 */
    void schedulePostReplyLearning(
            SessionRecord session, GatewayMessage message, GatewayReply reply) throws Exception;
}
