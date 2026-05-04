package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件快照元数据记录。 */
@Getter
@Setter
@NoArgsConstructor
public class CheckpointRecord {
    /** checkpoint 唯一标识。 */
    private String checkpointId;

    /** 来源键。 */
    private String sourceKey;

    /** 会话 ID。 */
    private String sessionId;

    /** 快照目录路径。 */
    private String checkpointDir;

    /** 快照清单文件路径。 */
    private String manifestPath;

    /** 创建时间。 */
    private long createdAt;

    /** 恢复时间。 */
    private long restoredAt;
}
