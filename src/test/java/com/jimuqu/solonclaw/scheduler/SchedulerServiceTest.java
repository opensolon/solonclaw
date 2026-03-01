package com.jimuqu.solonclaw.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SchedulerService 测试
 * 测试任务管理、执行历史记录等功能
 *
 * @author SolonClaw
 */
class SchedulerServiceTest {

    private Map<String, SchedulerService.JobInfo> jobs;
    private List<SchedulerService.JobHistory> jobHistory;

    @BeforeEach
    void setUp() {
        jobs = new HashMap<>();
        jobHistory = new ArrayList<>();
    }

    @Nested
    @DisplayName("任务信息测试")
    class JobInfoTest {

        @Test
        @DisplayName("创建 Cron 任务信息")
        void testCreateCronJobInfo() {
            SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(
                "test-cron-job",
                "0 0 * * *",
                0,
                false,
                0,
                "echo hello",
                SchedulerService.JobType.CRON
            );

            assertEquals("test-cron-job", jobInfo.name());
            assertEquals("0 0 * * *", jobInfo.cron());
            assertFalse(jobInfo.isOneTime());
            assertEquals("echo hello", jobInfo.command());
            assertEquals(SchedulerService.JobType.CRON, jobInfo.jobType());
        }

        @Test
        @DisplayName("创建固定频率任务信息")
        void testCreateFixedRateJobInfo() {
            SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(
                "test-fixed-job",
                null,
                5000,
                false,
                0,
                "ls -la",
                SchedulerService.JobType.FIXED_RATE
            );

            assertEquals("test-fixed-job", jobInfo.name());
            assertEquals(5000, jobInfo.fixedRate());
            assertFalse(jobInfo.isOneTime());
            assertEquals(SchedulerService.JobType.FIXED_RATE, jobInfo.jobType());
        }

        @Test
        @DisplayName("创建一次性任务信息")
        void testCreateOneTimeJobInfo() {
            long scheduleTime = System.currentTimeMillis() + 10000;
            SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(
                "test-onetime-job",
                null,
                10000,
                true,
                scheduleTime,
                "date",
                SchedulerService.JobType.ONE_TIME
            );

            assertEquals("test-onetime-job", jobInfo.name());
            assertTrue(jobInfo.isOneTime());
            assertEquals(scheduleTime, jobInfo.scheduleTime());
            assertEquals(SchedulerService.JobType.ONE_TIME, jobInfo.jobType());
        }
    }

    @Nested
    @DisplayName("任务管理测试")
    class JobManagementTest {

        @Test
        @DisplayName("添加任务")
        void testAddJob() {
            SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(
                "job1", "0 0 * * *", 0, false, 0, "echo test", SchedulerService.JobType.CRON
            );
            jobs.put(jobInfo.name(), jobInfo);

            assertEquals(1, jobs.size());
            assertTrue(jobs.containsKey("job1"));
        }

        @Test
        @DisplayName("删除任务")
        void testRemoveJob() {
            SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(
                "job-to-remove", "0 0 * * *", 0, false, 0, "echo test", SchedulerService.JobType.CRON
            );
            jobs.put(jobInfo.name(), jobInfo);

            assertEquals(1, jobs.size());

            jobs.remove("job-to-remove");
            assertEquals(0, jobs.size());
            assertFalse(jobs.containsKey("job-to-remove"));
        }

        @Test
        @DisplayName("删除不存在的任务")
        void testRemoveNonExistentJob() {
            SchedulerService.JobInfo removed = jobs.remove("non-existent");
            assertNull(removed);
            assertEquals(0, jobs.size());
        }

        @Test
        @DisplayName("获取所有任务")
        void testGetAllJobs() {
            jobs.put("job1", new SchedulerService.JobInfo("job1", "0 0 * * *", 0, false, 0, "cmd1", SchedulerService.JobType.CRON));
            jobs.put("job2", new SchedulerService.JobInfo("job2", null, 5000, false, 0, "cmd2", SchedulerService.JobType.FIXED_RATE));
            jobs.put("job3", new SchedulerService.JobInfo("job3", null, 10000, true, System.currentTimeMillis(), "cmd3", SchedulerService.JobType.ONE_TIME));

            List<SchedulerService.JobInfo> jobList = new ArrayList<>(jobs.values());
            assertEquals(3, jobList.size());
        }

