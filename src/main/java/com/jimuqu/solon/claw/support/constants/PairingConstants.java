package com.jimuqu.solon.claw.support.constants;

/** 管理员认领与 pairing 相关常量。 */
public interface PairingConstants {
    /** pairing code 的字符集，排除容易混淆的字符。 */
    String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** 管理员自动认领的内部 code。 */
    String ADMIN_CLAIM_CODE = "__ADMIN_CLAIM__";

    /** 首个管理员自动批准来源标记。 */
    String SELF_ADMIN_CLAIM = "self-admin-claim";

    /** pairing code 长度。 */
    int CODE_LENGTH = 8;

    /** pairing code 生存期，单位毫秒。 */
    long CODE_TTL_MILLIS = 60L * 60L * 1000L;

    /** 请求限流窗口，单位毫秒。 */
    long RATE_LIMIT_MILLIS = 10L * 60L * 1000L;

    /** 连续失败锁定时间，单位毫秒。 */
    long LOCKOUT_MILLIS = 60L * 60L * 1000L;

    /** 连续失败阈值。 */
    int MAX_FAILED_ATTEMPTS = 5;

    /** 单平台允许同时待审批的 pairing 请求上限。 */
    int MAX_PENDING_PER_PLATFORM = 3;
}
