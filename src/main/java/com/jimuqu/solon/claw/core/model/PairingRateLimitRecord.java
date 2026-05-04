package com.jimuqu.solon.claw.core.model;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** pairing 限流记录。 */
@Getter
@Setter
@NoArgsConstructor
public class PairingRateLimitRecord {
    /** 所属平台。 */
    private PlatformType platform;

    /** 用户 ID。 */
    private String userId;

    /** 最近一次请求时间。 */
    private long requestedAt;

    /** 连续失败次数。 */
    private int failedAttempts;

    /** 锁定截至时间。 */
    private long lockoutUntil;
}
