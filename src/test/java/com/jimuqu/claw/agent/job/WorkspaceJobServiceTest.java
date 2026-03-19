package com.jimuqu.claw.agent.job;

import com.jimuqu.claw.agent.model.ChannelType;
import com.jimuqu.claw.agent.model.ConversationType;
import com.jimuqu.claw.agent.model.ReplyTarget;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceJobServiceTest {
    @Test
    void onceDelayUsesScheduleValueAsInitialDelayWhenInitialDelayIsZero(@TempDir Path tempDir) {
        Fixture fixture = new Fixture(tempDir);

        fixture.service.addJob(
                "weather-once",
                "once_delay",
                "60000",
                "1分钟后再次汇报重庆天气",
                0L,
                ""
        );

        JobHolder holder = fixture.jobManager.jobGet("weather-once");
        assertNotNull(holder);
        assertEquals(60000L, holder.getScheduled().initialDelay());
        assertEquals(60000L, holder.getScheduled().fixedDelay());
    }

    @Test
    void triggeredJobWrapsPromptAsImmediateExecutionInstruction(@TempDir Path tempDir) throws Throwable {
        Fixture fixture = new Fixture(tempDir);
        List<String> dispatched = new ArrayList<>();
        fixture.service.setJobDispatcher((sessionKey, replyTarget, prompt) -> {
            dispatched.add(sessionKey);
            dispatched.add(replyTarget.getConversationId());
            dispatched.add(prompt);
            return "run-1";
        });

        fixture.service.addJob(
                "weather-once",
                "once_delay",
                "60000",
                "1分钟后再次汇报重庆天气",
                0L,
                ""
        );

        fixture.jobManager.trigger("weather-once");

        assertEquals(3, dispatched.size());
        assertEquals("dingtalk:group:group-1", dispatched.get(0));
        assertEquals("group-1", dispatched.get(1));
        assertTrue(dispatched.get(2).contains("[定时任务触发]"));
        assertTrue(dispatched.get(2).contains("现在立刻完成下面的任务"));
        assertTrue(dispatched.get(2).contains("不要把这条消息再理解成“稍后执行”"));
        assertTrue(dispatched.get(2).contains("1分钟后再次汇报重庆天气"));
        assertTrue(!fixture.jobManager.jobExists("weather-once"));
    }

    private static final class Fixture {
        private final FakeJobManager jobManager = new FakeJobManager();
        private final WorkspaceJobService service;

        private Fixture(Path tempDir) {
            AgentWorkspaceService workspaceService = new AgentWorkspaceService(tempDir.toString());
            JobStoreService jobStoreService = new JobStoreService(workspaceService);
            RuntimeStoreService runtimeStoreService = new RuntimeStoreService(workspaceService.fileInWorkspace("runtime"));
            runtimeStoreService.rememberReplyTarget(
                    "dingtalk:group:group-1",
                    new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-1", "user-1")
            );
            this.service = new WorkspaceJobService(jobManager, jobStoreService, runtimeStoreService);
            this.service.setJobDispatcher((sessionKey, replyTarget, prompt) -> "run-0");
        }
    }

    private static final class FakeJobManager implements IJobManager {
        private final Map<String, JobHolder> jobs = new LinkedHashMap<>();

        @Override
        public void addJobInterceptor(int index, JobInterceptor interceptor) {
        }

        @Override
        public boolean hasJobInterceptor() {
            return false;
        }

        @Override
        public List<org.noear.solon.core.util.RankEntity<JobInterceptor>> getJobInterceptors() {
            return new ArrayList<>();
        }

        @Override
        public JobHolder jobAdd(String name, Scheduled scheduled, JobHandler handler) {
            return jobAdd(name, scheduled, handler, null);
        }

        @Override
        public JobHolder jobAdd(String name, Scheduled scheduled, JobHandler handler, Map<String, String> data) {
            JobHolder holder = new JobHolder(this, name, scheduled, handler);
            holder.setData(data);
            jobs.put(name, holder);
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
        public void jobStart(String name, Map<String, String> data) {
        }

        @Override
        public void jobStop(String name) {
        }

        @Override
        public boolean isStarted() {
            return false;
        }

        @Override
        public void start() {
        }

        public void trigger(String name) throws Throwable {
            JobHolder holder = jobs.get(name);
            if (holder != null) {
                holder.getHandler().handle(null);
            }
        }
    }
}
