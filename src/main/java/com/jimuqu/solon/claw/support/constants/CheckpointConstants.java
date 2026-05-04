package com.jimuqu.solon.claw.support.constants;

/** 文件快照与回滚常量。 */
public interface CheckpointConstants {
    /** checkpoint 根目录名。 */
    String CHECKPOINT_DIR_NAME = "checkpoints";

    /** 快照清单文件名。 */
    String MANIFEST_FILE_NAME = "manifest.json";

    /** 默认每个来源键保留的 checkpoint 数。 */
    int DEFAULT_MAX_CHECKPOINTS_PER_SOURCE = 20;
}
