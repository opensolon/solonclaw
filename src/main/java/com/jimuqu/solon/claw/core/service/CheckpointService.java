package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.CheckpointRecord;
import java.io.File;
import java.util.List;
import java.util.Map;

/** 文件快照服务接口。 */
public interface CheckpointService {
    /** 为结构化写操作创建快照。 */
    CheckpointRecord createCheckpoint(String sourceKey, String sessionId, List<File> files)
            throws Exception;

    /** 回滚来源键最近一次快照。 */
    CheckpointRecord rollbackLatest(String sourceKey) throws Exception;

    /** 回滚指定快照。 */
    CheckpointRecord rollback(String checkpointId) throws Exception;

    /** 判断来源键最近是否发生过结构化文件修改。 */
    boolean hasRecentCheckpoint(String sourceKey, long sinceEpochMillis) throws Exception;

    /** 列出来源键最近的 checkpoints。 */
    List<CheckpointRecord> listRecent(String sourceKey, int limit) throws Exception;

    /** 预览指定 checkpoint 的文件清单。 */
    Map<String, Object> preview(String checkpointId) throws Exception;
}
