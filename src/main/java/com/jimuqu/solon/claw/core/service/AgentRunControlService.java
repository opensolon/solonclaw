package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.AgentRunStopResult;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.RunBusyDecision;
import java.util.Map;

/** Controls active Agent runs without exposing engine implementation details. */
public interface AgentRunControlService {
    /** Request cancellation of the active run for a gateway source. */
    AgentRunStopResult stop(String sourceKey);

    /** Whether the gateway source currently has an active run. */
    boolean isRunning(String sourceKey);

    /** Whether any source currently has an active run. */
    default boolean hasRunningRuns() {
        return false;
    }

    /** Last time any run finished. A zero value means no completed run is known. */
    default long lastRunFinishedAt() {
        return 0L;
    }

    default RunBusyDecision coordinateIncoming(
            String sourceKey, String sessionId, GatewayMessage message) throws Exception {
        return RunBusyDecision.runNow("queue");
    }

    default Map<String, Object> controlRun(String runId, String command, Map<String, Object> payload)
            throws Exception {
        throw new UnsupportedOperationException("run control unavailable");
    }

    default String consumeSteerInstruction(String runId) {
        return null;
    }

    default void onRunFinished(
            String sourceKey,
            String sessionId,
            java.util.function.Function<GatewayMessage, com.jimuqu.solon.claw.core.model.GatewayReply>
                    runner) {}
}
