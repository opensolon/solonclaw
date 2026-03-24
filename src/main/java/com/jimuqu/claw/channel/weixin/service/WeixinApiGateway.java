package com.jimuqu.claw.channel.weixin.service;

import com.jimuqu.claw.channel.weixin.model.WeixinGetUpdatesResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrCodeResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStatusResponse;

/**
 * 抽象微信 Bot API 调用，便于测试时替换远程网关。
 */
public interface WeixinApiGateway {
    /**
     * 长轮询拉取微信消息。
     *
     * @param baseUrl 微信基础地址
     * @param token Bot token
     * @param cursor 上次返回的游标
     * @param timeoutMs 长轮询超时
     * @return 拉取结果
     */
    WeixinGetUpdatesResponse getUpdates(String baseUrl, String token, String cursor, int timeoutMs);

    /**
     * 发送一条纯文本消息。
     *
     * @param baseUrl 微信基础地址
     * @param token Bot token
     * @param toUserId 目标用户
     * @param contextToken 上下文令牌
     * @param text 文本内容
     */
    void sendTextMessage(String baseUrl, String token, String toUserId, String contextToken, String text);

    /**
     * 获取二维码登录入口。
     *
     * @param baseUrl 微信基础地址
     * @param botType botType
     * @return 二维码信息
     */
    WeixinQrCodeResponse getBotQrCode(String baseUrl, String botType);

    /**
     * 轮询二维码状态。
     *
     * @param baseUrl 微信基础地址
     * @param qrcode 二维码标识
     * @param timeoutMs 请求超时
     * @return 登录状态
     */
    WeixinQrStatusResponse getQrCodeStatus(String baseUrl, String qrcode, int timeoutMs);
}
