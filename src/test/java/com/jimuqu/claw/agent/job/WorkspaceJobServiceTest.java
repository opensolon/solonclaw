package com.jimuqu.claw.agent.job;

import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.SystemEventPolicy;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.IsolatedAgentRunService;
import com.jimuqu.claw.agent.runtime.impl.SystemEventRunner;
import com.jimuqu.claw.agent.runtime.support.AgentTurnRequest;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.core.Lifecycle;
import org.noear.solon.core.util.RankEntity;
import org.noear.solon.scheduling.ScheduledException;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.noear.solon.scheduling.scheduled.JobHandler;
import org.noear.solon.scheduling.scheduled.JobHolder;
import org.noear.solon.scheduling.scheduled.JobInterceptor;
import org.noear.solon.scheduling.scheduled.manager.IJobManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceJobServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void addSystemJobPersistsNewPayloadFields() {
        TestContext ctx = new TestContext(tempDir);

        JobDefinition definition = ctx.workspaceJobService.addSystemJob(
                "drink-water",
                "fixed_rate",
                "60000",
                "提醒我喝水",
                0L,
                "Asia/Shanghai",
                JobWakeMode.NOW
        );

        assertEquals(JobPayloadKind.SYSTEM_EVENT, definition.getPayloadKind());
        assertEquals(JobSessionTarget.MAIN, definition.getSessionTarget());
        assertEquals(JobWakeMode.NOW, definition.getWakeMode());
        assertEquals(JobDeliveryMode.NONE, definition.getDeliveryMode());
        assertEquals("提醒我喝水", definition.getSystemEventText());
        assertEquals(ctx.boundSessionKey, definition.getBoundSessionKey());
        assertEquals(ctx.boundReplyTarget.getConversationId(), definition.getBoundReplyTarget().getConversationId());
        assertNotNull(ctx.jobStoreService.get("drink-water"));
    }

    @Test
    void addAgentJobPersistsAgentTurnFields() {
        TestContext ctx = new TestContext(tempDir);
        AgentTurnSpec spec = new AgentTurnSpec();
        spec.setMessage("检查服务器状态");
        spec.setModel("qwen");
        spec.setThinking("medium");
        spec.setTimeoutSeconds(120);
        spec.setLightContext(true);

        JobDefinition definition = ctx.workspaceJobService.addAgentJob(
                "check-server",
                "fixed_rate",
                "60000",
                spec,
                0L,
                "Asia/Shanghai",
                JobDeliveryMode.LAST_ROUTE
        );

        assertEquals(JobPayloadKind.AGENT_TURN, definition.getPayloadKind());
        assertEquals(JobSessionTarget.ISOLATED, definition.getSessionTarget());
        assertEquals(JobDeliveryMode.LAST_ROUTE, definition.getDeliveryMode());
        assertEquals("检查服务器状态", definition.getAgentTurn().getMessage());
        assertEquals("qwen", definition.getAgentTurn().getModel());
        assertTrue(definition.getAgentTurn().isLightContext());
    }

    @Test
    void invalidSystemEventAndAgentTurnCombinationsAreRejected() {
        TestContext ctx = new TestContext(tempDir);

        JobDefinition invalidSystem = new JobDefinition();
        invalidSystem.setName("bad-system");
        invalidSystem.setMode("fixed_rate");
        invalidSystem.setScheduleValue("60000");
        invalidSystem.setPayloadKind(JobPayloadKind.SYSTEM_EVENT);
        invalidSystem.setSessionTarget(JobSessionTarget.ISOLATED);
        invalidSystem.setWakeMode(JobWakeMode.NOW);
        invalidSystem.setDeliveryMode(JobDeliveryMode.NONE);
        invalidSystem.setBoundSessionKey(ctx.boundSessionKey);
        invalidSystem.setBoundReplyTarget(ctx.boundReplyTarget);
        invalidSystem.setSystemEventText("bad");
        ctx.jobStoreService.save(invalidSystem);

        JobDefinition invalidAgent = new JobDefinition();
        invalidAgent.setName("bad-agent");
        invalidAgent.setMode("fixed_rate");
        invalidAgent.setScheduleValue("60000");
        invalidAgent.setPayloadKind(JobPayloadKind.AGENT_TURN);
        invalidAgent.setSessionTarget(JobSessionTarget.MAIN);
        invalidAgent.setDeliveryMode(JobDeliveryMode.BOUND_REPLY_TARGET);
        invalidAgent.setBoundSessionKey(ctx.boundSessionKey);
        invalidAgent.setBoundReplyTarget(ctx.boundReplyTarget);
        AgentTurnSpec spec = new AgentTurnSpec();
        spec.setMessage("bad");
        invalidAgent.setAgentTurn(spec);
        ctx.jobStoreService.save(invalidAgent);

        assertThrows(IllegalArgumentException.class, () -> ctx.workspaceJobService.startJob("bad-system"));
        assertThrows(IllegalArgumentException.class, () -> ctx.workspaceJobService.startJob("bad-agent"));
    }

    @Test
    void schedulerDispatchesSystemEventAndAgentTurnToDifferentRunners() throws Exception {
        TestContext ctx = new TestContext(tempDir);
        AgentTurnSpec spec = new AgentTurnSpec();
        spec.setMessage("收集日志");

        ctx.workspaceJobService.addSystemJob(
                "system-job",
                "fixed_rate",
                "60000",
                "提醒我站起来活动一下",
                0L,
                "Asia/Shanghai",
                JobWakeMode.NOW
        );
        ctx.workspaceJobService.addAgentJob(
                "agent-job",
                "fixed_rate",
                "60000",
                spec,
                0L,
                "Asia/Shanghai",
                JobDeliveryMode.BOUND_REPLY_TARGET
        );

        ctx.jobManager.trigger("system-job");
        ctx.jobManager.trigger("agent-job");

        assertNotNull(ctx.systemEventRunner.lastRequest.get());
        assertEquals(RuntimeSourceKind.JOB_SYSTEM_EVENT, ctx.systemEventRunner.lastRequest.get().getSourceKind());
        assertEquals(SystemEventPolicy.USER_VISIBLE_OPTIONAL, ctx.systemEventRunner.lastRequest.get().getPolicy());
        assertEquals("提醒我站起来活动一下", ctx.systemEventRunner.lastRequest.get().getContent());

        assertNotNull(ctx.isolatedAgentRunService.lastRequest.get());
        assertEquals(RuntimeSourceKind.JOB_AGENT_TURN, ctx.isolatedAgentRunService.lastRequest.get().getSourceKind());
        assertEquals("收集日志", ctx.isolatedAgentRunService.lastRequest.get().getAgentTurn().getMessage());
        assertEquals(JobDeliveryMode.BOUND_REPLY_TARGET, ctx.isolatedAgentRunService.lastRequest.get().getDeliveryMode());
    }

    private static final class TestContext {
        private final ReplyTarget boundReplyTarget;
        private final String boundSessionKey;
        private final TestJobManager jobManager;
        private final JobStoreService jobStoreService;
        private final RuntimeStoreService runtimeStoreService;
        private final CapturingSystemEventRunner systemEventRunner;
        private final CapturingIsolatedAgentRunService isolatedAgentRunService;
        private final WorkspaceJobService workspaceJobService;
        private final ConversationScheduler scheduler;

        private TestContext(Path tempDir) {
            SolonClawProperties properties = new SolonClawProperties();
            AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
            this.jobManager = new TestJobManager();
            this.jobStoreService = new JobStoreService(workspaceService);
            this.runtimeStoreService = new RuntimeStoreService(tempDir.resolve("runtime").toFile());
            this.scheduler = new ConversationScheduler(1);
            this.boundReplyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.PRIVATE, "cid", "uid");
            this.boundSessionKey = "dingtalk:private:cid";
            this.runtimeStoreService.rememberReplyTarget(boundSessionKey, boundReplyTarget);

            ConversationAgent noopAgent = (request, progressConsumer) -> "noop";
            ChannelRegistry registry = new ChannelRegistry();
            this.systemEventRunner = new CapturingSystemEventRunner(
                    noopAgent,
                    runtimeStoreService,
                    scheduler,
                    registry,
                    properties
            );
            this.isolatedAgentRunService = new CapturingIsolatedAgentRunService(
                    noopAgent,
                    runtimeStoreService,
                    scheduler,
                    registry,
                    properties
            );
            this.workspaceJobService = new WorkspaceJobService(
                    jobManager,
                    jobStoreService,
                    runtimeStoreService,
                    systemEventRunner,
                    isolatedAgentRunService,
                    properties
            );
        }
    }

    private static class CapturingSystemEventRunner extends SystemEventRunner {
        private final AtomicReference<SystemEventRequest> lastRequest = new AtomicReference<SystemEventRequest>();

        private CapturingSystemEventRunner(
                ConversationAgent conversationAgent,
                RuntimeStoreService runtimeStoreService,
                ConversationScheduler conversationScheduler,
                ChannelRegistry channelRegistry,
                SolonClawProperties properties
        ) {
            super(
                    conversationAgent,
                    runtimeStoreService,
                    conversationScheduler,
                    channelRegistry,
                    properties
            );
        }

        @Override
        public String submit(SystemEventRequest request) {
            lastRequest.set(request);
            return "captured-system";
        }
    }

    private static class CapturingIsolatedAgentRunService extends IsolatedAgentRunService {
        private final AtomicReference<AgentTurnRequest> lastRequest = new AtomicReference<AgentTurnRequest>();

        private CapturingIsolatedAgentRunService(
                ConversationAgent conversationAgent,
                RuntimeStoreService runtimeStoreService,
                ConversationScheduler conversationScheduler,
                ChannelRegistry channelRegistry,
                SolonClawProperties properties
        ) {
            super(
                    conversationAgent,
                    runtimeStoreService,
                    conversationScheduler,
                    channelRegistry,
                    properties
            );
        }

        @Override
        public String submit(AgentTurnRequest request) {
            lastRequest.set(request);
            return "captured-agent";
        }
    }

    private static class TestJobManager implements IJobManager {
        private final Map<String, JobHolder> jobs = new LinkedHashMap<String, JobHolder>();

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void addJobInterceptor(int index, JobInterceptor interceptor) {
        }

        @Override
        public boolean hasJobInterceptor() {
            return false;
        }

        @Override
        public List<RankEntity<JobInterceptor>> getJobInterceptors() {
            return new ArrayList<RankEntity<JobInterceptor>>();
        }

        @Override
        public JobHolder jobAdd(String name, Scheduled scheduled, JobHandler handler) {
            JobHolder holder = new JobHolder(this, name, scheduled, handler);
            jobs.put(name, holder);
            return holder;
        }

        @Override
        public JobHolder jobAdd(String name, Scheduled scheduled, JobHandler handler, Map<String, String> data) {
            JobHolder holder = jobAdd(name, scheduled, handler);
            holder.setData(data);
            return holder;
        }

        @Override
        public boolean jobExists(String name) {
            return jobs.containsKey(name);
        }

        @Override
        public JobHolder jobGet(String name) {
            return jobs.get(name);
        }

        @Override
        public Map<String, JobHolder> jobGetAll() {
            return jobs;
        }

        @Override
        public void jobRemove(String name) {
            jobs.remove(name);
        }

        @Override
        public void jobStart(String name, Map<String, String> data) throws ScheduledException {
        }

        @Override
        public void jobStop(String name) throws ScheduledException {
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        private void trigger(String name) throws Exception {
            JobHolder holder = jobs.get(name);
            if (holder == null) {
                throw new IllegalArgumentException("job 不存在: " + name);
            }
            try {
                holder.getHandler().handle(null);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }
}
