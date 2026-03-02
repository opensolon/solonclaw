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

            // ==================== 学习系统相关表 ====================

            // 创建 reflections 表 - 反思记录
            String createReflectionsTable = """
                CREATE TABLE IF NOT EXISTS reflections (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    session_id VARCHAR,
                    reflection_type VARCHAR(50) NOT NULL,
                    content TEXT NOT NULL,
                    context TEXT,
                    action_items TEXT,
                    effectiveness_score DOUBLE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL
                )
                """;
            stmt.execute(createReflectionsTable);

            // 创建 experiences 表 - 经验条目
            String createExperiencesTable = """
                CREATE TABLE IF NOT EXISTS experiences (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    experience_type VARCHAR(50) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    content TEXT NOT NULL,
                    source_type VARCHAR(50),
                    source_id VARCHAR(255),
                    success BOOLEAN DEFAULT true,
                    confidence DOUBLE DEFAULT 0.5,
                    usage_count INTEGER DEFAULT 0,
                    effectiveness_score DOUBLE DEFAULT 0.0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_used_at TIMESTAMP
                )
                """;
            stmt.execute(createExperiencesTable);

            // 创建 skill_requests 表 - 技能需求
            String createSkillRequestsTable = """
                CREATE TABLE IF NOT EXISTS skill_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    reflection_id BIGINT,
                    skill_name VARCHAR(255) NOT NULL,
                    skill_description TEXT NOT NULL,
                    priority INTEGER DEFAULT 5,
                    status VARCHAR(50) DEFAULT 'pending',
                    metadata TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (reflection_id) REFERENCES reflections(id) ON DELETE CASCADE
                )
                """;
            stmt.execute(createSkillRequestsTable);

            // 创建学习系统相关索引
            String createReflectionsTypeIndex = "CREATE INDEX IF NOT EXISTS idx_reflections_type ON reflections(reflection_type)";
            stmt.execute(createReflectionsTypeIndex);

            String createReflectionsSessionIndex = "CREATE INDEX IF NOT EXISTS idx_reflections_session ON reflections(session_id)";
            stmt.execute(createReflectionsSessionIndex);

            String createExperiencesTypeIndex = "CREATE INDEX IF NOT EXISTS idx_experiences_type ON experiences(experience_type)";
            stmt.execute(createExperiencesTypeIndex);

            String createExperiencesSourceIndex = "CREATE INDEX IF NOT EXISTS idx_experiences_source ON experiences(source_type, source_id)";
            stmt.execute(createExperiencesSourceIndex);

            String createExperiencesConfidenceIndex = "CREATE INDEX IF NOT EXISTS idx_experiences_confidence ON experiences(confidence DESC)";
            stmt.execute(createExperiencesConfidenceIndex);

            String createExperiencesUsageIndex = "CREATE INDEX IF NOT EXISTS idx_experiences_usage ON experiences(usage_count DESC)";
            stmt.execute(createExperiencesUsageIndex);

            String createSkillRequestsReflectionIndex = "CREATE INDEX IF NOT EXISTS idx_skill_requests_reflection ON skill_requests(reflection_id)";
            stmt.execute(createSkillRequestsReflectionIndex);

            String createSkillRequestsStatusIndex = "CREATE INDEX IF NOT EXISTS idx_skill_requests_status ON skill_requests(status)";
            stmt.execute(createSkillRequestsStatusIndex);

            String createSkillRequestsPriorityIndex = "CREATE INDEX IF NOT EXISTS idx_skill_requests_priority ON skill_requests(priority)";
            stmt.execute(createSkillRequestsPriorityIndex);

            log.info("数据库表初始化完成（包含学习系统表：reflections、experiences、skill_requests）");

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

    // ==================== 学习系统相关方法 ====================

    /**
     * 保存反省记录
     */
    public long saveReflection(String sessionId, String reflectionType, String content,
                              String context, String actionItems, Double effectivenessScore) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                INSERT INTO reflections (session_id, reflection_type, content, context, action_items, effectiveness_score)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, reflectionType);
                stmt.setString(3, content);
                stmt.setString(4, context);
                stmt.setString(5, actionItems);
                stmt.setObject(6, effectivenessScore);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    long reflectionId = rs.getLong(1);
                    log.debug("保存反省: reflectionId={}, reflectionType={}", reflectionId, reflectionType);
                    return reflectionId;
                }
            }
        } catch (SQLException e) {
            log.error("保存反省失败", e);
            throw new RuntimeException("保存反省失败", e);
        }
        return -1;
    }

    /**
     * 获取反省记录
     */
    public List<Reflection> getReflections(String sessionId, String reflectionType, int limit) {
        List<Reflection> reflections = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sqlBuilder = new StringBuilder("""
                SELECT id, session_id, reflection_type, content, context,
                       action_items, effectiveness_score, created_at
                FROM reflections
                WHERE 1=1
                """);

            List<Object> params = new ArrayList<>();

            if (sessionId != null && !sessionId.isEmpty()) {
                sqlBuilder.append("AND session_id = ? ");
                params.add(sessionId);
            }

            if (reflectionType != null && !reflectionType.isEmpty()) {
                sqlBuilder.append("AND reflection_type = ? ");
                params.add(reflectionType);
            }

            sqlBuilder.append("ORDER BY created_at DESC LIMIT ?");

            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                int paramIndex = 1;
                for (Object param : params) {
                    stmt.setObject(paramIndex++, param);
                }
                stmt.setInt(paramIndex, limit);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    reflections.add(new Reflection(
                        rs.getLong("id"),
                        rs.getString("session_id"),
                        rs.getString("reflection_type"),
                        rs.getString("content"),
                        rs.getString("context"),
                        rs.getString("action_items"),
                        rs.getObject("effectiveness_score") != null ?
                            rs.getDouble("effectiveness_score") : null,
                        rs.getTimestamp("created_at").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    ));
                }

                log.debug("获取反省: sessionId={}, reflectionType={}, count={}",
                    sessionId, reflectionType, reflections.size());
            }
        } catch (SQLException e) {
            log.error("获取反省失败", e);
            throw new RuntimeException("获取反省失败", e);
        }

        return reflections;
    }

    /**
     * 保存经验条目
     */
    public long saveExperience(String experienceType, String title, String content,
                             String sourceType, String sourceId, Boolean success,
                             Double confidence) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                INSERT INTO experiences (experience_type, title, content, source_type, source_id, success, confidence)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, experienceType);
                stmt.setString(2, title);
                stmt.setString(3, content);
                stmt.setString(4, sourceType);
                stmt.setString(5, sourceId);
                stmt.setBoolean(6, success != null ? success : true);
                stmt.setDouble(7, confidence != null ? confidence : 0.5);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    long experienceId = rs.getLong(1);
                    log.debug("保存经验: experienceId={}, experienceType={}, title={}",
                        experienceId, experienceType, title);
                    return experienceId;
                }
            }
        } catch (SQLException e) {
            log.error("保存经验失败", e);
            throw new RuntimeException("保存经验失败", e);
        }
        return -1;
    }

    /**
     * 搜索经验
     */
    public List<Experience> searchExperiences(String experienceType, String keyword, int limit) {
        List<Experience> experiences = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sqlBuilder = new StringBuilder("""
                SELECT id, experience_type, title, content, source_type, source_id,
                       success, confidence, usage_count, effectiveness_score,
                       created_at, updated_at, last_used_at
                FROM experiences
                WHERE 1=1
                """);

            List<Object> params = new ArrayList<>();

            if (experienceType != null && !experienceType.isEmpty()) {
                sqlBuilder.append("AND experience_type = ? ");
                params.add(experienceType);
            }

            if (keyword != null && !keyword.isEmpty()) {
                sqlBuilder.append("AND (title LIKE ? OR content LIKE ?) ");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
            }

            sqlBuilder.append("ORDER BY confidence DESC, usage_count DESC LIMIT ?");

            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                int paramIndex = 1;
                for (Object param : params) {
                    stmt.setObject(paramIndex++, param);
                }
                stmt.setInt(paramIndex, limit);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    experiences.add(new Experience(
                        rs.getLong("id"),
                        rs.getString("experience_type"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        rs.getBoolean("success"),
                        rs.getDouble("confidence"),
                        rs.getInt("usage_count"),
                        rs.getDouble("effectiveness_score"),
                        rs.getTimestamp("created_at").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime(),
                        rs.getTimestamp("updated_at").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime(),
                        rs.getTimestamp("last_used_at") != null ?
                            rs.getTimestamp("last_used_at").toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime() : null
                    ));
                }

                log.debug("搜索经验: experienceType={}, keyword={}, count={}",
                    experienceType, keyword, experiences.size());
            }
        } catch (SQLException e) {
            log.error("搜索经验失败", e);
            throw new RuntimeException("搜索经验失败", e);
        }

        return experiences;
    }

    /**
     * 更新经验使用统计
     */
    public void updateExperienceUsage(long experienceId, double effectivenessScore) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                UPDATE experiences
                SET usage_count = usage_count + 1,
                    effectiveness_score = (effectiveness_score * usage_count + ?) / (usage_count + 1),
                    last_used_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, effectivenessScore);
                stmt.setLong(2, experienceId);
                stmt.executeUpdate();
                log.debug("更新经验使用: experienceId={}, effectivenessScore={}",
                    experienceId, effectivenessScore);
            }
        } catch (SQLException e) {
            log.error("更新经验使用失败", e);
            throw new RuntimeException("更新经验使用失败", e);
        }
    }

    /**
     * 保存技能需求
     */
    public long saveSkillRequest(Long reflectionId, String skillName, String skillDescription,
                                Integer priority, String status, String metadata) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                INSERT INTO skill_requests (reflection_id, skill_name, skill_description, priority, status, metadata)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setObject(1, reflectionId);
                stmt.setString(2, skillName);
                stmt.setString(3, skillDescription);
                stmt.setInt(4, priority != null ? priority : 5);
                stmt.setString(5, status != null ? status : "pending");
                stmt.setString(6, metadata);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    long requestId = rs.getLong(1);
                    log.debug("保存技能需求: requestId={}, skillName={}", requestId, skillName);
                    return requestId;
                }
            }
        } catch (SQLException e) {
            log.error("保存技能需求失败", e);
            throw new RuntimeException("保存技能需求失败", e);
        }
        return -1;
    }

    /**
     * 获取技能需求列表
     */
    public List<SkillRequest> getSkillRequests(String status, int limit) {
        List<SkillRequest> requests = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sqlBuilder = new StringBuilder("""
                SELECT id, reflection_id, skill_name, skill_description,
                       priority, status, metadata, created_at, updated_at
                FROM skill_requests
                WHERE 1=1
                """);

            if (status != null && !status.isEmpty()) {
                sqlBuilder.append("AND status = ? ");
            }

            sqlBuilder.append("ORDER BY priority ASC, created_at DESC LIMIT ?");

            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                int paramIndex = 1;
                if (status != null && !status.isEmpty()) {
                    stmt.setString(paramIndex++, status);
                }
                stmt.setInt(paramIndex, limit);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    requests.add(new SkillRequest(
                        rs.getLong("id"),
                        rs.getObject("reflection_id") != null ? rs.getLong("reflection_id") : null,
                        rs.getString("skill_name"),
                        rs.getString("skill_description"),
                        rs.getInt("priority"),
                        rs.getString("status"),
                        rs.getString("metadata"),
                        rs.getTimestamp("created_at").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime(),
                        rs.getTimestamp("updated_at").toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    ));
                }

                log.debug("获取技能需求: status={}, count={}", status, requests.size());
            }
        } catch (SQLException e) {
            log.error("获取技能需求失败", e);
            throw new RuntimeException("获取技能需求失败", e);
        }

        return requests;
    }

    /**
     * 更新技能需求状态
     */
    public void updateSkillRequestStatus(long requestId, String status) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE skill_requests SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status);
                stmt.setLong(2, requestId);
                stmt.executeUpdate();
                log.debug("更新技能需求状态: requestId={}, status={}", requestId, status);
            }
        } catch (SQLException e) {
            log.error("更新技能需求状态失败", e);
            throw new RuntimeException("更新技能需求状态失败", e);
        }
    }

    /**
     * 反省记录
     */
    public record Reflection(
            long id,
            String sessionId,
            String reflectionType,
            String content,
            String context,
            String actionItems,
            Double effectivenessScore,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 经验条目记录
     */
    public record Experience(
            long id,
            String experienceType,
            String title,
            String content,
            String sourceType,
            String sourceId,
            boolean success,
            double confidence,
            int usageCount,
            double effectivenessScore,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime lastUsedAt
    ) {
    }

    /**
     * 技能需求记录
     */
    public record SkillRequest(
            long id,
            Long reflectionId,
            String skillName,
            String skillDescription,
            int priority,
            String status,
            String metadata,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}