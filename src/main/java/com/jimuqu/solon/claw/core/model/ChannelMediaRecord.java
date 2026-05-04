package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 国内渠道媒体缓存索引。 */
@Getter
@Setter
@NoArgsConstructor
public class ChannelMediaRecord {
    private String mediaId;
    private String platform;
    private String chatId;
    private String messageId;
    private String kind;
    private String originalName;
    private String mimeType;
    private String localPath;
    private String remoteId;
    private String status;
    private String error;
    private long sizeBytes;
    private long createdAt;
    private long updatedAt;
    private long expiresAt;
}
