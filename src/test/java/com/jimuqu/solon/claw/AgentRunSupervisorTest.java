package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunOutcome;
import com.jimuqu.solon.claw.core.model.ContextBudgetDecision;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.ContextBudgetService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.ConversationEventSink;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.engine.AgentRunSupervisor;
import com.jimuqu.solon.claw.gateway.feedback.ConversationFeedbackSink;
import com.jimuqu.solon.claw.storage.repository.SqliteAgentRunRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.storage.repository.SqliteSessionRepository;
import com.jimuqu.solon.claw.support.LlmProviderService;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.AssistantMessage;

public class AgentRunSupervisorTest {
    @Test
    void shouldFallbackAndPersistRunEvents() throws Exception {
        Fixture fixture = fixture();
        RecordingGateway gateway = new RecordingGateway();
        AgentRunSupervisor supervisor =
                supervisor(fixture, gateway, noCompressionBudget(), noCompressionService());
        SessionRecord session = fixture.sessionRepository.bindNewSession("MEMORY:room:user");

        AgentRunOutcome outcome =
                supervisor.run(
                        session,
                        "system",
                        "hello",
                        Collections.emptyList(),
                        ConversationFeedbackSink.noop(),
                        ConversationEventSink.noop(),
                        false);

        assertThat(outcome.getFinalReply()).isEqualTo("backup ok");
        assertThat(outcome.getRunRecord().getStatus()).isEqualTo("success");
        assertThat(outcome.getRunRecord().getProvider()).isEqualTo("backup");
        assertThat(gateway.attempts)
                .containsExactly("primary:gpt-5-mini", "backup:claude-sonnet-4");

        List<AgentRunEventRecord> events =
                fixture.agentRunRepository.listEvents(outcome.getRunRecord().getRunId());
        assertThat(eventTypes(events))
                .contains(
                        "run.start",
                        "attempt.start",
                        "attempt.error",
                        "fallback",
                        "attempt.success",
                        "run.success");
    }

    @Test
    void shouldRecordCompressionDecisionBeforeAttempt() throws Exception {
        Fixture fixture = fixture();
        RecordingGateway gateway = new RecordingGateway();
        CountingCompressionService compressionService = new CountingCompressionService();
        AgentRunSupervisor supervisor =
                supervisor(fixture, gateway, compressingBudget(), compressionService);
        SessionRecord session = fixture.sessionRepository.bindNewSession("MEMORY:room:user");

        AgentRunOutcome outcome =
                supervisor.run(
                        session,
                        "system",
                        "hello",
                        Collections.emptyList(),
                        ConversationFeedbackSink.noop(),
                        ConversationEventSink.noop(),
                        false);

        assertThat(outcome.getRunRecord().getStatus()).isEqualTo("success");
        assertThat(compressionService.compressCount).isEqualTo(2);
        List<AgentRunEventRecord> events =
                fixture.agentRunRepository.listEvents(outcome.getRunRecord().getRunId());
        assertThat(eventTypes(events)).contains("compression.unchanged");
    }

    private static AgentRunSupervisor supervisor(
            Fixture fixture,
            LlmGateway gateway,
            ContextBudgetService budgetService,
            ContextCompressionService compressionService) {
        return new AgentRunSupervisor(
                fixture.config,
                fixture.sessionRepository,
                fixture.agentRunRepository,
                compressionService,
                budgetService,
                gateway,
                new LlmProviderService(fixture.config));
    }

