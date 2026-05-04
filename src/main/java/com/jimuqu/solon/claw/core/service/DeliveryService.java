package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.ChannelStatus;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import java.util.List;

/** 渠道投递服务接口。 */
public interface DeliveryService {
    /** 向目标平台投递一条消息。 */
    void deliver(DeliveryRequest request) throws Exception;

    /** 读取全部渠道状态。 */
    List<ChannelStatus> statuses();
}
