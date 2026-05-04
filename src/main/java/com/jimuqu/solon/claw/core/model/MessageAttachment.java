package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 统一附件模型。 */
@Getter
@Setter
@NoArgsConstructor
public class MessageAttachment {
    /** 附件类型：image / file / video / voice。 */
    private String kind;

    /** 附件缓存后的本地绝对路径。 */
    private String localPath;

    /** 原始文件名。 */
    private String originalName;

    /** MIME 类型。 */
    private String mimeType;

    /** 是否来自引用消息。 */
    private boolean fromQuote;

    /** 平台原生提供的转写文本。 */
    private String transcribedText;
}
