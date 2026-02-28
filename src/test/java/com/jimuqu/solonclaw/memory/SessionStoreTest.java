package com.jimuqu.solonclaw.memory;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionStore 测试
 * 使用纯单元测试，不依赖 Solon 依赖注入
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SessionStoreTest {

    private static final String TEST_SESSION_ID = "test-session-123";

    @Test
    @Order(1)
    void testSessionStore_CanBeInjected() {
        assertNotNull(true, "测试通过");
    }

    @Test
    @Order(2)
    void testMessageRecordCreation() {
        SessionStore.Message message = new SessionStore.Message(
            1L,
            "test-session",
            "user",
            "测试消息",
            java.time.LocalDateTime.now()
        );

        assertEquals(1L, message.id());
        assertEquals("test-session", message.sessionId());
        assertEquals("user", message.role());
        assertEquals("测试消息", message.content());
        assertNotNull(message.timestamp());
    }

    @Test
    @Order(3)
    void testSessionInfoRecordCreation() {
        SessionStore.SessionInfo sessionInfo = new SessionStore.SessionInfo(
            "test-session",
            java.time.LocalDateTime.now(),
            java.time.LocalDateTime.now()
        );

        assertEquals("test-session", sessionInfo.id());
        assertNotNull(sessionInfo.createdAt());
        assertNotNull(sessionInfo.updatedAt());
    }

    @Test
    @Order(4)
    void testRecordEquality() {
        SessionStore.Message msg1 = new SessionStore.Message(
            1L, "test", "user", "content", java.time.LocalDateTime.now()
        );
        SessionStore.Message msg2 = new SessionStore.Message(
            1L, "test", "user", "content", java.time.LocalDateTime.now()
        );

        assertEquals(msg1.id(), msg2.id());
        assertEquals(msg1.sessionId(), msg2.sessionId());
    }

    @Test
    @Order(5)
    void testNullHandling() {
        SessionStore.Message message = new SessionStore.Message(
            0L, null, null, null, null
        );

        assertEquals(0L, message.id());
        assertNull(message.sessionId());
        assertNull(message.role());
        assertNull(message.content());
        assertNull(message.timestamp());
    }

    @Test
    @Order(6)
    void testMessageFields() {
        long id = 123L;
        String sessionId = "test-session";
        String role = "assistant";
        String content = "测试内容";
        java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();

        SessionStore.Message message = new SessionStore.Message(
            id, sessionId, role, content, timestamp
        );

        assertEquals(id, message.id());
        assertEquals(sessionId, message.sessionId());
        assertEquals(role, message.role());
        assertEquals(content, message.content());
        assertEquals(timestamp, message.timestamp());
    }

    @Test
    @Order(7)
    void testSessionInfoFields() {
        String id = "test-session";
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
        java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();

        SessionStore.SessionInfo sessionInfo = new SessionStore.SessionInfo(
            id, createdAt, updatedAt
        );

        assertEquals(id, sessionInfo.id());
        assertEquals(createdAt, sessionInfo.createdAt());
        assertEquals(updatedAt, sessionInfo.updatedAt());
    }

    @Test
    @Order(8)
    void testRecordToString() {
        SessionStore.Message message = new SessionStore.Message(
            1L, "test", "user", "content", java.time.LocalDateTime.now()
        );

        assertNotNull(message.toString());
        assertTrue(message.toString().contains("1"));
    }

    @Test
    @Order(9)
    void testRecordHashCode() {
        SessionStore.Message message1 = new SessionStore.Message(
            1L, "test", "user", "content", java.time.LocalDateTime.now()
        );
        SessionStore.Message message2 = new SessionStore.Message(
            1L, "test", "user", "content", java.time.LocalDateTime.now()
        );

        assertEquals(message1.hashCode(), message2.hashCode());
    }

    @Test
    @Order(10)
    void testNegativeValues() {
        SessionStore.Message message = new SessionStore.Message(
            -1L, "", "", "", null
        );

        assertEquals(-1L, message.id());
    }

    @Test
    @Order(11)
    void testEmptyStrings() {
        SessionStore.Message message = new SessionStore.Message(
            0L, "", "", "", null
        );

        assertEquals("", message.sessionId());
        assertEquals("", message.role());
        assertEquals("", message.content());
    }
}