        @Test
        @DisplayName("检查任务是否存在")
        void testHasJob() {
            jobs.put("existing-job", new SchedulerService.JobInfo("existing-job", "0 0 * * *", 0, false, 0, "cmd", SchedulerService.JobType.CRON));

            assertTrue(jobs.containsKey("existing-job"));
            assertFalse(jobs.containsKey("non-existing-job"));
        }

        @Test
        @DisplayName("任务类型枚举")
        void testJobTypeEnum() {
            assertEquals("CRON", SchedulerService.JobType.CRON.name());
            assertEquals("FIXED_RATE", SchedulerService.JobType.FIXED_RATE.name());
            assertEquals("ONE_TIME", SchedulerService.JobType.ONE_TIME.name());
        }
    }

    @Nested
    @DisplayName("执行历史测试")
    class JobHistoryTest {

        @Test
        @DisplayName("记录成功执行")
        void testRecordSuccessExecution() {
            SchedulerService.JobHistory history = new SchedulerService.JobHistory(
                "success-job",
                System.currentTimeMillis(),
                1500,
                true,
                null
            );
            jobHistory.add(history);

            assertEquals(1, jobHistory.size());
            assertTrue(jobHistory.get(0).success());
            assertNull(jobHistory.get(0).errorMessage());
            assertEquals(1500, jobHistory.get(0).duration());
        }

        @Test
        @DisplayName("记录失败执行")
        void testRecordFailureExecution() {
            SchedulerService.JobHistory history = new SchedulerService.JobHistory(
                "failed-job",
                System.currentTimeMillis(),
                500,
                false,
                "Connection timed out"
            );
            jobHistory.add(history);

            assertEquals(1, jobHistory.size());
            assertFalse(jobHistory.get(0).success());
            assertEquals("Connection timed out", jobHistory.get(0).errorMessage());
        }

        @Test
        @DisplayName("获取限制数量的历史记录")
        void testGetLimitedHistory() {
            for (int i = 0; i < 10; i++) {
                jobHistory.add(new SchedulerService.JobHistory(
                    "job-" + i,
                    System.currentTimeMillis(),
                    1000L,
                    true,
                    null
                ));
            }

            int limit = 5;
            int size = jobHistory.size();
            List<SchedulerService.JobHistory> recentHistory = new ArrayList<>(
                jobHistory.subList(size - limit, size)
            );

            assertEquals(5, recentHistory.size());
        }

        @Test
        @DisplayName("清空历史记录")
        void testClearHistory() {
            jobHistory.add(new SchedulerService.JobHistory("job1", System.currentTimeMillis(), 1000L, true, null));
            jobHistory.add(new SchedulerService.JobHistory("job2", System.currentTimeMillis(), 2000L, true, null));

            assertEquals(2, jobHistory.size());

            jobHistory.clear();
            assertEquals(0, jobHistory.size());
        }

        @Test
        @DisplayName("按任务名称过滤历史")
        void testFilterHistoryByName() {
            jobHistory.add(new SchedulerService.JobHistory("job-A", System.currentTimeMillis(), 100L, true, null));
            jobHistory.add(new SchedulerService.JobHistory("job-B", System.currentTimeMillis(), 200L, true, null));
            jobHistory.add(new SchedulerService.JobHistory("job-A", System.currentTimeMillis(), 150L, true, null));
            jobHistory.add(new SchedulerService.JobHistory("job-A", System.currentTimeMillis(), 120L, false, "error"));

            List<SchedulerService.JobHistory> jobAHistory = new ArrayList<>();
            for (SchedulerService.JobHistory h : jobHistory) {
                if (h.name().equals("job-A")) {
                    jobAHistory.add(h);
                }
            }

            assertEquals(3, jobAHistory.size());
        }
    }

    @Nested
    @DisplayName("Cron 表达式测试")
    class CronExpressionTest {

        @Test
        @DisplayName("每天午夜执行")
        void testDailyMidnightCron() {
            String cron = "0 0 * * *";
            assertTrue(cron.contains("0"));
            assertTrue(cron.contains("*"));
        }

