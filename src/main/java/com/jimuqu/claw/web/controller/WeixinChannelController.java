package com.jimuqu.claw.web.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStartResult;
import com.jimuqu.claw.channel.weixin.model.WeixinQrWaitResult;
import com.jimuqu.claw.channel.weixin.service.WeixinAccountStoreService;
import com.jimuqu.claw.channel.weixin.service.WeixinLoginService;
import com.jimuqu.claw.web.dto.WeixinAccountSummary;
import com.jimuqu.claw.web.dto.WeixinLoginStartRequest;
import com.jimuqu.claw.web.dto.WeixinLoginWaitRequest;
import org.noear.solon.annotation.Body;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Post;
import org.noear.solon.core.handle.Context;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 提供微信渠道管理接口。
 */
@Controller
@Mapping("/api/admin/channels/weixin")
public class WeixinChannelController {
    /** 微信扫码登录服务。 */
    private final WeixinLoginService weixinLoginService;
    /** 微信账号存储服务。 */
    private final WeixinAccountStoreService weixinAccountStoreService;

    /**
     * 创建微信渠道控制器。
     *
     * @param weixinLoginService 微信扫码登录服务
     * @param weixinAccountStoreService 微信账号存储服务
     */
    public WeixinChannelController(
            WeixinLoginService weixinLoginService,
            WeixinAccountStoreService weixinAccountStoreService
    ) {
        this.weixinLoginService = weixinLoginService;
        this.weixinAccountStoreService = weixinAccountStoreService;
    }

    /**
     * 返回当前已登录的微信账号列表。
     *
     * @return 账号摘要列表
     */
    @Get
    @Mapping("/accounts")
    public List<WeixinAccountSummary> listAccounts() {
        List<WeixinAccountSummary> summaries = new ArrayList<WeixinAccountSummary>();
        for (WeixinAccount account : weixinAccountStoreService.listAccounts()) {
            WeixinAccountSummary summary = new WeixinAccountSummary();
            summary.setAccountId(account.getAccountId());
            summary.setRemoteAccountId(account.getRemoteAccountId());
            summary.setUserId(account.getUserId());
            summary.setBaseUrl(account.getBaseUrl());
            summary.setSavedAt(account.getSavedAt());
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 启动一次微信扫码登录。
     *
     * @param request 登录请求
     * @return 二维码结果
     */
    @Post
    @Mapping("/login/start")
    public WeixinQrStartResult startLogin(@Body WeixinLoginStartRequest request) {
        return weixinLoginService.startLogin(request == null ? null : request.getSessionKey());
    }

    /**
     * 等待指定微信扫码登录完成。
     *
     * @param request 等待请求
     * @return 登录完成结果
     */
    @Post
    @Mapping("/login/wait")
    public WeixinQrWaitResult waitLogin(@Body WeixinLoginWaitRequest request) {
        String sessionKey = request == null ? null : StrUtil.trim(request.getSessionKey());
        if (StrUtil.isBlank(sessionKey)) {
            throw new IllegalArgumentException("sessionKey 不能为空");
        }
        return weixinLoginService.waitForLogin(sessionKey);
    }

    /**
     * 将任意微信登录链接渲染为二维码图片。
     *
     * @param content 需要编码进二维码的文本内容
     * @param ctx 当前请求上下文
     */
    @Get
    @Mapping("/login/qr-image")
    public void renderQrImage(String content, Context ctx) {
        String normalizedContent = StrUtil.trim(content);
        if (StrUtil.isBlank(normalizedContent)) {
            throw new IllegalArgumentException("content 不能为空");
        }

        ctx.contentType("image/png");
        ctx.headerSet("Cache-Control", "no-store, no-cache, must-revalidate");
        ctx.output(generateQrPng(normalizedContent));
    }

    public byte[] generateQrPng(String content) {
        QrConfig config = new QrConfig(360, 360);
        config.setMargin(1);
        config.setForeColor(new Color(32, 36, 39));
        config.setBackColor(Color.WHITE);
        return QrCodeUtil.generatePng(content, config);
    }
}
