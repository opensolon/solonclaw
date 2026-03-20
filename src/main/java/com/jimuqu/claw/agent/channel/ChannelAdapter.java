package com.jimuqu.claw.agent.channel;

import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;

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
     * 当前渠道是否支持将运行中的增量内容作为“进度更新”透传到外部。
     *
     * @return 若支持进度更新则返回 true
     */
    default boolean supportsProgressUpdates() {
        return false;
    }

    /**
     * 发送一条出站消息。
     *
     * @param outboundEnvelope 出站消息
     */
    DeliveryResult send(OutboundEnvelope outboundEnvelope);
}

