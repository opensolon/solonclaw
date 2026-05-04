package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;

/** Dashboard-first MCP server registry. */
public class DashboardMcpService {
    private final AppConfig appConfig;
    private final SqliteDatabase database;

    public DashboardMcpService(AppConfig appConfig, SqliteDatabase database) {
        this.appConfig = appConfig;
        this.database = database;
    }

    public Map<String, Object> list() throws Exception {
        List<Map<String, Object>> servers = new ArrayList<Map<String, Object>>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select * from mcp_servers order by updated_at desc");
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    servers.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("enabled", appConfig.getMcp().isEnabled());
        result.put("servers", servers);
        return result;
    }

    public Map<String, Object> save(Map<String, Object> body) throws Exception {
        String serverId = read(body, "serverId");
        if (StrUtil.isBlank(serverId)) {
            serverId = IdSupport.newId();
        }
        String name = StrUtil.blankToDefault(read(body, "name"), serverId);
        String transport = StrUtil.blankToDefault(read(body, "transport"), "stdio");
        long now = System.currentTimeMillis();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into mcp_servers (server_id, name, transport, endpoint, command, args_json, auth_json, status, tools_json, last_error, enabled, created_at, updated_at, last_checked_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, coalesce((select created_at from mcp_servers where server_id = ?), ?), ?, coalesce((select last_checked_at from mcp_servers where server_id = ?), 0))");
            statement.setString(1, serverId);
            statement.setString(2, name);
            statement.setString(3, transport);
            statement.setString(4, read(body, "endpoint"));
            statement.setString(5, read(body, "command"));
            statement.setString(6, json(body.get("args")));
            statement.setString(7, json(body.get("auth")));
            statement.setString(8, "configured");
            statement.setString(9, json(body.get("tools")));
            statement.setString(10, null);
            statement.setInt(11, asBoolean(body.get("enabled"), true) ? 1 : 0);
            statement.setString(12, serverId);
            statement.setLong(13, now);
            statement.setLong(14, now);
            statement.setString(15, serverId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
        return Collections.singletonMap("server_id", serverId);
    }

    public Map<String, Object> check(String serverId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update mcp_servers set status = ?, last_error = ?, last_checked_at = ?, updated_at = ? where server_id = ?");
            statement.setString(1, appConfig.getMcp().isEnabled() ? "ready" : "disabled");
            statement.setString(
                    2,
                    appConfig.getMcp().isEnabled()
                            ? null
                            : "MCP is disabled in runtime config.");
            long now = System.currentTimeMillis();
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.setString(5, serverId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("server_id", serverId);
        result.put("status", appConfig.getMcp().isEnabled() ? "ready" : "disabled");
        result.put("schema_sanitizer", "snack4");
        return result;
    }

    public Map<String, Object> delete(String serverId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("delete from mcp_servers where server_id = ?");
            statement.setString(1, serverId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
        return Collections.singletonMap("ok", true);
    }

    private Map<String, Object> map(ResultSet resultSet) throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("server_id", resultSet.getString("server_id"));
        map.put("name", resultSet.getString("name"));
        map.put("transport", resultSet.getString("transport"));
        map.put("endpoint", resultSet.getString("endpoint"));
        map.put("command", resultSet.getString("command"));
        map.put("args", parse(resultSet.getString("args_json")));
        map.put("auth", parse(resultSet.getString("auth_json")));
        map.put("status", resultSet.getString("status"));
        map.put("tools", parse(resultSet.getString("tools_json")));
        map.put("last_error", resultSet.getString("last_error"));
        map.put("enabled", resultSet.getInt("enabled") != 0);
        map.put("created_at", resultSet.getLong("created_at"));
        map.put("updated_at", resultSet.getLong("updated_at"));
        map.put("last_checked_at", resultSet.getLong("last_checked_at"));
        return map;
    }

    private String read(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String json(Object value) {
        return value == null ? null : ONode.serialize(value);
    }

    private Object parse(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return ONode.deserialize(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }
}
