package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 会话搜索结果条目。 */
@Getter
@Setter
@NoArgsConstructor
public class SessionSearchEntry {
    /** 会话 ID。 */
    private String sessionId;

    /** 分支名。 */
    private String branchName;

    /** 标题。 */
    private String title;

    /** 最近更新时间。 */
    private long updatedAt;

    /** 匹配预览。 */
    private String matchPreview;

    /** 聚焦总结。 */
    private String summary;

    private String runId;
    private String toolName;
    private String channel;
    private long score;
}
