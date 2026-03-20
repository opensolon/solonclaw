package com.jimuqu.claw.agent.model.envelope;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述一条消息中保存到本地的附件引用信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentRef implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 附件类别，例如图片、音频或文件。 */
    private String type;
    /** 附件展示名称。 */
    private String name;
    /** 附件或附件元数据在本地的保存路径。 */
    private String path;
}
