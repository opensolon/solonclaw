package com.jimuqu.claw.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述调试页发起聊天请求时的参数。
 */
@Data
@NoArgsConstructor
public class DebugChatRequest implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 调试会话标识。 */
    private String sessionId;
    /** 用户输入文本。 */
    private String message;
}
