package com.jimuqu.solonclaw.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Trace ID 上下文管理
 * <p>
 * 用于追踪工具调用链，提供审计和监控能力
 *
 * @author SolonClaw
 */
public class TraceContext {

    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 生成新的 Trace ID
     *
     * @return Trace ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 设置 Trace ID 到 MDC
     *
     * @param traceId Trace ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 获取当前 Trace ID
     *
     * @return Trace ID，如果没有返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 清除 Trace ID
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 执行带 Trace ID 的操作
     *
     * @param traceId Trace ID
     * @param runnable 要执行的操作
     */
    public static void executeWithTraceId(String traceId, Runnable runnable) {
        try {
            setTraceId(traceId);
            runnable.run();
        } finally {
            clear();
        }
    }
}
