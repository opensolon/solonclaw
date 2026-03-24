package com.jimuqu.claw.channel.weixin.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.channel.weixin.adapter.WeixinChannelAdapter;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;
import com.jimuqu.claw.channel.weixin.model.WeixinQrCodeResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStartResult;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStatusResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrWaitResult;
import com.jimuqu.claw.config.props.WeixinProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理微信扫码登录流程。
 */
public class WeixinLoginService {
    /** 二维码会话存活时间。 */
    private static final long ACTIVE_LOGIN_TTL_MS = 5 * 60 * 1000L;
    /** 二维码状态轮询超时。 */
    private static final int QR_STATUS_TIMEOUT_MS = 35000;

    /** 微信网关。 */
    private final WeixinApiGateway apiGateway;
    /** 账号存储服务。 */
    private final WeixinAccountStoreService accountStoreService;
    /** 微信配置。 */
    private final WeixinProperties properties;
    /** 微信渠道适配器；用于登录后热加载新账号。 */
    private final WeixinChannelAdapter weixinChannelAdapter;
    /** 活跃登录会话。 */
    private final Map<String, ActiveLoginSession> activeSessions = new ConcurrentHashMap<String, ActiveLoginSession>();

    /**
     * 创建微信登录服务。
     *
     * @param apiGateway 微信网关
     * @param accountStoreService 账号存储服务
     * @param properties 微信配置
     */
    public WeixinLoginService(
            WeixinApiGateway apiGateway,
            WeixinAccountStoreService accountStoreService,
            WeixinProperties properties
    ) {
        this(apiGateway, accountStoreService, properties, null);
    }

    /**
     * 创建微信登录服务。
     *
     * @param apiGateway 微信网关
     * @param accountStoreService 账号存储服务
     * @param properties 微信配置
     * @param weixinChannelAdapter 微信渠道适配器
     */
    public WeixinLoginService(
            WeixinApiGateway apiGateway,
            WeixinAccountStoreService accountStoreService,
            WeixinProperties properties,
            WeixinChannelAdapter weixinChannelAdapter
    ) {
        this.apiGateway = apiGateway;
        this.accountStoreService = accountStoreService;
        this.properties = properties;
        this.weixinChannelAdapter = weixinChannelAdapter;
    }

    /**
     * 启动一次微信扫码登录。
     *
     * @param preferredSessionKey 调用方指定的会话标识；为空时自动生成
     * @return 二维码信息
     */
    public WeixinQrStartResult startLogin(String preferredSessionKey) {
        String sessionKey = StrUtil.blankToDefault(StrUtil.trim(preferredSessionKey), IdUtil.fastSimpleUUID());
        ActiveLoginSession existing = activeSessions.get(sessionKey);
        if (isFresh(existing)) {
            WeixinQrStartResult result = new WeixinQrStartResult();
            result.setSessionKey(sessionKey);
            result.setQrCodeUrl(existing.getQrCodeUrl());
            result.setMessage("二维码已就绪，请使用微信扫描。");
            return result;
        }

        WeixinQrCodeResponse response = apiGateway.getBotQrCode(properties.getBaseUrl(), properties.getBotType());
        if (response == null || StrUtil.isBlank(response.getQrcode_img_content()) || StrUtil.isBlank(response.getQrcode())) {
            throw new IllegalStateException("获取微信登录二维码失败");
        }

        ActiveLoginSession session = new ActiveLoginSession();
        session.setSessionKey(sessionKey);
        session.setQrCode(response.getQrcode());
        session.setQrCodeUrl(response.getQrcode_img_content());
        session.setStartedAt(System.currentTimeMillis());
        activeSessions.put(sessionKey, session);

        WeixinQrStartResult result = new WeixinQrStartResult();
        result.setSessionKey(sessionKey);
        result.setQrCodeUrl(response.getQrcode_img_content());
        result.setMessage("使用微信扫描二维码以完成连接。");
        return result;
    }

    /**
     * 等待扫码登录完成。
     *
     * @param sessionKey 登录会话标识
     * @return 登录结果
     */
    public WeixinQrWaitResult waitForLogin(String sessionKey) {
        ActiveLoginSession session = activeSessions.get(sessionKey);
        if (!isFresh(session)) {
            activeSessions.remove(sessionKey);
            WeixinQrWaitResult expired = new WeixinQrWaitResult();
            expired.setConnected(false);
            expired.setMessage("二维码已过期，请重新开始登录。");
            return expired;
        }

        long deadline = System.currentTimeMillis() + Math.max(1000, properties.getLoginTimeoutMs());
        while (System.currentTimeMillis() < deadline) {
            WeixinQrStatusResponse status = apiGateway.getQrCodeStatus(properties.getBaseUrl(), session.getQrCode(), QR_STATUS_TIMEOUT_MS);
            if (status == null || StrUtil.isBlank(status.getStatus()) || "wait".equalsIgnoreCase(status.getStatus()) || "scaned".equalsIgnoreCase(status.getStatus())) {
                sleepQuietly(1000L);
                continue;
            }
            if ("expired".equalsIgnoreCase(status.getStatus())) {
                activeSessions.remove(sessionKey);
                WeixinQrWaitResult expired = new WeixinQrWaitResult();
                expired.setConnected(false);
                expired.setMessage("二维码已过期，请重新开始登录。");
                return expired;
            }
            if ("confirmed".equalsIgnoreCase(status.getStatus())) {
                String normalizedAccountId = accountStoreService.normalizeAccountId(status.getIlink_bot_id());
                WeixinAccount account = new WeixinAccount();
                account.setAccountId(normalizedAccountId);
                account.setRemoteAccountId(status.getIlink_bot_id());
                account.setBaseUrl(StrUtil.blankToDefault(status.getBaseurl(), properties.getBaseUrl()));
                account.setToken(status.getBot_token());
                account.setUserId(status.getIlink_user_id());
                accountStoreService.saveAccount(account);
                if (weixinChannelAdapter != null) {
                    weixinChannelAdapter.reloadAccounts();
                }
                activeSessions.remove(sessionKey);

                WeixinQrWaitResult result = new WeixinQrWaitResult();
                result.setConnected(true);
                result.setAccountId(normalizedAccountId);
                result.setRemoteAccountId(status.getIlink_bot_id());
                result.setUserId(status.getIlink_user_id());
                result.setMessage("微信连接成功。");
                return result;
            }
            sleepQuietly(1000L);
        }

        WeixinQrWaitResult timeout = new WeixinQrWaitResult();
        timeout.setConnected(false);
        timeout.setMessage("登录超时，请重试。");
        return timeout;
    }

    private boolean isFresh(ActiveLoginSession session) {
        return session != null && System.currentTimeMillis() - session.getStartedAt() < ACTIVE_LOGIN_TTL_MS;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待扫码登录被中断", interruptedException);
        }
    }
}
