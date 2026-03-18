package com.jimuqu.claw.agent.channel;

import com.jimuqu.claw.agent.model.ChannelType;
import com.jimuqu.claw.agent.model.OutboundEnvelope;

/**
 * 抽象统一的消息渠道适配器接口。
 */
public interface ChannelAdapter {
    /**
     * 返回当前适配器负责的渠道类型。
     *
     * @return 渠道类型
     */
    ChannelType channelType();

    /**
     * 发送一条出站消息。
     *
     * @param outboundEnvelope 出站消息
     */
    void send(OutboundEnvelope outboundEnvelope);
}