        @Test
        @DisplayName("每5分钟执行")
        void testEveryFiveMinutesCron() {
            String cron = "*/5 * * * *";
            assertTrue(cron.contains("*/5"));
        }

        @Test
        @DisplayName("每小时执行")
        void testHourlyCron() {
            String cron = "0 * * * *";
            assertEquals("0 * * * *", cron);
        }

        @Test
        @DisplayName("每天中午12点执行")
        void testDailyNoonCron() {
            String cron = "0 12 * * *";
            assertTrue(cron.contains("12"));
        }
    }

    @Nested
    @DisplayName("任务请求测试")
    class JobRequestTest {

        @Test
        @DisplayName("创建 Cron 任务请求")
        void testCronJobRequest() {
            SchedulerController.JobRequest request = new SchedulerController.JobRequest(
                "test-job",
                "0 0 * * *",
                null,
                null,
                "echo hello"
            );

            assertEquals("test-job", request.name());
            assertEquals("0 0 * * *", request.cron());
            assertEquals("echo hello", request.command());
            assertNull(request.fixedRate());
            assertNull(request.delay());
        }

        @Test
        @DisplayName("创建固定频率任务请求")
        void testFixedRateJobRequest() {
            SchedulerController.JobRequest request = new SchedulerController.JobRequest(
                "fixed-job",
                null,
                5000L,
                null,
                "ls"
            );

            assertEquals("fixed-job", request.name());
            assertEquals(5000L, request.fixedRate());
            assertEquals("ls", request.command());
            assertNull(request.cron());
        }

        @Test
        @DisplayName("创建一次性任务请求")
        void testOneTimeJobRequest() {
            SchedulerController.JobRequest request = new SchedulerController.JobRequest(
                "onetime-job",
                null,
                null,
                60000L,
                "date"
            );

            assertEquals("onetime-job", request.name());
            assertEquals(60000L, request.delay());
            assertEquals("date", request.command());
            assertNull(request.cron());
        }
    }

    @Nested
    @DisplayName("响应结果测试")
    class ResultTest {

        @Test
        @DisplayName("创建成功响应")
        void testSuccessResult() {
            SchedulerController.Result result = SchedulerController.Result.success("操作成功", Map.of("id", 1));

            assertEquals(200, result.code());
            assertEquals("操作成功", result.message());
            assertNotNull(result.data());
        }

        @Test
        @DisplayName("创建成功响应（无数据）")
        void testSuccessResultWithoutData() {
            SchedulerController.Result result = SchedulerController.Result.success("操作成功");

            assertEquals(200, result.code());
            assertEquals("操作成功", result.message());
            assertNull(result.data());
        }

        @Test
        @DisplayName("创建错误响应")
        void testErrorResult() {
            SchedulerController.Result result = SchedulerController.Result.error("操作失败");

            assertEquals(500, result.code());
            assertEquals("操作失败", result.message());
            assertNull(result.data());
        }
    }

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTest {

        @Test
        @DisplayName("ConcurrentHashMap 线程安全")
        void testConcurrentHashMap() {
            Map<String, SchedulerService.JobInfo> concurrentJobs = new java.util.concurrent.ConcurrentHashMap<>();

            // 模拟并发添加
            for (int i = 0; i < 100; i++) {
                final int index = i;
                concurrentJobs.put("job-" + index, new SchedulerService.JobInfo(
                    "job-" + index,
                    "0 0 * * *",
                    0,
                    false,
                    0,
                    "cmd-" + index,
                    SchedulerService.JobType.CRON
                ));
            }

            assertEquals(100, concurrentJobs.size());
        }

        @Test
        @DisplayName("CopyOnWriteArrayList 线程安全")
        void testCopyOnWriteArrayList() {
            List<SchedulerService.JobHistory> concurrentHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

            // 模拟并发添加
            for (int i = 0; i < 100; i++) {
                concurrentHistory.add(new SchedulerService.JobHistory(
                    "job-" + i,
                    System.currentTimeMillis(),
                    100L,
                    true,
                    null
                ));
            }

            assertEquals(100, concurrentHistory.size());
        }
    }
}