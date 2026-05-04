package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.QueuedRunMessage;
import com.jimuqu.solon.claw.core.model.RunRecoveryRecord;
import com.jimuqu.solon.claw.core.model.RunControlCommand;
import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** SQLite Agent run 仓储实现。 */
@RequiredArgsConstructor
public class SqliteAgentRunRepository implements AgentRunRepository {
    private final SqliteDatabase database;

    @Override
    public void saveRun(AgentRunRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into agent_runs (run_id, session_id, source_key, run_kind, parent_run_id, agent_name, agent_snapshot_json, status, phase, busy_policy, backgrounded, input_preview, final_reply_preview, provider, model, attempts, context_estimate_tokens, context_window_tokens, compression_count, fallback_count, tool_call_count, subtask_count, input_tokens, output_tokens, total_tokens, queued_at, started_at, heartbeat_at, last_activity_at, finished_at, exit_reason, recoverable, recovery_hint, error) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getRunId());
            statement.setString(2, record.getSessionId());
            statement.setString(3, record.getSourceKey());
            statement.setString(4, record.getRunKind());
            statement.setString(5, record.getParentRunId());
            statement.setString(6, record.getAgentName());
            statement.setString(7, record.getAgentSnapshotJson());
            statement.setString(8, record.getStatus());
            statement.setString(9, record.getPhase());
            statement.setString(10, record.getBusyPolicy());
            statement.setInt(11, record.isBackgrounded() ? 1 : 0);
            statement.setString(12, record.getInputPreview());
            statement.setString(13, record.getFinalReplyPreview());
            statement.setString(14, record.getProvider());
            statement.setString(15, record.getModel());
            statement.setInt(16, record.getAttempts());
            statement.setInt(17, record.getContextEstimateTokens());
            statement.setInt(18, record.getContextWindowTokens());
            statement.setInt(19, record.getCompressionCount());
            statement.setInt(20, record.getFallbackCount());
            statement.setInt(21, record.getToolCallCount());
            statement.setInt(22, record.getSubtaskCount());
            statement.setLong(23, record.getInputTokens());
            statement.setLong(24, record.getOutputTokens());
            statement.setLong(25, record.getTotalTokens());
            statement.setLong(26, record.getQueuedAt());
            statement.setLong(27, record.getStartedAt());
            statement.setLong(28, record.getHeartbeatAt());
            statement.setLong(29, record.getLastActivityAt());
            statement.setLong(30, record.getFinishedAt());
            statement.setString(31, record.getExitReason());
            statement.setInt(32, record.isRecoverable() ? 1 : 0);
            statement.setString(33, record.getRecoveryHint());
            statement.setString(34, record.getError());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public AgentRunRecord findRun(String runId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select * from agent_runs where run_id = ?");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapRun(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public List<AgentRunRecord> listBySession(String sessionId, int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where session_id = ? order by started_at desc limit ?");
            statement.setString(1, sessionId);
            statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public List<AgentRunRecord> listRecoverable(int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where recoverable = 1 order by last_activity_at desc limit ?");
            statement.setInt(1, Math.max(1, Math.min(limit, 200)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public List<AgentRunRecord> listActiveBefore(long beforeEpochMillis, int limit)
            throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where status in ('queued','running','waiting_approval','backgrounded','paused','interrupting') and coalesce(nullif(last_activity_at, 0), started_at) < ? order by started_at asc limit ?");
            statement.setLong(1, beforeEpochMillis);
            statement.setInt(2, Math.max(1, Math.min(limit, 200)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void markStaleRuns(long beforeEpochMillis, long now) throws Exception {
        List<AgentRunRecord> stale = listActiveBefore(beforeEpochMillis, 500);
        for (AgentRunRecord record : stale) {
            record.setStatus("recoverable");
            record.setPhase("recovery");
            record.setRecoverable(true);
            record.setRecoveryHint("服务重启或长时间无 heartbeat，已标记为可恢复。");
            record.setExitReason("stale_heartbeat");
            record.setFinishedAt(0L);
            saveRun(record);

            RunRecoveryRecord recovery = new RunRecoveryRecord();
            recovery.setRecoveryId(com.jimuqu.solon.claw.support.IdSupport.newId());
            recovery.setRunId(record.getRunId());
            recovery.setSessionId(record.getSessionId());
            recovery.setSourceKey(record.getSourceKey());
            recovery.setRecoveryType("stale_heartbeat");
            recovery.setStatus("recoverable");
            recovery.setSummary(record.getRecoveryHint());
            recovery.setCreatedAt(now);
            saveRecovery(recovery);
        }
    }

    @Override
    public List<AgentRunRecord> listActiveBySource(String sourceKey, int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where source_key = ? and status in ('queued','running','waiting_approval','backgrounded','paused','interrupting','recoverable') order by started_at desc limit ?");
            statement.setString(1, sourceKey);
            statement.setInt(2, Math.max(1, Math.min(limit, 50)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public List<AgentRunRecord> searchRuns(
            String sourceKey,
            String sessionId,
            String runId,
            String query,
            long timeFrom,
            long timeTo,
            int limit)
            throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            StringBuilder sql = new StringBuilder("select distinct r.* from agent_runs r");
            List<Object> args = new ArrayList<Object>();
            boolean hasQuery = query != null && query.trim().length() > 0;
            if (hasQuery) {
                sql.append(" left join agent_run_events e on e.run_id = r.run_id");
            }
            sql.append(" where 1 = 1");
            appendRunFilters(sql, args, sourceKey, sessionId, runId, timeFrom, timeTo);
            if (hasQuery) {
                sql.append(
                        " and (lower(coalesce(r.input_preview, '')) like ?"
                                + " or lower(coalesce(r.final_reply_preview, '')) like ?"
                                + " or lower(coalesce(r.error, '')) like ?"
                                + " or lower(coalesce(e.summary, '')) like ?"
                                + " or lower(coalesce(e.metadata_json, '')) like ?)");
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }
            sql.append(" order by coalesce(nullif(r.last_activity_at, 0), r.started_at) desc limit ?");
            args.add(Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200)));
            PreparedStatement statement = connection.prepareStatement(sql.toString());
            bindArgs(statement, args);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void appendEvent(AgentRunEventRecord event) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events (event_id, run_id, session_id, source_key, event_type, phase, severity, attempt_no, provider, model, summary, metadata_json, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getRunId());
            statement.setString(3, event.getSessionId());
            statement.setString(4, event.getSourceKey());
            statement.setString(5, event.getEventType());
            statement.setString(6, event.getPhase());
            statement.setString(7, event.getSeverity());
            statement.setInt(8, event.getAttemptNo());
            statement.setString(9, event.getProvider());
            statement.setString(10, event.getModel());
            statement.setString(11, event.getSummary());
            statement.setString(12, event.getMetadataJson());
            statement.setLong(13, event.getCreatedAt());
            statement.executeUpdate();
            appendEventFts(connection, event);
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<AgentRunEventRecord> listEvents(String runId) throws Exception {
        List<AgentRunEventRecord> events = new ArrayList<AgentRunEventRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_run_events where run_id = ? order by created_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return events;
    }

    @Override
    public void saveRunControlCommand(RunControlCommand command) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into run_control_commands (command_id, run_id, source_key, command, payload_json, status, created_at, handled_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, command.getCommandId());
            statement.setString(2, command.getRunId());
            statement.setString(3, command.getSourceKey());
            statement.setString(4, command.getCommand());
            statement.setString(5, command.getPayloadJson());
            statement.setString(6, command.getStatus());
            statement.setLong(7, command.getCreatedAt());
            statement.setLong(8, command.getHandledAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<RunControlCommand> listRunControlCommands(String runId) throws Exception {
        List<RunControlCommand> records = new ArrayList<RunControlCommand>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from run_control_commands where run_id = ? order by created_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRunControlCommand(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public RunControlCommand findLatestPendingCommand(String runId, String command)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from run_control_commands where run_id = ? and command = ? and status = 'pending' order by created_at desc limit 1");
            statement.setString(1, runId);
            statement.setString(2, command);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapRunControlCommand(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public void markRunControlCommandHandled(String commandId, String status, long handledAt)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update run_control_commands set status = ?, handled_at = ? where command_id = ?");
            statement.setString(1, status);
            statement.setLong(2, handledAt);
            statement.setString(3, commandId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public void saveQueuedMessage(QueuedRunMessage message) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into queued_run_messages (queue_id, run_id, session_id, source_key, message_text, message_json, status, busy_policy, created_at, started_at, finished_at, error) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, message.getQueueId());
            statement.setString(2, message.getRunId());
            statement.setString(3, message.getSessionId());
            statement.setString(4, message.getSourceKey());
            statement.setString(5, message.getMessageText());
            statement.setString(6, message.getMessageJson());
            statement.setString(7, message.getStatus());
            statement.setString(8, message.getBusyPolicy());
            statement.setLong(9, message.getCreatedAt());
            statement.setLong(10, message.getStartedAt());
            statement.setLong(11, message.getFinishedAt());
            statement.setString(12, message.getError());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public QueuedRunMessage findNextQueuedMessage(String sourceKey, String sessionId)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from queued_run_messages where source_key = ? and session_id = ? and status = 'queued' order by created_at asc limit 1");
            statement.setString(1, sourceKey);
            statement.setString(2, sessionId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapQueuedMessage(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public void markQueuedMessage(String queueId, String status, long timestamp, String error)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update queued_run_messages set status = ?, started_at = case when ? = 'running' then ? else started_at end, finished_at = case when ? in ('success','failed','cancelled') then ? else finished_at end, error = ? where queue_id = ?");
            statement.setString(1, status);
            statement.setString(2, status);
            statement.setLong(3, timestamp);
            statement.setString(4, status);
            statement.setLong(5, timestamp);
            statement.setString(6, error);
            statement.setString(7, queueId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public void saveToolCall(ToolCallRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into tool_calls (tool_call_id, run_id, session_id, source_key, tool_name, status, args_preview, result_preview, result_ref, error, read_only, interruptible, side_effecting, result_indexable, output_limit_bytes, result_size_bytes, execution_policy, started_at, finished_at, duration_ms) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getToolCallId());
            statement.setString(2, record.getRunId());
            statement.setString(3, record.getSessionId());
            statement.setString(4, record.getSourceKey());
            statement.setString(5, record.getToolName());
            statement.setString(6, record.getStatus());
            statement.setString(7, record.getArgsPreview());
            statement.setString(8, record.getResultPreview());
            statement.setString(9, record.getResultRef());
            statement.setString(10, record.getError());
            statement.setInt(11, record.isReadOnly() ? 1 : 0);
            statement.setInt(12, record.isInterruptible() ? 1 : 0);
            statement.setInt(13, record.isSideEffecting() ? 1 : 0);
            statement.setInt(14, record.isResultIndexable() ? 1 : 0);
            statement.setInt(15, record.getOutputLimitBytes());
            statement.setLong(16, record.getResultSizeBytes());
            statement.setString(17, record.getExecutionPolicy());
            statement.setLong(18, record.getStartedAt());
            statement.setLong(19, record.getFinishedAt());
            statement.setLong(20, record.getDurationMs());
            statement.executeUpdate();
            statement.close();
            incrementToolCallCount(connection, record);
            appendToolResultFts(connection, record);
        } finally {
            connection.close();
        }
    }

    @Override
    public List<ToolCallRecord> listToolCalls(String runId) throws Exception {
        List<ToolCallRecord> records = new ArrayList<ToolCallRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from tool_calls where run_id = ? order by started_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapToolCall(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public List<ToolCallRecord> searchToolCalls(
            String sourceKey,
            String sessionId,
            String runId,
            String toolName,
            String query,
            long timeFrom,
            long timeTo,
            int limit)
            throws Exception {
        List<ToolCallRecord> records = new ArrayList<ToolCallRecord>();
        Connection connection = database.openConnection();
        try {
            StringBuilder sql = new StringBuilder("select * from tool_calls where 1 = 1");
            List<Object> args = new ArrayList<Object>();
            if (sourceKey != null && sourceKey.trim().length() > 0) {
                sql.append(" and source_key = ?");
                args.add(sourceKey);
            }
            if (sessionId != null && sessionId.trim().length() > 0) {
                sql.append(" and session_id = ?");
                args.add(sessionId);
            }
            if (runId != null && runId.trim().length() > 0) {
                sql.append(" and run_id = ?");
                args.add(runId);
            }
            if (toolName != null && toolName.trim().length() > 0) {
                sql.append(" and tool_name = ?");
                args.add(toolName);
            }
            if (timeFrom > 0) {
                sql.append(" and started_at >= ?");
                args.add(Long.valueOf(timeFrom));
            }
            if (timeTo > 0) {
                sql.append(" and started_at <= ?");
                args.add(Long.valueOf(timeTo));
            }
            if (query != null && query.trim().length() > 0) {
                sql.append(
                        " and (lower(coalesce(tool_name, '')) like ?"
                                + " or lower(coalesce(args_preview, '')) like ?"
                                + " or lower(coalesce(result_preview, '')) like ?"
                                + " or lower(coalesce(error, '')) like ?)");
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }
            sql.append(" order by started_at desc limit ?");
            args.add(Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200)));
            PreparedStatement statement = connection.prepareStatement(sql.toString());
            bindArgs(statement, args);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapToolCall(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void saveSubagentRun(SubagentRunRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into subagent_runs (subagent_id, parent_run_id, child_run_id, parent_source_key, child_source_key, session_id, name, goal_preview, status, active, interrupt_requested, depth, task_index, output_tail_json, error, started_at, finished_at, heartbeat_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getSubagentId());
            statement.setString(2, record.getParentRunId());
            statement.setString(3, record.getChildRunId());
            statement.setString(4, record.getParentSourceKey());
            statement.setString(5, record.getChildSourceKey());
            statement.setString(6, record.getSessionId());
            statement.setString(7, record.getName());
            statement.setString(8, record.getGoalPreview());
            statement.setString(9, record.getStatus());
            statement.setInt(10, record.isActive() ? 1 : 0);
            statement.setInt(11, record.isInterruptRequested() ? 1 : 0);
            statement.setInt(12, record.getDepth());
            statement.setInt(13, record.getTaskIndex());
            statement.setString(14, record.getOutputTailJson());
            statement.setString(15, record.getError());
            statement.setLong(16, record.getStartedAt());
            statement.setLong(17, record.getFinishedAt());
            statement.setLong(18, record.getHeartbeatAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<SubagentRunRecord> listSubagents(String parentRunId) throws Exception {
        List<SubagentRunRecord> records = new ArrayList<SubagentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from subagent_runs where parent_run_id = ? order by started_at asc");
            statement.setString(1, parentRunId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapSubagent(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void saveRecovery(RunRecoveryRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into run_recoveries (recovery_id, run_id, session_id, source_key, recovery_type, status, summary, payload_json, created_at, resolved_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getRecoveryId());
            statement.setString(2, record.getRunId());
            statement.setString(3, record.getSessionId());
            statement.setString(4, record.getSourceKey());
            statement.setString(5, record.getRecoveryType());
            statement.setString(6, record.getStatus());
            statement.setString(7, record.getSummary());
            statement.setString(8, record.getPayloadJson());
            statement.setLong(9, record.getCreatedAt());
            statement.setLong(10, record.getResolvedAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<RunRecoveryRecord> listRecoveries(String runId) throws Exception {
        List<RunRecoveryRecord> records = new ArrayList<RunRecoveryRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from run_recoveries where run_id = ? order by created_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRecovery(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void pruneBefore(long beforeEpochMillis) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement deleteToolCalls =
                    connection.prepareStatement(
                            "delete from tool_calls where run_id in (select run_id from agent_runs where started_at < ?)");
            deleteToolCalls.setLong(1, beforeEpochMillis);
            deleteToolCalls.executeUpdate();
            deleteToolCalls.close();

            PreparedStatement deleteSubagents =
                    connection.prepareStatement(
                            "delete from subagent_runs where parent_run_id in (select run_id from agent_runs where started_at < ?)");
            deleteSubagents.setLong(1, beforeEpochMillis);
            deleteSubagents.executeUpdate();
            deleteSubagents.close();

            PreparedStatement deleteRecoveries =
                    connection.prepareStatement(
                            "delete from run_recoveries where run_id in (select run_id from agent_runs where started_at < ?)");
            deleteRecoveries.setLong(1, beforeEpochMillis);
            deleteRecoveries.executeUpdate();
            deleteRecoveries.close();

            PreparedStatement deleteEvents =
                    connection.prepareStatement(
                            "delete from agent_run_events where run_id in (select run_id from agent_runs where started_at < ?)");
            deleteEvents.setLong(1, beforeEpochMillis);
            deleteEvents.executeUpdate();
            deleteEvents.close();

            PreparedStatement deleteRuns =
                    connection.prepareStatement("delete from agent_runs where started_at < ?");
            deleteRuns.setLong(1, beforeEpochMillis);
            deleteRuns.executeUpdate();
            deleteRuns.close();
        } finally {
            connection.close();
        }
    }

    private AgentRunRecord mapRun(ResultSet resultSet) throws Exception {
        AgentRunRecord record = new AgentRunRecord();
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setRunKind(resultSet.getString("run_kind"));
        record.setParentRunId(resultSet.getString("parent_run_id"));
        record.setAgentName(resultSet.getString("agent_name"));
        record.setAgentSnapshotJson(resultSet.getString("agent_snapshot_json"));
        record.setStatus(resultSet.getString("status"));
        record.setPhase(resultSet.getString("phase"));
        record.setBusyPolicy(resultSet.getString("busy_policy"));
        record.setBackgrounded(resultSet.getInt("backgrounded") != 0);
        record.setInputPreview(resultSet.getString("input_preview"));
        record.setFinalReplyPreview(resultSet.getString("final_reply_preview"));
        record.setProvider(resultSet.getString("provider"));
        record.setModel(resultSet.getString("model"));
        record.setAttempts(resultSet.getInt("attempts"));
        record.setContextEstimateTokens(resultSet.getInt("context_estimate_tokens"));
        record.setContextWindowTokens(resultSet.getInt("context_window_tokens"));
        record.setCompressionCount(resultSet.getInt("compression_count"));
        record.setFallbackCount(resultSet.getInt("fallback_count"));
        record.setToolCallCount(resultSet.getInt("tool_call_count"));
        record.setSubtaskCount(resultSet.getInt("subtask_count"));
        record.setInputTokens(resultSet.getLong("input_tokens"));
        record.setOutputTokens(resultSet.getLong("output_tokens"));
        record.setTotalTokens(resultSet.getLong("total_tokens"));
        record.setQueuedAt(resultSet.getLong("queued_at"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setHeartbeatAt(resultSet.getLong("heartbeat_at"));
        record.setLastActivityAt(resultSet.getLong("last_activity_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setExitReason(resultSet.getString("exit_reason"));
        record.setRecoverable(resultSet.getInt("recoverable") != 0);
        record.setRecoveryHint(resultSet.getString("recovery_hint"));
        record.setError(resultSet.getString("error"));
        return record;
    }

    private AgentRunEventRecord mapEvent(ResultSet resultSet) throws Exception {
        AgentRunEventRecord record = new AgentRunEventRecord();
        record.setEventId(resultSet.getString("event_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setEventType(resultSet.getString("event_type"));
        record.setPhase(resultSet.getString("phase"));
        record.setSeverity(resultSet.getString("severity"));
        record.setAttemptNo(resultSet.getInt("attempt_no"));
        record.setProvider(resultSet.getString("provider"));
        record.setModel(resultSet.getString("model"));
        record.setSummary(resultSet.getString("summary"));
        record.setMetadataJson(resultSet.getString("metadata_json"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        return record;
    }

    private ToolCallRecord mapToolCall(ResultSet resultSet) throws Exception {
        ToolCallRecord record = new ToolCallRecord();
        record.setToolCallId(resultSet.getString("tool_call_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setToolName(resultSet.getString("tool_name"));
        record.setStatus(resultSet.getString("status"));
        record.setArgsPreview(resultSet.getString("args_preview"));
        record.setResultPreview(resultSet.getString("result_preview"));
        record.setResultRef(resultSet.getString("result_ref"));
        record.setError(resultSet.getString("error"));
        record.setReadOnly(resultSet.getInt("read_only") != 0);
        record.setInterruptible(resultSet.getInt("interruptible") != 0);
        record.setSideEffecting(resultSet.getInt("side_effecting") != 0);
        record.setResultIndexable(resultSet.getInt("result_indexable") != 0);
        record.setOutputLimitBytes(resultSet.getInt("output_limit_bytes"));
        record.setResultSizeBytes(resultSet.getLong("result_size_bytes"));
        record.setExecutionPolicy(resultSet.getString("execution_policy"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setDurationMs(resultSet.getLong("duration_ms"));
        return record;
    }

    private SubagentRunRecord mapSubagent(ResultSet resultSet) throws Exception {
        SubagentRunRecord record = new SubagentRunRecord();
        record.setSubagentId(resultSet.getString("subagent_id"));
        record.setParentRunId(resultSet.getString("parent_run_id"));
        record.setChildRunId(resultSet.getString("child_run_id"));
        record.setParentSourceKey(resultSet.getString("parent_source_key"));
        record.setChildSourceKey(resultSet.getString("child_source_key"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setName(resultSet.getString("name"));
        record.setGoalPreview(resultSet.getString("goal_preview"));
        record.setStatus(resultSet.getString("status"));
        record.setActive(resultSet.getInt("active") != 0);
        record.setInterruptRequested(resultSet.getInt("interrupt_requested") != 0);
        record.setDepth(resultSet.getInt("depth"));
        record.setTaskIndex(resultSet.getInt("task_index"));
        record.setOutputTailJson(resultSet.getString("output_tail_json"));
        record.setError(resultSet.getString("error"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setHeartbeatAt(resultSet.getLong("heartbeat_at"));
        return record;
    }

    private RunControlCommand mapRunControlCommand(ResultSet resultSet) throws Exception {
        RunControlCommand record = new RunControlCommand();
        record.setCommandId(resultSet.getString("command_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setCommand(resultSet.getString("command"));
        record.setPayloadJson(resultSet.getString("payload_json"));
        record.setStatus(resultSet.getString("status"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setHandledAt(resultSet.getLong("handled_at"));
        return record;
    }

    private QueuedRunMessage mapQueuedMessage(ResultSet resultSet) throws Exception {
        QueuedRunMessage record = new QueuedRunMessage();
        record.setQueueId(resultSet.getString("queue_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setMessageText(resultSet.getString("message_text"));
        record.setMessageJson(resultSet.getString("message_json"));
        record.setStatus(resultSet.getString("status"));
        record.setBusyPolicy(resultSet.getString("busy_policy"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setError(resultSet.getString("error"));
        return record;
    }

    private RunRecoveryRecord mapRecovery(ResultSet resultSet) throws Exception {
        RunRecoveryRecord record = new RunRecoveryRecord();
        record.setRecoveryId(resultSet.getString("recovery_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setRecoveryType(resultSet.getString("recovery_type"));
        record.setStatus(resultSet.getString("status"));
        record.setSummary(resultSet.getString("summary"));
        record.setPayloadJson(resultSet.getString("payload_json"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setResolvedAt(resultSet.getLong("resolved_at"));
        return record;
    }

    private void appendRunFilters(
            StringBuilder sql,
            List<Object> args,
            String sourceKey,
            String sessionId,
            String runId,
            long timeFrom,
            long timeTo) {
        if (sourceKey != null && sourceKey.trim().length() > 0) {
            sql.append(" and r.source_key = ?");
            args.add(sourceKey);
        }
        if (sessionId != null && sessionId.trim().length() > 0) {
            sql.append(" and r.session_id = ?");
            args.add(sessionId);
        }
        if (runId != null && runId.trim().length() > 0) {
            sql.append(" and r.run_id = ?");
            args.add(runId);
        }
        if (timeFrom > 0) {
            sql.append(" and coalesce(nullif(r.last_activity_at, 0), r.started_at) >= ?");
            args.add(Long.valueOf(timeFrom));
        }
        if (timeTo > 0) {
            sql.append(" and coalesce(nullif(r.last_activity_at, 0), r.started_at) <= ?");
            args.add(Long.valueOf(timeTo));
        }
    }

    private void bindArgs(PreparedStatement statement, List<Object> args) throws Exception {
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i);
            if (value instanceof Long) {
                statement.setLong(i + 1, ((Long) value).longValue());
            } else if (value instanceof Integer) {
                statement.setInt(i + 1, ((Integer) value).intValue());
            } else {
                statement.setString(i + 1, value == null ? null : String.valueOf(value));
            }
        }
    }

    private void appendEventFts(Connection connection, AgentRunEventRecord event) {
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events_fts (run_id, session_id, source_key, event_type, summary, metadata_json) values (?, ?, ?, ?, ?, ?)");
            statement.setString(1, event.getRunId());
            statement.setString(2, event.getSessionId());
            statement.setString(3, event.getSourceKey());
            statement.setString(4, event.getEventType());
            statement.setString(5, event.getSummary());
            statement.setString(6, event.getMetadataJson());
            statement.executeUpdate();
            statement.close();
        } catch (Exception ignored) {
        }
    }

    private void incrementToolCallCount(Connection connection, ToolCallRecord record) {
        if (record == null
                || record.getRunId() == null
                || !"completed".equalsIgnoreCase(record.getStatus())) {
            return;
        }
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update agent_runs set tool_call_count = (select count(*) from tool_calls where run_id = ? and status in ('completed','failed')) where run_id = ?");
            statement.setString(1, record.getRunId());
            statement.setString(2, record.getRunId());
            statement.executeUpdate();
            statement.close();
        } catch (Exception ignored) {
        }
    }

    private void appendToolResultFts(Connection connection, ToolCallRecord record) {
        if (record == null || !record.isResultIndexable()) {
            return;
        }
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events_fts (run_id, session_id, source_key, event_type, summary, metadata_json) values (?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getRunId());
            statement.setString(2, record.getSessionId());
            statement.setString(3, record.getSourceKey());
            statement.setString(4, "tool.result");
            statement.setString(
                    5,
                    String.valueOf(record.getToolName())
                            + " "
                            + String.valueOf(record.getResultPreview()));
            statement.setString(
                    6,
                    "{\"tool_name\":\""
                            + escapeJson(record.getToolName())
                            + "\",\"args_preview\":\""
                            + escapeJson(record.getArgsPreview())
                            + "\",\"result_ref\":\""
                            + escapeJson(record.getResultRef())
                            + "\"}");
            statement.executeUpdate();
            statement.close();
        } catch (Exception ignored) {
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
