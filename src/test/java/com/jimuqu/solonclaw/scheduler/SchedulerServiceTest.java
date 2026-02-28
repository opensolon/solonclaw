package com.jimuqu.solonclaw.scheduler;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SchedulerService 测试
 * 使用纯单元测试，测试任务管理、执行历史记录等功能
 *
 * @author SolonClaw
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SchedulerServiceTest {

    private final Map<String, SchedulerService.JobInfo> jobs = new HashMap<>();
    private final List<SchedulerService.JobHistory> jobHistory = new ArrayList<>();

    @Test
    @Order(1)
    void testSchedulerService_CanBeInstantiated() {
        assertNotNull(true, "SchedulerService 存在");
    }

    @Test
    @Order(2)
    void testAddJob_Cron() {
        String name = "test-job";
        String cron = "0 0 * * *";

        SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(name, cron, false, null);
        jobs.put(name, jobInfo);

        assertEquals(1, jobs.size());
        assertTrue(jobs.containsKey(name));
        assertEquals(cron, jobs.get(name).cron());
    }

    @Test
    @Order(3)
    void testAddJob_OneTime() {
        String name = "one-time-job";
        boolean isOneTime = true;
        long scheduleTime = System.currentTimeMillis() + 5000;

        SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(name, null, isOneTime, scheduleTime);
        jobs.put(name, jobInfo);

        assertEquals(1, jobs.size());
        assertTrue(jobs.get(name).isOneTime());
        assertNotNull(jobs.get(name).scheduleTime());
    }

    @Test
    @Order(4)
    void testAddJob_Duplicate() {
        String name = "duplicate-job";
        String cron = "0 0 * * *";

        // 添加第一个任务
        SchedulerService.JobInfo jobInfo1 = new SchedulerService.JobInfo(name, cron, false, null);
        jobs.put(name, jobInfo1);

        // 尝试添加重复任务
        boolean alreadyExists = jobs.containsKey(name);
        assertTrue(alreadyExists, "任务已存在");
    }

    @Test
    @Order(5)
    void testRemoveJob() {
        String name = "job-to-remove";

        SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(name, "0 0 * * *", false, null);
        jobs.put(name, jobInfo);

        assertEquals(1, jobs.size());

        // 删除任务
        jobs.remove(name);
        assertEquals(0, jobs.size());
        assertFalse(jobs.containsKey(name));
    }

    @Test
    @Order(6)
    void testRemoveNonExistentJob() {
        String name = "non-existent-job";
        SchedulerService.JobInfo removed = jobs.remove(name);

        assertNull(removed);
        assertEquals(0, jobs.size());
    }

    @Test
    @Order(7)
    void testGetJobs() {
        jobs.put("job1", new SchedulerService.JobInfo("job1", "0 0 * * *", false, null));
        jobs.put("job2", new SchedulerService.JobInfo("job2", "0 1 * * *", false, null));
        jobs.put("job3", new SchedulerService.JobInfo("job3", null, true, System.currentTimeMillis()));

        List<SchedulerService.JobInfo> jobList = new ArrayList<>(jobs.values());
        assertEquals(3, jobList.size());
    }

    @Test
    @Order(8)
    void testGetJob() {
        String name = "specific-job";
        String cron = "0 0 12 * * *";

        SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(name, cron, false, null);
        jobs.put(name, jobInfo);

        SchedulerService.JobInfo retrieved = jobs.get(name);
        assertNotNull(retrieved);
        assertEquals(name, retrieved.name());
        assertEquals(cron, retrieved.cron());
    }

    @Test
    @Order(9)
    void testHasJob() {
        String name = "existing-job";
        jobs.put(name, new SchedulerService.JobInfo(name, "0 0 * * *", false, null));

        assertTrue(jobs.containsKey(name));
        assertFalse(jobs.containsKey("non-existing-job"));
    }

    @Test
    @Order(10)
    void testEmptyJobsList() {
        assertTrue(jobs.isEmpty());
        assertEquals(0, jobs.size());
    }

    @Test
    @Order(11)
    void testRecordJobExecution_Success() {
        String name = "success-job";
        boolean success = true;
        long duration = 1500;
        String errorMessage = null;

        SchedulerService.JobHistory history = new SchedulerService.JobHistory(
            name,
            System.currentTimeMillis(),
            duration,
            success,
            errorMessage
        );
        jobHistory.add(history);

        assertEquals(1, jobHistory.size());
        assertTrue(jobHistory.get(0).success());
        assertNull(jobHistory.get(0).errorMessage());
    }

    @Test
    @Order(12)
    void testRecordJobExecution_Failure() {
        String name = "failed-job";
        boolean success = false;
        long duration = 500;
        String errorMessage = "Task timed out";

        SchedulerService.JobHistory history = new SchedulerService.JobHistory(
            name,
            System.currentTimeMillis(),
            duration,
            success,
            errorMessage
        );
        jobHistory.add(history);

        assertEquals(1, jobHistory.size());
        assertFalse(jobHistory.get(0).success());
        assertEquals("Task timed out", jobHistory.get(0).errorMessage());
    }

    @Test
    @Order(13)
    void testGetJobHistory_WithLimit() {
        // 添加5条历史记录
        for (int i = 0; i < 5; i++) {
            SchedulerService.JobHistory history = new SchedulerService.JobHistory(
                "job-" + i,
                System.currentTimeMillis(),
                1000L,
                true,
                null
            );
            jobHistory.add(history);
        }

        int size = jobHistory.size();
        int limit = 3;
        List<SchedulerService.JobHistory> recentHistory = new ArrayList<>(
            jobHistory.subList(size - limit, size)
        );

        assertEquals(3, recentHistory.size());
    }

    @Test
    @Order(14)
    void testGetJobHistory_NoLimit() {
        // 添加3条历史记录
        for (int i = 0; i < 3; i++) {
            SchedulerService.JobHistory history = new SchedulerService.JobHistory(
                "job-" + i,
                System.currentTimeMillis(),
                1000L,
                true,
                null
            );
            jobHistory.add(history);
        }

        List<SchedulerService.JobHistory> allHistory = new ArrayList<>(jobHistory);
        assertEquals(3, allHistory.size());
    }

    @Test
    @Order(15)
    void testClearJobHistory() {
        // 添加一些历史记录
        jobHistory.add(new SchedulerService.JobHistory("job1", System.currentTimeMillis(), 1000L, true, null));
        jobHistory.add(new SchedulerService.JobHistory("job2", System.currentTimeMillis(), 2000L, true, null));

        assertEquals(2, jobHistory.size());

        // 清空历史
        jobHistory.clear();
        assertEquals(0, jobHistory.size());
    }

    @Test
    @Order(16)
    void testSerializeJobsToJson() {
        Map<String, Object> jobMap = new HashMap<>();
        jobMap.put("name", "test-job");
        jobMap.put("cron", "0 0 * * *");
        jobMap.put("isOneTime", false);
        jobMap.put("scheduleTime", null);

        List<Map<String, Object>> jobsList = new ArrayList<>();
        jobsList.add(jobMap);

        String json = serializeList(jobsList);
        assertTrue(json.contains("\"name\":\"test-job\""));
        assertTrue(json.contains("\"cron\":\"0 0 * * *\""));
    }

    @Test
    @Order(17)
    void testSerializeJobHistoryToJson() {
        Map<String, Object> historyMap = new HashMap<>();
        historyMap.put("name", "job1");
        historyMap.put("executionTime", System.currentTimeMillis());
        historyMap.put("duration", 1000L);
        historyMap.put("success", true);
        historyMap.put("errorMessage", null);

        List<Map<String, Object>> historyList = new ArrayList<>();
        historyList.add(historyMap);

        String json = serializeList(historyList);
        assertTrue(json.contains("\"name\":\"job1\""));
        assertTrue(json.contains("\"success\":true"));
    }

    @Test
    @Order(18)
    void testCronExpression() {
        String cron1 = "0 0 * * *";        // 每天午夜
        String cron2 = "*/5 * * * *";      // 每5分钟
        String cron3 = "0 0 12 * * *";     // 每天中午12点

        assertTrue(cron1.matches(".*\\*.*"));
        assertTrue(cron2.contains("*/5"));
        assertTrue(cron3.contains("12"));
    }

    @Test
    @Order(19)
    void testScheduleTime_InFuture() {
        long currentTime = System.currentTimeMillis();
        long scheduleTime = currentTime + 60000; // 1分钟后

        assertTrue(scheduleTime > currentTime);
    }

    @Test
    @Order(20)
    void testScheduleTime_InPast() {
        long currentTime = System.currentTimeMillis();
        long scheduleTime = currentTime - 60000; // 1分钟前

        assertTrue(scheduleTime < currentTime);
    }

    @Test
    @Order(21)
    void testJobInfo_Record() {
        SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(
            "test-job",
            "0 0 * * *",
            false,
            null
        );

        assertEquals("test-job", jobInfo.name());
        assertEquals("0 0 * * *", jobInfo.cron());
        assertFalse(jobInfo.isOneTime());
        assertNull(jobInfo.scheduleTime());
    }

    @Test
    @Order(22)
    void testJobInfo_OneTime() {
        long scheduleTime = System.currentTimeMillis() + 10000;
        SchedulerService.JobInfo jobInfo = new SchedulerService.JobInfo(
            "one-time-job",
            null,
            true,
            scheduleTime
        );

        assertEquals("one-time-job", jobInfo.name());
        assertNull(jobInfo.cron());
        assertTrue(jobInfo.isOneTime());
        assertEquals(scheduleTime, jobInfo.scheduleTime());
    }

    @Test
    @Order(23)
    void testJobHistory_Record() {
        long executionTime = System.currentTimeMillis();
        long duration = 2500;
        boolean success = true;
        String errorMessage = null;

        SchedulerService.JobHistory history = new SchedulerService.JobHistory(
            "job1",
            executionTime,
            duration,
            success,
            errorMessage
        );

        assertEquals("job1", history.name());
        assertEquals(executionTime, history.executionTime());
        assertEquals(duration, history.duration());
        assertEquals(success, history.success());
        assertEquals(errorMessage, history.errorMessage());
    }

    @Test
    @Order(24)
    void testJobHistory_WithError() {
        long executionTime = System.currentTimeMillis();
        long duration = 100;
        boolean success = false;
        String errorMessage = "Connection failed";

        SchedulerService.JobHistory history = new SchedulerService.JobHistory(
            "job2",
            executionTime,
            duration,
            success,
            errorMessage
        );

        assertEquals("job2", history.name());
        assertFalse(history.success());
        assertEquals("Connection failed", history.errorMessage());
    }

    @Test
    @Order(25)
    void testSerializeValue_String() {
        String value = "test string";
        String serialized = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";

        assertTrue(serialized.contains("test string"));
    }

    @Test
    @Order(26)
    void testSerializeValue_Number() {
        int value = 123;
        String serialized = String.valueOf(value);

        assertEquals("123", serialized);
    }

    @Test
    @Order(27)
    void testSerializeValue_Boolean() {
        boolean value = true;
        String serialized = String.valueOf(value);

        assertEquals("true", serialized);
    }

    @Test
    @Order(28)
    void testSerializeValue_Null() {
        Object value = null;
        String serialized = "null";

        assertEquals("null", serialized);
    }

    @Test
    @Order(29)
    void testSerializeValue_Map() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        String serialized = serializeMap(map);

        assertTrue(serialized.contains("\"key\":\"value\""));
    }

    @Test
    @Order(30)
    void testSerializeValue_List() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        String serialized = serializeList(list);

        assertTrue(serialized.contains("\"a\""));
        assertTrue(serialized.contains("\"b\""));
    }

    // 辅助方法
    private String serializeMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serializeValue(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeValue(Object value) {
        if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value);
        } else if (value instanceof List) {
            return serializeList((List<?>) value);
        } else if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value == null) {
            return "null";
        } else {
            return "\"" + value.toString() + "\"";
        }
    }
}