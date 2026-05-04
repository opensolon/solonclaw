package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.context.SkillCuratorService;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.support.IdSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;

/** Curator dashboard report facade. */
public class DashboardCuratorService {
    private final SkillCuratorService skillCuratorService;
    private final SqliteDatabase database;

    public DashboardCuratorService(
            SkillCuratorService skillCuratorService, SqliteDatabase database) {
        this.skillCuratorService = skillCuratorService;
        this.database = database;
    }

    public Map<String, Object> run(boolean force) throws Exception {
        Map<String, Object> report = skillCuratorService.runOnce(force);
        saveReport(report);
        return report;
    }

    public Map<String, Object> list(int limit) throws Exception {
        List<Map<String, Object>> reports = new ArrayList<Map<String, Object>>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from curator_reports order by started_at desc limit ?");
            statement.setInt(1, Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    reports.add(map(resultSet, false));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("reports", reports);
        return result;
    }

    public Map<String, Object> detail(String reportId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select * from curator_reports where report_id = ?");
            statement.setString(1, reportId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? map(resultSet, true) : new LinkedHashMap<String, Object>();
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public Map<String, Object> apply(String skillName, String suggestion) {
        return skillCuratorService.applySuggestion(skillName, suggestion);
    }

    public Map<String, Object> ignore(String skillName, String suggestion) {
        return skillCuratorService.ignoreSuggestion(skillName, suggestion);
    }

    public Map<String, Object> improvements(int limit) throws Exception {
        List<Map<String, Object>> improvements = new ArrayList<Map<String, Object>>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from skill_improvements order by created_at desc limit ?");
            statement.setInt(1, Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("improvement_id", resultSet.getString("improvement_id"));
                    item.put("session_id", resultSet.getString("session_id"));
                    item.put("run_id", resultSet.getString("run_id"));
                    item.put("skill_name", resultSet.getString("skill_name"));
                    item.put("action", resultSet.getString("action"));
                    item.put("summary", resultSet.getString("summary"));
                    item.put(
                            "changed_files",
                            parseJson(resultSet.getString("changed_files_json")));
                    item.put("evidence", parseJson(resultSet.getString("evidence_json")));
                    item.put("needs_review", resultSet.getInt("needs_review") != 0);
                    item.put("created_at", resultSet.getLong("created_at"));
                    improvements.add(item);
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("improvements", improvements);
        return result;
    }

    private void saveReport(Map<String, Object> report) throws Exception {
        long startedAt = asLong(report.get("startedAt"));
        long finishedAt = asLong(report.get("finishedAt"));
        String status = StrUtil.blankToDefault(String.valueOf(report.get("status")), "unknown");
        Object items = report.get("items");
        int itemCount = items instanceof List ? ((List<?>) items).size() : 0;
        String summary = "items=" + itemCount + ", status=" + status;
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into curator_reports (report_id, status, summary, report_path, report_json, started_at, finished_at) values (?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, IdSupport.newId());
            statement.setString(2, status);
            statement.setString(3, summary);
            statement.setString(4, String.valueOf(report.get("stateFile")));
            statement.setString(5, ONode.serialize(report));
            statement.setLong(6, startedAt);
            statement.setLong(7, finishedAt);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    private Map<String, Object> map(ResultSet resultSet, boolean includeJson) throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("report_id", resultSet.getString("report_id"));
        map.put("status", resultSet.getString("status"));
        map.put("summary", resultSet.getString("summary"));
        map.put("report_path", resultSet.getString("report_path"));
        map.put("started_at", resultSet.getLong("started_at"));
        map.put("finished_at", resultSet.getLong("finished_at"));
        if (includeJson) {
            map.put("report", ONode.deserialize(resultSet.getString("report_json"), Object.class));
        }
        return map;
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private Object parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return ONode.deserialize(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
