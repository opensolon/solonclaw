package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.MemorySnapshot;

/** 长期记忆服务接口。 */
public interface MemoryService {
    /** 读取冻结快照。 */
    MemorySnapshot loadSnapshot() throws Exception;

    /** 读取目标内容。 */
    String read(String target) throws Exception;

    /** 添加条目。 */
    String add(String target, String content) throws Exception;

    /** 替换条目。 */
    String replace(String target, String oldText, String newContent) throws Exception;

    /** 删除条目。 */
    String remove(String target, String matchText) throws Exception;
}
