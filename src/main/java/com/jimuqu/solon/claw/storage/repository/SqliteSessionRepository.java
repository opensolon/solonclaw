package com.jimuqu.solon.claw.storage.repository;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.MessageSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.tool.ToolCall;

/** SQLite 会话仓储实现。 */
@RequiredArgsConstructor
public class SqliteSessionRepository implements SessionRepository {
    /** 数据库访问对象。 */
    private final SqliteDatabase database;

    @Override
    public SessionRecord getBoundSession(String sourceKey) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select session_id from bindings where source_key = ?");
            statement.setString(1, sourceKey);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return findById(resultSet.getString(1));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return null;
    }

    @Override
    public SessionRecord bindNewSession(String sourceKey) throws Exception {
        long now = System.currentTimeMillis();
        SessionRecord record = new SessionRecord();
        record.setSessionId(IdSupport.newId());
        record.setSourceKey(sourceKey);
        record.setBranchName("main");
        record.setNdjson("");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        save(record);
        bindSource(sourceKey, record.getSessionId());
        return record;
    }

    @Override
    public void bindSource(String sourceKey, String sessionId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into bindings (source_key, session_id) values (?, ?)");
            statement.setString(1, sourceKey);
            statement.setString(2, sessionId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public SessionRecord cloneSession(String sourceKey, String sourceSessionId, String branchName)
            throws Exception {
        SessionRecord source = findById(sourceSessionId);
        if (source == null) {
            return bindNewSession(sourceKey);
        }

        long now = System.currentTimeMillis();
        SessionRecord clone = new SessionRecord();
        clone.setSessionId(IdSupport.newId());
        clone.setSourceKey(sourceKey);
        clone.setParentSessionId(source.getSessionId());
        clone.setBranchName(branchName);
        clone.setModelOverride(source.getModelOverride());
        clone.setActiveAgentName(source.getActiveAgentName());
        clone.setNdjson(source.getNdjson());
        clone.setTitle(source.getTitle());
        clone.setCompressedSummary(source.getCompressedSummary());
        clone.setSystemPromptSnapshot(source.getSystemPromptSnapshot());
        clone.setAgentSnapshotJson(source.getAgentSnapshotJson());
        clone.setLastCompressionAt(source.getLastCompressionAt());
        clone.setLastCompressionInputTokens(source.getLastCompressionInputTokens());
        clone.setCompressionFailureCount(source.getCompressionFailureCount());
        clone.setLastCompressionFailedAt(source.getLastCompressionFailedAt());
        clone.setLastResolvedProvider(source.getLastResolvedProvider());
        clone.setLastResolvedModel(source.getLastResolvedModel());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);
        save(clone);
        bindSource(sourceKey, clone.getSessionId());
        return clone;
    }

    @Override
    public SessionRecord findById(String sessionId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select session_id, source_key, branch_name, parent_session_id, model_override, active_agent_name, ndjson, title, compressed_summary, system_prompt_snapshot, agent_snapshot_json, last_learning_at, last_compression_at, last_compression_input_tokens, compression_failure_count, last_compression_failed_at, last_input_tokens, last_output_tokens, last_reasoning_tokens, last_cache_read_tokens, last_cache_write_tokens, last_total_tokens, cumulative_input_tokens, cumulative_output_tokens, cumulative_reasoning_tokens, cumulative_cache_read_tokens, cumulative_cache_write_tokens, cumulative_total_tokens, last_usage_at, last_resolved_provider, last_resolved_model, created_at, updated_at from sessions where session_id = ?");
            statement.setString(1, sessionId);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return map(resultSet);
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return null;
    }

    @Override
    public SessionRecord findBySourceAndBranch(String sourceKey, String branchName)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select session_id, source_key, branch_name, parent_session_id, model_override, active_agent_name, ndjson, title, compressed_summary, system_prompt_snapshot, agent_snapshot_json, last_learning_at, last_compression_at, last_compression_input_tokens, compression_failure_count, last_compression_failed_at, last_input_tokens, last_output_tokens, last_reasoning_tokens, last_cache_read_tokens, last_cache_write_tokens, last_total_tokens, cumulative_input_tokens, cumulative_output_tokens, cumulative_reasoning_tokens, cumulative_cache_read_tokens, cumulative_cache_write_tokens, cumulative_total_tokens, last_usage_at, last_resolved_provider, last_resolved_model, created_at, updated_at from sessions where source_key = ? and branch_name = ? order by updated_at desc limit 1");
            statement.setString(1, sourceKey);
            statement.setString(2, branchName);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return map(resultSet);
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return null;
    }

    @Override
    public void save(SessionRecord sessionRecord) throws Exception {
        long updatedAt =
                sessionRecord.getUpdatedAt() > 0
                        ? sessionRecord.getUpdatedAt()
                        : System.currentTimeMillis();
        long createdAt =
                sessionRecord.getCreatedAt() > 0 ? sessionRecord.getCreatedAt() : updatedAt;

        Connection connection = database.openConnection();
        try {
            connection.setAutoCommit(false);
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into sessions (session_id, source_key, branch_name, parent_session_id, model_override, active_agent_name, ndjson, title, compressed_summary, system_prompt_snapshot, agent_snapshot_json, last_learning_at, last_compression_at, last_compression_input_tokens, compression_failure_count, last_compression_failed_at, last_input_tokens, last_output_tokens, last_reasoning_tokens, last_cache_read_tokens, last_cache_write_tokens, last_total_tokens, cumulative_input_tokens, cumulative_output_tokens, cumulative_reasoning_tokens, cumulative_cache_read_tokens, cumulative_cache_write_tokens, cumulative_total_tokens, last_usage_at, last_resolved_provider, last_resolved_model, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, sessionRecord.getSessionId());
            statement.setString(2, sessionRecord.getSourceKey());
            statement.setString(3, sessionRecord.getBranchName());
            statement.setString(4, sessionRecord.getParentSessionId());
            statement.setString(5, sessionRecord.getModelOverride());
            statement.setString(6, sessionRecord.getActiveAgentName());
            statement.setString(7, sessionRecord.getNdjson());
            statement.setString(8, sessionRecord.getTitle());
            statement.setString(9, sessionRecord.getCompressedSummary());
            statement.setString(10, sessionRecord.getSystemPromptSnapshot());
            statement.setString(11, sessionRecord.getAgentSnapshotJson());
            statement.setLong(12, sessionRecord.getLastLearningAt());
            statement.setLong(13, sessionRecord.getLastCompressionAt());
            statement.setInt(14, sessionRecord.getLastCompressionInputTokens());
            statement.setInt(15, sessionRecord.getCompressionFailureCount());
            statement.setLong(16, sessionRecord.getLastCompressionFailedAt());
            statement.setLong(17, sessionRecord.getLastInputTokens());
            statement.setLong(18, sessionRecord.getLastOutputTokens());
            statement.setLong(19, sessionRecord.getLastReasoningTokens());
            statement.setLong(20, sessionRecord.getLastCacheReadTokens());
            statement.setLong(21, sessionRecord.getLastCacheWriteTokens());
            statement.setLong(22, sessionRecord.getLastTotalTokens());
            statement.setLong(23, sessionRecord.getCumulativeInputTokens());
            statement.setLong(24, sessionRecord.getCumulativeOutputTokens());
            statement.setLong(25, sessionRecord.getCumulativeReasoningTokens());
            statement.setLong(26, sessionRecord.getCumulativeCacheReadTokens());
            statement.setLong(27, sessionRecord.getCumulativeCacheWriteTokens());
            statement.setLong(28, sessionRecord.getCumulativeTotalTokens());
            statement.setLong(29, sessionRecord.getLastUsageAt());
            statement.setString(30, sessionRecord.getLastResolvedProvider());
            statement.setString(31, sessionRecord.getLastResolvedModel());
            statement.setLong(32, createdAt);
            statement.setLong(33, updatedAt);
            statement.executeUpdate();
            statement.close();

            upsertSearchIndex(connection, sessionRecord);
            connection.commit();
            sessionRecord.setCreatedAt(createdAt);
            sessionRecord.setUpdatedAt(updatedAt);
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
            connection.close();
        }
    }

    @Override
    public List<SessionRecord> search(String keyword, int limit) throws Exception {
        List<SessionRecord> results = new ArrayList<SessionRecord>();
        Connection connection = database.openConnection();
        try {
            try {
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select s.session_id, s.source_key, s.branch_name, s.parent_session_id, s.model_override, s.active_agent_name, s.ndjson, s.title, s.compressed_summary, s.system_prompt_snapshot, s.agent_snapshot_json, s.last_learning_at, s.last_compression_at, s.last_compression_input_tokens, s.compression_failure_count, s.last_compression_failed_at, s.last_input_tokens, s.last_output_tokens, s.last_reasoning_tokens, s.last_cache_read_tokens, s.last_cache_write_tokens, s.last_total_tokens, s.cumulative_input_tokens, s.cumulative_output_tokens, s.cumulative_reasoning_tokens, s.cumulative_cache_read_tokens, s.cumulative_cache_write_tokens, s.cumulative_total_tokens, s.last_usage_at, s.last_resolved_provider, s.last_resolved_model, s.created_at, s.updated_at "
                                        + "from sessions_fts f join sessions s on s.session_id = f.session_id "
                                        + "where sessions_fts match ? order by bm25(sessions_fts), s.updated_at desc limit ?");
                statement.setString(1, keyword);
                statement.setInt(2, limit);
                ResultSet resultSet = statement.executeQuery();
                try {
                    while (resultSet.next()) {
                        results.add(map(resultSet));
                    }
                } finally {
                    resultSet.close();
                    statement.close();
                }
            } catch (Exception e) {
                PreparedStatement fallback =
                        connection.prepareStatement(
                                "select session_id, source_key, branch_name, parent_session_id, model_override, active_agent_name, ndjson, title, compressed_summary, system_prompt_snapshot, agent_snapshot_json, last_learning_at, last_compression_at, last_compression_input_tokens, compression_failure_count, last_compression_failed_at, last_input_tokens, last_output_tokens, last_reasoning_tokens, last_cache_read_tokens, last_cache_write_tokens, last_total_tokens, cumulative_input_tokens, cumulative_output_tokens, cumulative_reasoning_tokens, cumulative_cache_read_tokens, cumulative_cache_write_tokens, cumulative_total_tokens, last_usage_at, last_resolved_provider, last_resolved_model, created_at, updated_at "
                                        + "from sessions where ndjson like ? or compressed_summary like ? or title like ? order by updated_at desc limit ?");
                String like = "%" + keyword + "%";
                fallback.setString(1, like);
                fallback.setString(2, like);
                fallback.setString(3, like);
                fallback.setInt(4, limit);
                ResultSet resultSet = fallback.executeQuery();
                try {
                    while (resultSet.next()) {
                        results.add(map(resultSet));
                    }
                } finally {
                    resultSet.close();
                    fallback.close();
                }
            }
        } finally {
            connection.close();
        }
        return results;
    }

    @Override
    public List<SessionRecord> listRecent(int limit) throws Exception {
        return listRecent(limit, 0);
    }

    @Override
    public List<SessionRecord> listRecent(int limit, int offset) throws Exception {
        List<SessionRecord> results = new ArrayList<SessionRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select session_id, source_key, branch_name, parent_session_id, model_override, active_agent_name, ndjson, title, compressed_summary, system_prompt_snapshot, agent_snapshot_json, last_learning_at, last_compression_at, last_compression_input_tokens, compression_failure_count, last_compression_failed_at, last_input_tokens, last_output_tokens, last_reasoning_tokens, last_cache_read_tokens, last_cache_write_tokens, last_total_tokens, cumulative_input_tokens, cumulative_output_tokens, cumulative_reasoning_tokens, cumulative_cache_read_tokens, cumulative_cache_write_tokens, cumulative_total_tokens, last_usage_at, last_resolved_provider, last_resolved_model, created_at, updated_at "
                                    + "from sessions order by updated_at desc limit ? offset ?");
            statement.setInt(1, limit);
            statement.setInt(2, Math.max(0, offset));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    results.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return results;
    }

    @Override
    public int countAll() throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select count(1) from sessions");
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public void delete(String sessionId) throws Exception {
        Connection connection = database.openConnection();
        try {
            connection.setAutoCommit(false);
            PreparedStatement deleteFts =
                    connection.prepareStatement("delete from sessions_fts where session_id = ?");
            deleteFts.setString(1, sessionId);
            deleteFts.executeUpdate();
            deleteFts.close();

            PreparedStatement deleteBindings =
                    connection.prepareStatement("delete from bindings where session_id = ?");
            deleteBindings.setString(1, sessionId);
            deleteBindings.executeUpdate();
            deleteBindings.close();

            PreparedStatement deleteSession =
                    connection.prepareStatement("delete from sessions where session_id = ?");
            deleteSession.setString(1, sessionId);
            deleteSession.executeUpdate();
            deleteSession.close();
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
            connection.close();
        }
    }

    @Override
    public void setModelOverride(String sessionId, String modelOverride) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update sessions set model_override = ?, updated_at = ? where session_id = ?");
            statement.setString(1, modelOverride);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, sessionId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public void setActiveAgentName(String sessionId, String agentName) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update sessions set active_agent_name = ?, updated_at = ? where session_id = ?");
            statement.setString(1, agentName);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, sessionId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public void clearActiveAgentName(String agentName) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update sessions set active_agent_name = null, updated_at = ? where active_agent_name = ?");
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, agentName);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /** 将会话同步到 FTS5 索引。 */
    private void upsertSearchIndex(Connection connection, SessionRecord sessionRecord)
            throws Exception {
        PreparedStatement delete =
                connection.prepareStatement("delete from sessions_fts where session_id = ?");
        delete.setString(1, sessionRecord.getSessionId());
        delete.executeUpdate();
        delete.close();

        PreparedStatement insert =
                connection.prepareStatement(
                        "insert into sessions_fts (session_id, title, compressed_summary, ndjson, tool_names, tool_calls) values (?, ?, ?, ?, ?, ?)");
        ToolIndex toolIndex = buildToolIndex(sessionRecord.getNdjson());
        insert.setString(1, sessionRecord.getSessionId());
        insert.setString(2, sessionRecord.getTitle());
        insert.setString(3, sessionRecord.getCompressedSummary());
        insert.setString(4, sessionRecord.getNdjson());
        insert.setString(5, toolIndex.names);
        insert.setString(6, toolIndex.calls);
        insert.executeUpdate();
        insert.close();
    }

    @SuppressWarnings("unchecked")
    private ToolIndex buildToolIndex(String ndjson) {
        StringBuilder names = new StringBuilder();
        StringBuilder calls = new StringBuilder();
        try {
            List<ChatMessage> messages = MessageSupport.loadMessages(ndjson);
            for (ChatMessage message : messages) {
                if (!(message instanceof AssistantMessage)) {
                    continue;
                }
                AssistantMessage assistant = (AssistantMessage) message;
                if (assistant.getToolCalls() != null) {
                    for (ToolCall toolCall : assistant.getToolCalls()) {
                        append(names, toolCall == null ? "" : toolCall.getName());
                        if (toolCall != null) {
                            append(calls, toolCall.getName());
                            append(calls, toolCall.getArgumentsStr());
                            append(calls, ONode.serialize(toolCall.getArguments()));
                        }
                    }
                }
                if (assistant.getToolCallsRaw() != null) {
                    for (Map raw : assistant.getToolCallsRaw()) {
                        Object function = raw == null ? null : raw.get("function");
                        if (function instanceof Map) {
                            Map functionMap = (Map) function;
                            append(names, String.valueOf(functionMap.get("name")));
                            append(calls, String.valueOf(functionMap.get("name")));
                            append(calls, String.valueOf(functionMap.get("arguments")));
                        } else {
                            append(calls, ONode.serialize(raw));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ToolIndex(names.toString(), calls.toString());
    }

    private void append(StringBuilder buffer, String value) {
        if (StrUtil.isBlank(value) || "null".equals(value)) {
            return;
        }
        if (buffer.length() > 0) {
            buffer.append('\n');
        }
        buffer.append(value.trim());
    }

    private static class ToolIndex {
        private final String names;
        private final String calls;

        private ToolIndex(String names, String calls) {
            this.names = names;
            this.calls = calls;
        }
    }

    /** 结果集映射。 */
    private SessionRecord map(ResultSet resultSet) throws Exception {
        SessionRecord record = new SessionRecord();
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setBranchName(resultSet.getString("branch_name"));
        record.setParentSessionId(resultSet.getString("parent_session_id"));
        record.setModelOverride(resultSet.getString("model_override"));
        record.setActiveAgentName(resultSet.getString("active_agent_name"));
        record.setNdjson(resultSet.getString("ndjson"));
        record.setTitle(resultSet.getString("title"));
        record.setCompressedSummary(resultSet.getString("compressed_summary"));
        record.setSystemPromptSnapshot(resultSet.getString("system_prompt_snapshot"));
        record.setAgentSnapshotJson(resultSet.getString("agent_snapshot_json"));
        record.setLastLearningAt(resultSet.getLong("last_learning_at"));
        record.setLastCompressionAt(resultSet.getLong("last_compression_at"));
        record.setLastCompressionInputTokens(resultSet.getInt("last_compression_input_tokens"));
        record.setCompressionFailureCount(resultSet.getInt("compression_failure_count"));
        record.setLastCompressionFailedAt(resultSet.getLong("last_compression_failed_at"));
        record.setLastInputTokens(resultSet.getLong("last_input_tokens"));
        record.setLastOutputTokens(resultSet.getLong("last_output_tokens"));
        record.setLastReasoningTokens(resultSet.getLong("last_reasoning_tokens"));
        record.setLastCacheReadTokens(resultSet.getLong("last_cache_read_tokens"));
        record.setLastCacheWriteTokens(resultSet.getLong("last_cache_write_tokens"));
        record.setLastTotalTokens(resultSet.getLong("last_total_tokens"));
        record.setCumulativeInputTokens(resultSet.getLong("cumulative_input_tokens"));
        record.setCumulativeOutputTokens(resultSet.getLong("cumulative_output_tokens"));
        record.setCumulativeReasoningTokens(resultSet.getLong("cumulative_reasoning_tokens"));
        record.setCumulativeCacheReadTokens(resultSet.getLong("cumulative_cache_read_tokens"));
        record.setCumulativeCacheWriteTokens(resultSet.getLong("cumulative_cache_write_tokens"));
        record.setCumulativeTotalTokens(resultSet.getLong("cumulative_total_tokens"));
        record.setLastUsageAt(resultSet.getLong("last_usage_at"));
        record.setLastResolvedProvider(resultSet.getString("last_resolved_provider"));
        record.setLastResolvedModel(resultSet.getString("last_resolved_model"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setUpdatedAt(resultSet.getLong("updated_at"));
        return record;
    }
}
