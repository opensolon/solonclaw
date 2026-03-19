package com.jimuqu.claw.channel.feishu.gateway;

import cn.hutool.core.util.StrUtil;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.enums.MsgTypeEnum;
import com.lark.oapi.service.im.v1.enums.ReceiveIdTypeEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.PatchMessageReq;
import com.lark.oapi.service.im.v1.model.PatchMessageReqBody;
import com.lark.oapi.service.im.v1.model.PatchMessageResp;
import com.jimuqu.claw.config.props.FeishuProperties;

/**
 * 基于飞书 Java SDK 的消息网关实现。
 */
public class FeishuSdkMessageGateway implements FeishuMessageGateway {
    /** 飞书 OpenAPI 客户端。 */
    private final Client client;

    /**
     * 创建基于 SDK 的消息网关。
     *
     * @param properties 飞书配置
     */
    public FeishuSdkMessageGateway(FeishuProperties properties) {
        Client.Builder builder = Client.newBuilder(properties.getAppId(), properties.getAppSecret());
        if (StrUtil.isNotBlank(properties.getBaseDomain())) {
            builder.openBaseUrl(properties.getBaseDomain().trim());
        }
        this.client = builder.build();
    }

    /**
     * 使用显式客户端创建消息网关。
     *
     * @param client 飞书客户端
     */
    FeishuSdkMessageGateway(Client client) {
        this.client = client;
    }

    @Override
    public String createCardMessage(String chatId, String cardContent) throws Exception {
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType(ReceiveIdTypeEnum.CHAT_ID.getValue())
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(chatId)
                        .msgType(MsgTypeEnum.MSG_TYPE_INTERACTIVE.getValue())
                        .content(cardContent)
                        .build())
                .build();

        CreateMessageResp resp = client.im().message().create(req);
        if (resp.getCode() != 0) {
            throw new IllegalStateException("Feishu create message failed, code=" + resp.getCode() + ", msg=" + resp.getMsg());
        }

        return resp.getData() == null ? null : resp.getData().getMessageId();
    }

    @Override
    public void patchCardMessage(String messageId, String cardContent) throws Exception {
        PatchMessageReq req = PatchMessageReq.newBuilder()
                .messageId(messageId)
                .patchMessageReqBody(PatchMessageReqBody.newBuilder()
                        .content(cardContent)
                        .build())
                .build();

        PatchMessageResp resp = client.im().message().patch(req);
        if (resp.getCode() != 0) {
            throw new IllegalStateException("Feishu patch message failed, code=" + resp.getCode() + ", msg=" + resp.getMsg());
        }
    }
}


