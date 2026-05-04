package com.jimuqu.solon.claw.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.gateway.service.DefaultGatewayService;
import com.jimuqu.solon.claw.gateway.service.GatewayInjectionAuthService;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** HTTP 网关入口，主要用于内存网关和调试场景下的消息注入。 */
@Controller
public class GatewayController {
    /** 网关服务。 */
    private final DefaultGatewayService gatewayService;

    private final GatewayInjectionAuthService injectionAuthService;

    public GatewayController(
            DefaultGatewayService gatewayService,
            GatewayInjectionAuthService injectionAuthService) {
        this.gatewayService = gatewayService;
        this.injectionAuthService = injectionAuthService;
    }

    /**
     * 接收统一网关消息并转发到主处理链。
     *
     * @param context HTTP 上下文
     * @return 处理结果
     */
    @Mapping(value = "/api/gateway/message", method = MethodType.POST)
    public GatewayReply message(Context context) throws Exception {
        String body = context.body();
        try {
            injectionAuthService.verify(context, body);
        } catch (IllegalStateException e) {
            GatewayReply reply = new GatewayReply();
            reply.setError(true);
            reply.setContent(e.getMessage());
            return reply;
        }
        GatewayMessage message = ONode.deserialize(body, GatewayMessage.class);
        validateMessage(message);
        return gatewayService.handle(message);
    }

    private void validateMessage(GatewayMessage message) {
        if (message == null || message.getPlatform() == null) {
            throw new IllegalArgumentException("Gateway message platform is required");
        }
        switch (message.getPlatform()) {
            case MEMORY:
            case FEISHU:
            case DINGTALK:
            case WECOM:
            case WEIXIN:
                break;
            default:
                throw new IllegalArgumentException("Unsupported gateway platform");
        }
        if (isBlank(message.getChatId()) || isBlank(message.getUserId())) {
            throw new IllegalArgumentException("Gateway message chatId and userId are required");
        }
    }

    private boolean isBlank(String value) {
        return StrUtil.isBlank(value);
    }
}
