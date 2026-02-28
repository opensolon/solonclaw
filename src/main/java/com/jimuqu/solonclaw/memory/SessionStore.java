package com.jimuqu.solonclaw.memory;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话存储
 * <p>
 * 负责会话记忆的数据库操作
 *
 * @author SolonClaw
 */
@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    @Inject
    private DataSource dataSource;

    /**
     * 初始化数据库表结构
     */
    @Init
    public void initTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 创建 sessions 表
            String createSessionsTable = """
                CREATE TABLE IF NOT EXISTS sessions (
                    id VARCHAR PRIMARY KEY,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            stmt.execute(createSessionsTable);

            // 创建 messages 表
            String createMessagesTable = """
                CREATE TABLE IF NOT EXISTS messages (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    session_id VARCHAR NOT NULL,
                    role VARCHAR NOT NULL,
                    content TEXT NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
                )
                """;
            stmt.execute(createMessagesTable);

            // 创建索引以提高查询性能
            String createIndex = "CREATE INDEX IF NOT EXISTS idx_messages_session_id ON messages(session_id)";
            stmt.execute(createIndex);

            String createTimestampIndex = "CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)";
            stmt.execute(createTimestampIndex);

            log.info("数据库表初始化完成");

        } catch (SQLException e) {
            log.error("初始化数据库表失败", e);
            throw new RuntimeException("初始化数据库表失败", e);
        }
    }

    /**
     * 创建或获取会话
     */
    public String createOrGetSession(String sessionId) {
        try (Connection conn = dataSource.getConnection()) {
            // 检查会话是否存在
            String checkSql = "SELECT id FROM sessions WHERE id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, sessionId);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    // 会话已存在，更新 updated_at
                    updateSessionTimestamp(conn, sessionId);
                    return sessionId;
                } else {
                    // 创建新会话
                    return createSession(conn, sessionId);
                }
            }
        } catch (SQLException e) {
            log.error("创建或获取会话失败", e);
            throw new RuntimeException("创建或获取会话失败", e);
        }
    }

    /**
     * 创建新会话
     */
    private String createSession(Connection conn, String sessionId) throws SQLException {
        String sql = "INSERT INTO sessions (id) VALUES (?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
            log.debug("创建新会话: {}", sessionId);
            return sessionId;
        }
    }

    /**
     * 更新会话时间戳
     */
    private void updateSessionTimestamp(Connection conn, String sessionId) throws SQLException {
        String sql = "UPDATE sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.executeUpdate();
        }
    }

    /**
     * 保存消息
     */
    public void saveMessage(String sessionId, String role, String content) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO messages (session_id, role, content) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, role);
                stmt.setString(3, content);
                stmt.executeUpdate();

                // 获取生成的主键
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    long messageId = rs.getLong(1);
                    log.debug("保存消息: sessionId={}, messageId={}, role={}, contentLength={}",
                        sessionId, messageId, role, content.length());
                }
            }
        } catch (SQLException e) {
            log.error("保存消息失败", e);
            throw new RuntimeException("保存消息失败", e);
        }
    }

    /**
     * 获取会话历史消息
     */
    public List<Message> getSessionMessages(String sessionId, int limit) {
        List<Message> messages = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT id, session_id, role, content, timestamp
                FROM messages
                WHERE session_id = ?
                ORDER BY timestamp ASC
                LIMIT ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    messages.add(new Message(
                        rs.getLong("id"),
                        rs.getString("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getTimestamp("timestamp").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    ));
                }

                log.debug("获取会话历史: sessionId={}, count={}", sessionId, messages.size());
            }
        } catch (SQLException e) {
            log.error("获取会话历史失败", e);
            throw new RuntimeException("获取会话历史失败", e);
        }

        return messages;
    }

    /**
     * 获取所有会话列表
     */
    public List<SessionInfo> listSessions(int limit) {
        List<SessionInfo> sessions = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT id, created_at, updated_at
                FROM sessions
                ORDER BY updated_at DESC
                LIMIT ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    sessions.add(new SessionInfo(
                        rs.getString("id"),
                        rs.getTimestamp("created_at").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime(),
                        rs.getTimestamp("updated_at").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("获取会话列表失败", e);
            throw new RuntimeException("获取会话列表失败", e);
        }

        return sessions;
    }

    /**
     * 搜索消息（简单关键词匹配）
     */
    public List<Message> searchMessages(String keyword, int limit) {
        List<Message> messages = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT id, session_id, role, content, timestamp
                FROM messages
                WHERE content LIKE ?
                ORDER BY timestamp DESC
                LIMIT ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "%" + keyword + "%");
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    messages.add(new Message(
                        rs.getLong("id"),
                        rs.getString("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getTimestamp("timestamp").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    ));
                }

                log.debug("搜索消息: keyword={}, count={}", keyword, messages.size());
            }
        } catch (SQLException e) {
            log.error("搜索消息失败", e);
            throw new RuntimeException("搜索消息失败", e);
        }

        return messages;
    }

    /**
     * 删除会话及其所有消息
     */
    public void deleteSession(String sessionId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM sessions WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                int affectedRows = stmt.executeUpdate();
                log.debug("删除会话: sessionId={}, affectedRows={}", sessionId, affectedRows);
            }
        } catch (SQLException e) {
            log.error("删除会话失败", e);
            throw new RuntimeException("删除会话失败", e);
        }
    }

    /**
     * 消息记录
     */
    public record Message(
            long id,
            String sessionId,
            String role,
            String content,
            LocalDateTime timestamp
    ) {
    }

    /**
     * 会话信息
     */
    public record SessionInfo(
            String id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}