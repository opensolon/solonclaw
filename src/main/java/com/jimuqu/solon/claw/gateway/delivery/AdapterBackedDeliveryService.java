package com.jimuqu.solon.claw.gateway.delivery;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.ChannelStatus;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.HomeChannelRecord;
import com.jimuqu.solon.claw.core.repository.GatewayPolicyRepository;
import com.jimuqu.solon.claw.core.service.ChannelAdapter;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/** 基于渠道适配器集合实现的投递服务。 */
@RequiredArgsConstructor
public class AdapterBackedDeliveryService implements DeliveryService {
    /** 平台到适配器的映射。 */
    private final Map<PlatformType, ChannelAdapter> adapters;

    /** 网关授权策略仓储，用于解析 home channel。 */
    private final GatewayPolicyRepository gatewayPolicyRepository;

    /** 发送消息；若请求未指定 chatId，则回退到平台 home channel。 */
    @Override
    public void deliver(DeliveryRequest request) throws Exception {
        if (StrUtil.isBlank(request.getChatId())) {
            HomeChannelRecord home = gatewayPolicyRepository.getHomeChannel(request.getPlatform());
            if (home == null) {
                throw new IllegalStateException(
                        "No home channel configured for platform: " + request.getPlatform());
            }
            request.setChatId(home.getChatId());
        }

        ChannelAdapter adapter = adapters.get(request.getPlatform());
        if (adapter == null) {
            throw new IllegalStateException("No adapter for platform: " + request.getPlatform());
        }

        adapter.send(request);
    }

    /** 汇总全部渠道状态。 */
    @Override
    public List<ChannelStatus> statuses() {
        List<ChannelStatus> items = new ArrayList<ChannelStatus>();
        for (ChannelAdapter adapter : adapters.values()) {
            items.add(adapter.statusSnapshot());
        }
        return items;
    }
}
