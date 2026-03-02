package com.jimuqu.solonclaw.context;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextBuilder 接口测试
 *
 * @author SolonClaw
 */
interface ContextBuilderTest {

    @Test
    default void testBuild() {
        ContextBuilder builder = createBuilder();

        Context context = builder.build("test-session", "test-message");

        assertNotNull(context);
        assertEquals("test-session", context.getMetadata("sessionId"));
    }

    @Test
    default void testBuildWithOptions() {
        ContextBuilder builder = createBuilder();

        Map<String, Object> options = Map.of("option1", "value1");
        Context context = builder.build("test-session", "test-message", options);

        assertNotNull(context);
    }

    ContextBuilder createBuilder();
}