package com.jimuqu.claw.agent.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VisibleProgressAccumulatorTest {
    @Test
    void ignoresThinkingChunksFromVisibleProgress() {
        VisibleProgressAccumulator accumulator = new VisibleProgressAccumulator();

        assertNull(accumulator.append("先想想", true, false));
        assertEquals("你好", accumulator.append("你好", false, false));
        assertEquals("你好，世界", accumulator.append("，世界", false, false));
    }

    @Test
    void mergesIncrementalChunksIntoCumulativeVisibleText() {
        VisibleProgressAccumulator accumulator = new VisibleProgressAccumulator();

        assertEquals("没问题", accumulator.append("没问题", false, false));
        assertEquals("没问题，以后我就用 Markdown", accumulator.append("，以后我就用 Markdown", false, false));
        assertEquals("没问题，以后我就用 Markdown 给你回消息。", accumulator.append(" 给你回消息。", false, false));
        assertNull(accumulator.append("没问题，以后我就用 Markdown 给你回消息。", false, false));
    }

    @Test
    void ignoresToolCallChunksDuringStreaming() {
        VisibleProgressAccumulator accumulator = new VisibleProgressAccumulator();

        assertNull(accumulator.append("tool_call_payload", false, true));
        assertEquals("搞定。", accumulator.append("搞定。", false, false));
    }
}