    private static Fixture fixture() throws Exception {
        AppConfig config = new AppConfig();
        File runtimeHome = Files.createTempDirectory("solon-claw-supervisor").toFile();
        config.getRuntime()
                .setStateDb(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        config.getTrace().setMaxAttempts(1);

        AppConfig.ProviderConfig primary = new AppConfig.ProviderConfig();
        primary.setName("Primary");
        primary.setBaseUrl("https://api.openai.com");
        primary.setApiKey("primary-key");
        primary.setDefaultModel("gpt-5-mini");
        primary.setDialect("openai-responses");
        config.getProviders().put("primary", primary);

        AppConfig.ProviderConfig backup = new AppConfig.ProviderConfig();
        backup.setName("Backup");
        backup.setBaseUrl("https://api.anthropic.com");
        backup.setApiKey("backup-key");
        backup.setDefaultModel("claude-sonnet-4");
        backup.setDialect("anthropic");
        config.getProviders().put("backup", backup);

        config.getModel().setProviderKey("primary");
        AppConfig.FallbackProviderConfig fallback = new AppConfig.FallbackProviderConfig();
        fallback.setProvider("backup");
        config.getFallbackProviders().add(fallback);

        SqliteDatabase database = new SqliteDatabase(config);
        Fixture fixture = new Fixture();
        fixture.config = config;
        fixture.sessionRepository = new SqliteSessionRepository(database);
        fixture.agentRunRepository = new SqliteAgentRunRepository(database);
        return fixture;
    }

    private static ContextBudgetService noCompressionBudget() {
        return new ContextBudgetService() {
            @Override
            public ContextBudgetDecision decide(
                    SessionRecord session,
                    String systemPrompt,
                    String userMessage,
                    AppConfig.LlmConfig resolved) {
                ContextBudgetDecision decision = new ContextBudgetDecision();
                decision.setShouldCompress(false);
                decision.setReason("within budget");
                decision.setEstimatedTokens(100);
                decision.setThresholdTokens(1000);
                return decision;
            }
        };
    }

    private static ContextBudgetService compressingBudget() {
        return new ContextBudgetService() {
            @Override
            public ContextBudgetDecision decide(
                    SessionRecord session,
                    String systemPrompt,
                    String userMessage,
                    AppConfig.LlmConfig resolved) {
                ContextBudgetDecision decision = new ContextBudgetDecision();
                decision.setShouldCompress(true);
                decision.setReason("over budget");
                decision.setEstimatedTokens(2000);
                decision.setThresholdTokens(1000);
                return decision;
            }
        };
    }

    private static ContextCompressionService noCompressionService() {
        return new CountingCompressionService();
    }

    private static List<String> eventTypes(List<AgentRunEventRecord> events) {
        List<String> types = new ArrayList<String>();
        for (AgentRunEventRecord event : events) {
            types.add(event.getEventType());
        }
        return types;
    }

    private static class Fixture {
        private AppConfig config;
        private SessionRepository sessionRepository;
        private AgentRunRepository agentRunRepository;
    }

    private static class RecordingGateway implements LlmGateway {
        private final List<String> attempts = new ArrayList<String>();

        @Override
        public LlmResult chat(
                SessionRecord session,
                String systemPrompt,
                String userMessage,
                List<Object> toolObjects) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmResult resume(
                SessionRecord session, String systemPrompt, List<Object> toolObjects) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmResult executeOnce(
                SessionRecord session,
                String systemPrompt,
                String userMessage,
                List<Object> toolObjects,
                ConversationFeedbackSink feedbackSink,
                ConversationEventSink eventSink,
                boolean resume,
                AppConfig.LlmConfig resolved,
                com.jimuqu.solon.claw.core.model.AgentRunContext runContext) {
            attempts.add(resolved.getProvider() + ":" + resolved.getModel());
            if ("primary".equals(resolved.getProvider())) {
                throw new IllegalStateException("HTTP 401 unauthorized");
            }
            LlmResult result = new LlmResult();
            result.setProvider(resolved.getProvider());
            result.setModel(resolved.getModel());
            result.setRawResponse("ok");
            result.setAssistantMessage(new AssistantMessage("backup ok"));
            result.setNdjson(session.getNdjson());
            return result;
        }
    }

    private static class CountingCompressionService implements ContextCompressionService {
        private int compressCount;

        @Override
        public SessionRecord compressIfNeeded(
                SessionRecord session, String systemPrompt, String userMessage) {
            return session;
        }

        @Override
        public SessionRecord compressNow(SessionRecord session, String systemPrompt) {
            compressCount++;
            return session;
        }

        @Override
        public SessionRecord compressNow(SessionRecord session, String systemPrompt, String focus) {
            compressCount++;
            return session;
        }
    }
}
