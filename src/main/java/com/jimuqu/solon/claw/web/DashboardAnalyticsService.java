package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard 分析服务。 */
public class DashboardAnalyticsService {
    private final SessionRepository sessionRepository;

    public DashboardAnalyticsService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Map<String, Object> getUsage(int days) throws Exception {
        int safeDays = days <= 0 ? 30 : Math.min(days, 365);
        int totalSessions = sessionRepository.countAll();
        List<Map<String, Object>> daily = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> byModel = new ArrayList<Map<String, Object>>();
        Map<String, Object> totals = createTotals(0);

        if (totalSessions <= 0) {
            return buildResponse(daily, byModel, totals);
        }

        List<SessionRecord> records = sessionRepository.listRecent(totalSessions);
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate end =
                Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zoneId).toLocalDate();
        LocalDate start = end.minusDays(safeDays - 1L);

        Map<String, Integer> dailySessions = new LinkedHashMap<String, Integer>();
        Map<String, Integer> modelSessions = new LinkedHashMap<String, Integer>();
        Map<String, Long> dailyInputTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> dailyOutputTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> dailyReasoningTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> dailyCacheReadTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> dailyCacheWriteTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> modelInputTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> modelOutputTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> modelCacheReadTokens = new LinkedHashMap<String, Long>();
        Map<String, Long> modelCacheWriteTokens = new LinkedHashMap<String, Long>();
        int totalInRange = 0;
        long totalInput = 0L;
        long totalOutput = 0L;
        long totalReasoning = 0L;
        long totalCacheRead = 0L;
        long totalCacheWrite = 0L;

        for (SessionRecord record : records) {
            long usageAt =
                    record.getLastUsageAt() > 0
                            ? record.getLastUsageAt()
                            : (record.getUpdatedAt() > 0
                                    ? record.getUpdatedAt()
                                    : record.getCreatedAt());
            LocalDate recordDay = Instant.ofEpochMilli(usageAt).atZone(zoneId).toLocalDate();
            if (recordDay.isBefore(start) || recordDay.isAfter(end)) {
                continue;
            }

            String dayKey = recordDay.toString();
            dailySessions.put(
                    dayKey, dailySessions.containsKey(dayKey) ? dailySessions.get(dayKey) + 1 : 1);
            addLong(dailyInputTokens, dayKey, record.getCumulativeInputTokens());
            addLong(dailyOutputTokens, dayKey, record.getCumulativeOutputTokens());
            addLong(dailyReasoningTokens, dayKey, record.getCumulativeReasoningTokens());
            addLong(dailyCacheReadTokens, dayKey, record.getCumulativeCacheReadTokens());
            addLong(dailyCacheWriteTokens, dayKey, record.getCumulativeCacheWriteTokens());

            String modelKey =
                    normalizeModelLabel(
                            StrUtil.blankToDefault(
                                    record.getLastResolvedModel(), record.getModelOverride()));
            modelSessions.put(
                    modelKey,
                    modelSessions.containsKey(modelKey) ? modelSessions.get(modelKey) + 1 : 1);
            addLong(modelInputTokens, modelKey, record.getCumulativeInputTokens());
            addLong(modelOutputTokens, modelKey, record.getCumulativeOutputTokens());
            addLong(modelCacheReadTokens, modelKey, record.getCumulativeCacheReadTokens());
            addLong(modelCacheWriteTokens, modelKey, record.getCumulativeCacheWriteTokens());
            totalInRange++;
            totalInput += record.getCumulativeInputTokens();
            totalOutput += record.getCumulativeOutputTokens();
            totalReasoning += record.getCumulativeReasoningTokens();
            totalCacheRead += record.getCumulativeCacheReadTokens();
            totalCacheWrite += record.getCumulativeCacheWriteTokens();
        }

        if (totalInRange <= 0) {
            return buildResponse(daily, byModel, totals);
        }

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String dayKey = cursor.toString();
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("day", dayKey);
            item.put(
                    "input_tokens",
                    dailyInputTokens.containsKey(dayKey) ? dailyInputTokens.get(dayKey) : 0L);
            item.put(
                    "output_tokens",
                    dailyOutputTokens.containsKey(dayKey) ? dailyOutputTokens.get(dayKey) : 0L);
            item.put(
                    "cache_read_tokens",
                    dailyCacheReadTokens.containsKey(dayKey)
                            ? dailyCacheReadTokens.get(dayKey)
                            : 0L);
            item.put(
                    "cache_write_tokens",
                    dailyCacheWriteTokens.containsKey(dayKey)
                            ? dailyCacheWriteTokens.get(dayKey)
                            : 0L);
            item.put(
                    "reasoning_tokens",
                    dailyReasoningTokens.containsKey(dayKey)
                            ? dailyReasoningTokens.get(dayKey)
                            : 0L);
            item.put("sessions", dailySessions.containsKey(dayKey) ? dailySessions.get(dayKey) : 0);
            daily.add(item);
            cursor = cursor.plusDays(1);
        }

        for (Map.Entry<String, Integer> entry : modelSessions.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("model", normalizeModelLabel(entry.getKey()));
            item.put(
                    "input_tokens",
                    modelInputTokens.containsKey(entry.getKey())
                            ? modelInputTokens.get(entry.getKey())
                            : 0L);
            item.put(
                    "output_tokens",
                    modelOutputTokens.containsKey(entry.getKey())
                            ? modelOutputTokens.get(entry.getKey())
                            : 0L);
            item.put(
                    "cache_read_tokens",
                    modelCacheReadTokens.containsKey(entry.getKey())
                            ? modelCacheReadTokens.get(entry.getKey())
                            : 0L);
            item.put(
                    "cache_write_tokens",
                    modelCacheWriteTokens.containsKey(entry.getKey())
                            ? modelCacheWriteTokens.get(entry.getKey())
                            : 0L);
            item.put("sessions", entry.getValue());
            byModel.add(item);
        }

        totals =
                createTotals(
                        totalInRange,
                        totalInput,
                        totalOutput,
                        totalCacheRead,
                        totalCacheWrite,
                        totalReasoning);
        return buildResponse(daily, byModel, totals);
    }

    private Map<String, Object> buildResponse(
            List<Map<String, Object>> daily,
            List<Map<String, Object>> byModel,
            Map<String, Object> totals) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("daily", daily);
        result.put("by_model", byModel);
        result.put("totals", totals);
        return result;
    }

    private Map<String, Object> createTotals(int totalSessions) {
        return createTotals(totalSessions, 0L, 0L, 0L, 0L, 0L);
    }

    private Map<String, Object> createTotals(
            int totalSessions,
            long totalInput,
            long totalOutput,
            long totalCacheRead,
            long totalCacheWrite,
            long totalReasoning) {
        Map<String, Object> totals = new LinkedHashMap<String, Object>();
        totals.put("total_input", totalInput);
        totals.put("total_output", totalOutput);
        totals.put("total_cache_read", totalCacheRead);
        totals.put("total_cache_write", totalCacheWrite);
        totals.put("total_reasoning", totalReasoning);
        totals.put("total_sessions", totalSessions);
        return totals;
    }

    private String normalizeModelLabel(String model) {
        String value = StrUtil.blankToDefault(model, "default").trim();
        return value.toLowerCase(Locale.ROOT);
    }

    private void addLong(Map<String, Long> target, String key, long value) {
        target.put(key, target.containsKey(key) ? target.get(key) + value : value);
    }
}
