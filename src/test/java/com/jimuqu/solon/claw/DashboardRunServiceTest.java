package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.QueuedRunMessage;
import com.jimuqu.solon.claw.core.model.RunRecoveryRecord;
import com.jimuqu.solon.claw.core.model.RunControlCommand;
import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.web.DashboardRunService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class DashboardRunServiceTest {
    @Test
    void shouldReturnEventsWhenMetadataJsonIsBroken() throws Exception {
        FakeAgentRunRepository repository = new FakeAgentRunRepository();
        AgentRunEventRecord event = new AgentRunEventRecord();
        event.setEventId("event-1");
        event.setRunId("run-1");
        event.setSessionId("session-1");
        event.setEventType("attempt.error");
        event.setMetadataJson("{\"preview\":\"unterminated");
        repository.events.add(event);

        DashboardRunService service = new DashboardRunService(repository);
        Map<String, Object> response = service.events("run-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) response.get("events");
        assertThat(events).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) events.get(0).get("metadata");
        assertThat(metadata.get("parse_error")).isEqualTo(true);
        assertThat(metadata.get("field")).isEqualTo("metadata");
        assertThat(metadata.get("raw")).isEqualTo("{\"preview\":\"unterminated");
    }

    private static class FakeAgentRunRepository implements AgentRunRepository {
        private final List<AgentRunEventRecord> events = new ArrayList<AgentRunEventRecord>();

        @Override
        public void saveRun(AgentRunRecord record) {}

        @Override
        public AgentRunRecord findRun(String runId) {
            return null;
        }

        @Override
        public List<AgentRunRecord> listBySession(String sessionId, int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentRunRecord> listRecoverable(int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentRunRecord> listActiveBefore(long beforeEpochMillis, int limit) {
            return Collections.emptyList();
        }

        @Override
        public void markStaleRuns(long beforeEpochMillis, long now) {}

        @Override
        public List<AgentRunRecord> listActiveBySource(String sourceKey, int limit) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentRunRecord> searchRuns(
                String sourceKey,
                String sessionId,
                String runId,
                String query,
                long timeFrom,
                long timeTo,
                int limit) {
            return Collections.emptyList();
        }

        @Override
        public void appendEvent(AgentRunEventRecord event) {
            events.add(event);
        }

        @Override
        public List<AgentRunEventRecord> listEvents(String runId) {
            return events;
        }

        @Override
        public void saveRunControlCommand(RunControlCommand command) {}

        @Override
        public List<RunControlCommand> listRunControlCommands(String runId) {
            return Collections.emptyList();
        }

        @Override
        public RunControlCommand findLatestPendingCommand(String runId, String command) {
            return null;
        }

        @Override
        public void markRunControlCommandHandled(String commandId, String status, long handledAt) {}

        @Override
        public void saveQueuedMessage(QueuedRunMessage message) {}

        @Override
        public QueuedRunMessage findNextQueuedMessage(String sourceKey, String sessionId) {
            return null;
        }

        @Override
        public void markQueuedMessage(String queueId, String status, long timestamp, String error) {}

        @Override
        public void saveToolCall(ToolCallRecord record) {}

        @Override
        public List<ToolCallRecord> listToolCalls(String runId) {
            return Collections.emptyList();
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
                int limit) {
            return Collections.emptyList();
        }

        @Override
        public void saveSubagentRun(SubagentRunRecord record) {}

        @Override
        public List<SubagentRunRecord> listSubagents(String parentRunId) {
            return Collections.emptyList();
        }

        @Override
        public void saveRecovery(RunRecoveryRecord record) {}

        @Override
        public List<RunRecoveryRecord> listRecoveries(String runId) {
            return Collections.emptyList();
        }

        @Override
        public void pruneBefore(long beforeEpochMillis) {}
    }
}
