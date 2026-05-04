package com.jimuqu.solon.claw.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 统一网关回复模型。 */
@Getter
@Setter
@NoArgsConstructor
public class GatewayReply {
    /** 关联会话 ID。 */
    private String sessionId;

    /** 关联分支名。 */
    private String branchName;

    /** 回复文本。 */
    private String content;

    /** 渠道定制扩展参数。 */
    private Map<String, Object> channelExtras = new LinkedHashMap<String, Object>();

    /** 最终回复运行态元数据，不参与命令/错误回复。 */
    private Map<String, Object> runtimeMetadata = new LinkedHashMap<String, Object>();

    /** 是否由命令处理链生成。 */
    private boolean commandHandled;

    /** 是否为错误回复。 */
    private boolean error;

    /** 构造成功回复。 */
    public static GatewayReply ok(String content) {
        GatewayReply reply = new GatewayReply();
        reply.setContent(content);
        return reply;
    }

    /** 构造错误回复。 */
    public static GatewayReply error(String content) {
        GatewayReply reply = new GatewayReply();
        reply.setContent(content);
        reply.setError(true);
        return reply;
    }
}
