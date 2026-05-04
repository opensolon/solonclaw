package com.jimuqu.solon.claw.core.model;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 已批准用户记录。 */
@Getter
@Setter
@NoArgsConstructor
public class ApprovedUserRecord {
    /** 所属平台。 */
    private PlatformType platform;

    /** 用户唯一标识。 */
    private String userId;

    /** 用户展示名。 */
    private String userName;

    /** 批准时间。 */
    private long approvedAt;

    /** 批准来源。 */
    private String approvedBy;
}
