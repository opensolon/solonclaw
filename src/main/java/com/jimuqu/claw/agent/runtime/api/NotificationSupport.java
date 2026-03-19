package com.jimuqu.claw.agent.runtime.api;

import com.jimuqu.claw.agent.runtime.support.NotificationResult;

/**
 * 为当前运行提供主动通知用户的能力。
 */
public interface NotificationSupport {
    /**
     * 向当前会话已绑定的用户侧目标发送通知。
     *
     * @param message 通知内容
     * @param progress 是否标记为进度通知
     * @return 通知结果
     */
    NotificationResult notifyUser(String message, boolean progress);
}

