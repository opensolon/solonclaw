package com.jimuqu.solon.claw.support.constants;

/** 网关层通用行为常量。 */
public interface GatewayBehaviorConstants {
    /** 私聊策略：开放。 */
    String DM_POLICY_OPEN = "open";

    /** 私聊策略：允许名单。 */
    String DM_POLICY_ALLOWLIST = "allowlist";

    /** 私聊策略：禁用。 */
    String DM_POLICY_DISABLED = "disabled";

    /** 私聊策略：走 pairing。 */
    String DM_POLICY_PAIRING = "pairing";

    /** 群聊策略：开放。 */
    String GROUP_POLICY_OPEN = "open";

    /** 群聊策略：允许名单。 */
    String GROUP_POLICY_ALLOWLIST = "allowlist";

    /** 群聊策略：禁用。 */
    String GROUP_POLICY_DISABLED = "disabled";

    /** 未授权私聊用户进入 pairing 流程。 */
    String UNAUTHORIZED_DM_BEHAVIOR_PAIR = "pair";

    /** 未授权私聊用户直接忽略。 */
    String UNAUTHORIZED_DM_BEHAVIOR_IGNORE = "ignore";

    /** 私聊会话类型。 */
    String CHAT_TYPE_DM = "dm";

    /** 群聊会话类型。 */
    String CHAT_TYPE_GROUP = "group";

    /** 允许名单中的通配标记。 */
    String ALLOW_ALL_MARKER = "*";

    /** 默认空详情占位值。 */
    String NONE = "none";
}
