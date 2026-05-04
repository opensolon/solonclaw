package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.RuntimePathGuard;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard media cache index. */
public class DashboardMediaService {
    private final SqliteDatabase database;
    private final RuntimePathGuard pathGuard;

    public DashboardMediaService(SqliteDatabase database, RuntimePathGuard pathGuard) {
        this.database = database;
        this.pathGuard = pathGuard;
    }

    public Map<String, Object> list(String platform, int limit) throws Exception {
        List<Map<String, Object>> media = new ArrayList<Map<String, Object>>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement;
            if (StrUtil.isBlank(platform)) {
                statement =
                        connection.prepareStatement(
                                "select * from channel_media order by updated_at desc limit ?");
                statement.setInt(1, Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200)));
            } else {
                statement =
                        connection.prepareStatement(
                                "select * from channel_media where platform = ? order by updated_at desc limit ?");
                statement.setString(1, platform);
                statement.setInt(2, Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200)));
            }
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    media.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return Collections.singletonMap("media", media);
    }

    public Map<String, Object> indexLocal(Map<String, Object> body) throws Exception {
        String localPath = read(body, "localPath");
        File file = pathGuard.requireUnderMedia(FileUtil.file(localPath));
        long now = System.currentTimeMillis();
        String mediaId = StrUtil.blankToDefault(read(body, "mediaId"), IdSupport.newId());
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into channel_media (media_id, platform, chat_id, message_id, kind, original_name, mime_type, local_path, remote_id, status, error, size_bytes, created_at, updated_at, expires_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, coalesce((select created_at from channel_media where media_id = ?), ?), ?, ?)");
            statement.setString(1, mediaId);
            statement.setString(2, StrUtil.blankToDefault(read(body, "platform"), "MEMORY"));
            statement.setString(3, read(body, "chatId"));
            statement.setString(4, read(body, "messageId"));
            statement.setString(5, read(body, "kind"));
            statement.setString(6, StrUtil.blankToDefault(read(body, "originalName"), file.getName()));
            statement.setString(7, read(body, "mimeType"));
            statement.setString(8, file.getAbsolutePath());
            statement.setString(9, read(body, "remoteId"));
            statement.setString(10, "cached");
            statement.setString(11, null);
            statement.setLong(12, file.length());
            statement.setString(13, mediaId);
            statement.setLong(14, now);
            statement.setLong(15, now);
            statement.setLong(16, asLong(body.get("expiresAt")));
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
        return Collections.singletonMap("media_id", mediaId);
    }

    public Map<String, Object> detail(String mediaId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select * from channel_media where media_id = ?");
            statement.setString(1, mediaId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? map(resultSet) : new LinkedHashMap<String, Object>();
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public Map<String, Object> refresh(String mediaId) throws Exception {
        return updateStatus(mediaId, "refresh_requested", null);
    }

    public Map<String, Object> download(String mediaId) throws Exception {
        Map<String, Object> detail = detail(mediaId);
        File file = FileUtil.file(String.valueOf(detail.get("local_path")));
        if (!file.isFile()) {
            return updateStatus(mediaId, "download_missing", "local file not found");
        }
        Map<String, Object> result = updateStatus(mediaId, "download_ready", null);
        result.put("local_path", file.getAbsolutePath());
        result.put("size_bytes", file.length());
        return result;
    }

    public Map<String, Object> reference(String mediaId) throws Exception {
        Map<String, Object> detail = detail(mediaId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("media_id", mediaId);
        result.put("reference", "media://" + mediaId);
        result.put("status", detail.get("status"));
        result.put("kind", detail.get("kind"));
        result.put("local_path", detail.get("local_path"));
        return result;
    }

    private Map<String, Object> updateStatus(String mediaId, String status, String error)
            throws Exception {
        long now = System.currentTimeMillis();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update channel_media set status = ?, error = ?, updated_at = ? where media_id = ?");
            statement.setString(1, status);
            statement.setString(2, error);
            statement.setLong(3, now);
            statement.setString(4, mediaId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("media_id", mediaId);
        result.put("status", status);
        result.put("error", error);
        return result;
    }

    private Map<String, Object> map(ResultSet resultSet) throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("media_id", resultSet.getString("media_id"));
        map.put("platform", resultSet.getString("platform"));
        map.put("chat_id", resultSet.getString("chat_id"));
        map.put("message_id", resultSet.getString("message_id"));
        map.put("kind", resultSet.getString("kind"));
        map.put("original_name", resultSet.getString("original_name"));
        map.put("mime_type", resultSet.getString("mime_type"));
        map.put("local_path", resultSet.getString("local_path"));
        map.put("remote_id", resultSet.getString("remote_id"));
        map.put("status", resultSet.getString("status"));
        map.put("error", resultSet.getString("error"));
        map.put("size_bytes", resultSet.getLong("size_bytes"));
        map.put("created_at", resultSet.getLong("created_at"));
        map.put("updated_at", resultSet.getLong("updated_at"));
        map.put("expires_at", resultSet.getLong("expires_at"));
        return map;
    }

    private String read(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }
}
