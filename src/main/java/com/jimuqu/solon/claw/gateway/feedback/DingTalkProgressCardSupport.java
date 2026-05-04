package com.jimuqu.solon.claw.gateway.feedback;

import org.noear.snack4.ONode;

/** 钉钉长任务进度卡数据辅助。 */
public final class DingTalkProgressCardSupport {
    private DingTalkProgressCardSupport() {}

    public static String buildCardData(
            String title, String status, String summary, String detail, String updatedAt) {
        return new ONode()
                .set("title", title)
                .set("status", status)
                .set("summary", summary)
                .set("detail", detail)
                .set("updatedAt", updatedAt)
                .toJson();
    }
}
