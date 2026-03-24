package com.jimuqu.claw.channel.weixin.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述微信文件消息片段。
 */
@Data
@NoArgsConstructor
public class WeixinFileItem implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 文件名。 */
    private String file_name;
}
