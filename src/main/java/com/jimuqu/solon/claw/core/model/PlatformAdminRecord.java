package com.jimuqu.solon.claw.core.model;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 平台唯一管理员记录。 */
@Getter
@Setter
@NoArgsConstructor
public class PlatformAdminRecord {
    /** 所属平台。 */
    private PlatformType platform;

    /** 管理员用户 ID。 */
    private String userId;

    /** 管理员名称。 */
    private String userName;

    /** 认领时所在私聊会话 ID。 */
    private String chatId;

    /** 创建时间。 */
    private long createdAt;
}
