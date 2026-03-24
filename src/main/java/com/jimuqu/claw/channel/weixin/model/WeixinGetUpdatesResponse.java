package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述微信 getUpdates 响应。
 */
@Data
@NoArgsConstructor
public class WeixinGetUpdatesResponse implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 返回码；0 表示成功。 */
    private Integer ret;
    /** 错误码。 */
    private Integer errcode;
    /** 错误消息。 */
    private String errmsg;
    /** 返回的消息列表。 */
    private List<WeixinMessage> msgs = new ArrayList<WeixinMessage>();
    /** 下次请求要回传的游标。 */
    private String get_updates_buf;
    /** 服务端建议的下次长轮询超时。 */
    private Integer longpolling_timeout_ms;
}
