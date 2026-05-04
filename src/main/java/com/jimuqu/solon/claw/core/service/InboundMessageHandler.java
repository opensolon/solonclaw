package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.GatewayMessage;

/** 渠道入站消息回调接口。 */
public interface InboundMessageHandler {
    /** 处理单条渠道入站消息。 */
    void handle(GatewayMessage message) throws Exception;
}
