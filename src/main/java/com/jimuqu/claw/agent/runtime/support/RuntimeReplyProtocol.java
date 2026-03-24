package com.jimuqu.claw.agent.runtime.support;

import cn.hutool.core.util.StrUtil;

/**
 * 运行时回复协议常量与解析工具。
 */
public final class RuntimeReplyProtocol {
    public static final String NO_REPLY = "NO_REPLY";
    public static final String FINAL_REPLY_ONCE_PREFIX = "FINAL_REPLY_ONCE:";

    private RuntimeReplyProtocol() {
    }

    public static String normalizeVisibleResponse(String response) {
        String trimmed = StrUtil.trim(response);
        if (StrUtil.startWithIgnoreCase(trimmed, FINAL_REPLY_ONCE_PREFIX)) {
            return StrUtil.trim(trimmed.substring(FINAL_REPLY_ONCE_PREFIX.length()));
        }
        return response;
    }

    public static boolean isNoReply(String response) {
        return StrUtil.equalsIgnoreCase(StrUtil.trim(response), NO_REPLY);
    }

    public static boolean isFinalReplyOnce(String response) {
        return StrUtil.startWithIgnoreCase(StrUtil.trim(response), FINAL_REPLY_ONCE_PREFIX);
    }
}
