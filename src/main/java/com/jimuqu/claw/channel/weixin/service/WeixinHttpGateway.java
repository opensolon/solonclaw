package com.jimuqu.claw.channel.weixin.service;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.jimuqu.claw.channel.weixin.model.WeixinGetUpdatesResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrCodeResponse;
import com.jimuqu.claw.channel.weixin.model.WeixinQrStatusResponse;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 基于 HTTP JSON 协议调用微信 Bot API。
 */
public class WeixinHttpGateway implements WeixinApiGateway {
    /** 渠道版本标识。 */
    private static final String CHANNEL_VERSION = "solonclaw-weixin-java";

    @Override
    public WeixinGetUpdatesResponse getUpdates(String baseUrl, String token, String cursor, int timeoutMs) {
        try {
            HttpResponse response = buildPost(baseUrl, "ilink/bot/getupdates", token, timeoutMs)
                    .body(
                            JSONUtil.createObj()
                                    .set("get_updates_buf", StrUtil.blankToDefault(cursor, ""))
                                    .set("base_info", baseInfo())
                                    .toString()
                    )
                    .execute();
            return parseOkResponse(response, WeixinGetUpdatesResponse.class, "getUpdates");
        } catch (IORuntimeException ioRuntimeException) {
            Throwable root = ExceptionUtil.getRootCause(ioRuntimeException);
            if (root instanceof SocketTimeoutException) {
                WeixinGetUpdatesResponse timeoutResponse = new WeixinGetUpdatesResponse();
                timeoutResponse.setRet(0);
                timeoutResponse.setGet_updates_buf(cursor);
                return timeoutResponse;
            }
            throw ioRuntimeException;
        }
    }

    @Override
    public void sendTextMessage(String baseUrl, String token, String toUserId, String contextToken, String text) {
        HttpResponse response = buildPost(baseUrl, "ilink/bot/sendmessage", token, 15000)
                .body(
                        JSONUtil.createObj()
                                .set(
                                        "msg",
                                        JSONUtil.createObj()
                                                .set("from_user_id", "")
                                                .set("to_user_id", toUserId)
                                                .set("client_id", IdUtil.fastSimpleUUID())
                                                .set("message_type", 2)
                                                .set("message_state", 2)
                                                .set("context_token", contextToken)
                                                .set(
                                                        "item_list",
                                                        JSONUtil.createArray()
                                                                .put(
                                                                        JSONUtil.createObj()
                                                                                .set("type", 1)
                                                                                .set(
                                                                                        "text_item",
                                                                                        JSONUtil.createObj().set("text", StrUtil.blankToDefault(text, ""))
                                                                                )
                                                                )
                                                )
                                )
                                .set("base_info", baseInfo())
                                .toString()
                )
                .execute();
        parseOkResponse(response, Object.class, "sendMessage");
    }

    @Override
    public WeixinQrCodeResponse getBotQrCode(String baseUrl, String botType) {
        HttpResponse response = HttpRequest.get(resolveUrl(baseUrl, "ilink/bot/get_bot_qrcode"))
                .form("bot_type", StrUtil.blankToDefault(botType, "3"))
                .timeout(15000)
                .execute();
        return parseOkResponse(response, WeixinQrCodeResponse.class, "getBotQrCode");
    }

    @Override
    public WeixinQrStatusResponse getQrCodeStatus(String baseUrl, String qrcode, int timeoutMs) {
        try {
            HttpResponse response = HttpRequest.get(resolveUrl(baseUrl, "ilink/bot/get_qrcode_status"))
                    .header("iLink-App-ClientVersion", "1")
                    .form("qrcode", qrcode)
                    .timeout(timeoutMs)
                    .execute();
            return parseOkResponse(response, WeixinQrStatusResponse.class, "getQrCodeStatus");
        } catch (IORuntimeException ioRuntimeException) {
            Throwable root = ExceptionUtil.getRootCause(ioRuntimeException);
            if (root instanceof SocketTimeoutException) {
                WeixinQrStatusResponse timeoutResponse = new WeixinQrStatusResponse();
                timeoutResponse.setStatus("wait");
                return timeoutResponse;
            }
            throw ioRuntimeException;
        }
    }

    private HttpRequest buildPost(String baseUrl, String path, String token, int timeoutMs) {
        HttpRequest request = HttpRequest.post(resolveUrl(baseUrl, path))
                .header("Content-Type", "application/json")
                .header("AuthorizationType", "ilink_bot_token")
                .header("X-WECHAT-UIN", randomWechatUin())
                .timeout(timeoutMs);
        if (StrUtil.isNotBlank(token)) {
            request.header("Authorization", "Bearer " + token.trim());
        }
        return request;
    }

    private <T> T parseOkResponse(HttpResponse response, Class<T> type, String action) {
        if (response == null) {
            throw new IllegalStateException(action + " response is null");
        }
        int status = response.getStatus();
        String body = response.body();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(action + " failed: " + status + ", body=" + body);
        }
        if (type == Object.class) {
            return type.cast(new Object());
        }
        return JSONUtil.toBean(body, type);
    }

    private String resolveUrl(String baseUrl, String path) {
        String normalizedBase = StrUtil.blankToDefault(baseUrl, "").trim();
        if (!normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase + "/";
        }
        return normalizedBase + path;
    }

    private String randomWechatUin() {
        String raw = String.valueOf(RandomUtil.randomLong(1L, 4294967295L));
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private cn.hutool.json.JSONObject baseInfo() {
        return JSONUtil.createObj().set("channel_version", CHANNEL_VERSION);
    }
}
