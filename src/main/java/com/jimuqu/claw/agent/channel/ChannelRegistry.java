package com.jimuqu.claw.agent.channel;

import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.runtime.support.DeliveryResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有渠道适配器并负责统一转发出站消息。
 */
public class ChannelRegistry {
    /** 渠道类型到适配器实例的映射表。 */
    private final Map<ChannelType, ChannelAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * 注册一个渠道适配器。
     *
     * @param channelAdapter 渠道适配器
     */
    public void register(ChannelAdapter channelAdapter) {
        adapters.put(channelAdapter.channelType(), channelAdapter);
    }

    /**
     * 根据渠道类型获取适配器。
     *
     * @param channelType 渠道类型
     * @return 渠道适配器
     */
    public ChannelAdapter get(ChannelType channelType) {
        return adapters.get(channelType);
    }

    /**
     * 根据出站消息中的回复目标选择对应渠道发送。
     *
     * @param outboundEnvelope 出站消息
     */
    public DeliveryResult send(OutboundEnvelope outboundEnvelope) {
        DeliveryResult result = new DeliveryResult();
        if (outboundEnvelope == null || outboundEnvelope.getReplyTarget() == null) {
            result.setDelivered(false);
            result.setMessage("missing reply target");
            return result;
        }

        ChannelAdapter adapter = adapters.get(outboundEnvelope.getReplyTarget().getChannelType());
        if (adapter != null) {
            return adapter.send(outboundEnvelope);
        }
        result.setDelivered(false);
        result.setChannelType(outboundEnvelope.getReplyTarget().getChannelType());
        result.setMessage("channel adapter not found");
        return result;
    }
}

