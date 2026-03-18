package com.jimuqu.claw.channel.dingtalk;

import com.aliyun.dingtalkoauth2_1_0.Client;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;
import com.jimuqu.claw.config.SolonClawProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 管理钉钉企业内部应用 access_token 的获取与刷新。
 */
public class DingTalkAccessTokenService {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(DingTalkAccessTokenService.class);
    /** 钉钉配置。 */
    private final SolonClawProperties.DingTalk properties;
    /** 当前可用 token。 */
    private volatile AccessToken currentToken;
    /** 定时刷新调度器。 */
    private ScheduledExecutorService scheduler;
    /** 钉钉 OAuth 客户端。 */
    private Client authClient;

    /**
     * 创建 token 服务。
     *
     * @param properties 钉钉配置
     */
    public DingTalkAccessTokenService(SolonClawProperties.DingTalk properties) {
        this.properties = properties;
    }

    /**
     * 启动 token 服务。
     */
    public void start() {
        if (!isConfigured()) {
            log.info("DingTalk access token service disabled because channel config is incomplete.");
            return;
        }

        if (scheduler != null) {
            return;
        }

        try {
            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
            config.protocol = "https";
            config.regionId = "central";
            authClient = new Client(config);
        } catch (Exception exception) {
            log.warn("Failed to initialize DingTalk auth client: {}", exception.getMessage(), exception);
            return;
        }

        refreshAccessToken();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "dingtalk-access-token");
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
        scheduler.scheduleAtFixedRate(this::safeCheck, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 停止 token 服务。
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * 判断当前 token 是否可用于请求。
     *
     * @return 若可用则返回 true
     */
    public boolean isReady() {
        AccessToken token = currentToken;
        return token != null && token.expireTimestamp > System.currentTimeMillis() + 5000L;
    }

    /**
     * 返回当前 access_token。
     *
     * @return access_token
     */
    public String getAccessToken() {
        AccessToken token = currentToken;
        return token == null ? null : token.value;
    }

    /**
     * 判断钉钉配置是否完整。
     *
     * @return 若配置完整则返回 true
     */
    private boolean isConfigured() {
        return properties.isEnabled()
                && notBlank(properties.getClientId())
                && notBlank(properties.getClientSecret());
    }

    /**
     * 安全执行一次定时检查。
     */
    private void safeCheck() {
        try {
            checkAccessToken();
        } catch (Throwable throwable) {
            log.warn("Periodic DingTalk access token refresh failed: {}", throwable.getMessage(), throwable);
        }
    }

    /**
     * 在 token 接近过期时触发刷新。
     */
    private void checkAccessToken() {
        AccessToken token = currentToken;
        if (token == null || token.expireTimestamp - System.currentTimeMillis() <= 10 * 60 * 1000L) {
            refreshAccessToken();
        }
    }

    /**
     * 向钉钉远程接口刷新 access_token。
     */
    private void refreshAccessToken() {
        if (authClient == null) {
            return;
        }

        GetAccessTokenRequest request = new GetAccessTokenRequest()
                .setAppKey(properties.getClientId())
                .setAppSecret(properties.getClientSecret());

        try {
            GetAccessTokenResponse response = authClient.getAccessToken(request);
            if (response == null || response.getBody() == null) {
                log.warn("DingTalk access token refresh returned empty response.");
                return;
            }

            GetAccessTokenResponseBody body = response.getBody();
            if (Objects.isNull(body.getAccessToken()) || Objects.isNull(body.getExpireIn())) {
                log.warn("DingTalk access token refresh returned incomplete body.");
                return;
            }

            AccessToken token = new AccessToken();
            token.value = body.getAccessToken();
            token.expireTimestamp = System.currentTimeMillis() + body.getExpireIn() * 1000L;
            currentToken = token;
            log.info("DingTalk access token refreshed, expireIn={}s", body.getExpireIn());
        } catch (Exception exception) {
            log.warn("Failed to refresh DingTalk access token: {}", exception.getMessage(), exception);
        }
    }

    /**
     * 判断字符串是否非空白。
     *
     * @param value 待检查文本
     * @return 若非空白则返回 true
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 保存 token 文本和值得过期时间。
     */
    private static class AccessToken {
        /** access_token 文本值。 */
        private String value;
        /** access_token 过期时间戳。 */
        private long expireTimestamp;
    }
}
