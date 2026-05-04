package com.jimuqu.solon.claw.core.model;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** pairing 申请记录。 */
@Getter
@Setter
@NoArgsConstructor
public class PairingRequestRecord {
    /** 所属平台。 */
    private PlatformType platform;

    /** pairing code。 */
    private String code;

    /** 申请用户 ID。 */
    private String userId;

    /** 申请用户名称。 */
    private String userName;

    /** 发起申请的会话 ID。 */
    private String chatId;

    /** 创建时间。 */
    private long createdAt;

    /** 过期时间。 */
    private long expiresAt;
}
