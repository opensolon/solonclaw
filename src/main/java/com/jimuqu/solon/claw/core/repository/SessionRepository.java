package com.jimuqu.solon.claw.core.repository;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import java.util.List;

/** 会话仓储接口。 */
public interface SessionRepository {
    /** 查询来源键当前绑定的会话。 */
    SessionRecord getBoundSession(String sourceKey) throws Exception;

    /** 为来源键创建并绑定新会话。 */
    SessionRecord bindNewSession(String sourceKey) throws Exception;

    /** 重新绑定来源键到指定会话。 */
    void bindSource(String sourceKey, String sessionId) throws Exception;

    /** 克隆会话并生成新分支。 */
    SessionRecord cloneSession(String sourceKey, String sourceSessionId, String branchName)
            throws Exception;

    /** 通过会话 ID 查询会话。 */
    SessionRecord findById(String sessionId) throws Exception;

    /** 通过来源键和分支名查询会话。 */
    SessionRecord findBySourceAndBranch(String sourceKey, String branchName) throws Exception;

    /** 保存会话。 */
    void save(SessionRecord sessionRecord) throws Exception;

    /** 全文检索会话。 */
    List<SessionRecord> search(String keyword, int limit) throws Exception;

    /** 按更新时间列出最近会话。 */
    List<SessionRecord> listRecent(int limit) throws Exception;

    /** 按更新时间分页列出最近会话。 */
    List<SessionRecord> listRecent(int limit, int offset) throws Exception;

    /** 返回会话总数。 */
    int countAll() throws Exception;

    /** 删除指定会话。 */
    void delete(String sessionId) throws Exception;

    /** 更新会话模型覆盖配置。 */
    void setModelOverride(String sessionId, String modelOverride) throws Exception;

    /** 更新当前会话激活 Agent。 */
    void setActiveAgentName(String sessionId, String agentName) throws Exception;

    /** 清除所有使用指定 Agent 的会话激活状态。 */
    void clearActiveAgentName(String agentName) throws Exception;
}
