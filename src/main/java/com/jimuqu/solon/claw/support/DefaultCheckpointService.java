package com.jimuqu.solon.claw.support;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.CheckpointRecord;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.support.constants.CheckpointConstants;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;

/** 默认文件快照服务。 */
@RequiredArgsConstructor
public class DefaultCheckpointService implements CheckpointService {
    /** 应用配置。 */
    private final AppConfig appConfig;

    /** 数据库访问对象。 */
    private final SqliteDatabase database;

    @Override
    public CheckpointRecord createCheckpoint(String sourceKey, String sessionId, List<File> files)
            throws Exception {
        if (!appConfig.getRollback().isEnabled()) {
            return null;
        }

        String checkpointId = IdSupport.newId();
        File rootDir =
                FileUtil.file(
                        appConfig.getRuntime().getCacheDir(),
                        CheckpointConstants.CHECKPOINT_DIR_NAME,
                        checkpointId);
        FileUtil.mkdir(rootDir);

        ONode manifest = new ONode().asObject();
        manifest.set("checkpointId", checkpointId);
        manifest.set("sourceKey", sourceKey);
        manifest.set("sessionId", sessionId);
        manifest.getOrNew("files").asArray();

        int index = 0;
        for (File file : files) {
            if (file == null) {
                continue;
            }
            ONode item = new ONode().asObject();
            item.set("path", file.getAbsolutePath());
            item.set("exists", file.exists());
            if (file.exists()) {
                File snapshotFile = FileUtil.file(rootDir, "file-" + index + ".bak");
                FileUtil.copy(file, snapshotFile, true);
                item.set("snapshot", snapshotFile.getAbsolutePath());
            }
            manifest.get("files").add(item);
            index++;
        }

        File manifestFile = FileUtil.file(rootDir, CheckpointConstants.MANIFEST_FILE_NAME);
        FileUtil.writeUtf8String(manifest.toJson(), manifestFile);

        CheckpointRecord record = new CheckpointRecord();
        record.setCheckpointId(checkpointId);
        record.setSourceKey(sourceKey);
        record.setSessionId(sessionId);
        record.setCheckpointDir(rootDir.getAbsolutePath());
        record.setManifestPath(manifestFile.getAbsolutePath());
        record.setCreatedAt(System.currentTimeMillis());
        saveRecord(record);
        pruneOldRecords(sourceKey);
        return record;
    }

    @Override
    public CheckpointRecord rollbackLatest(String sourceKey) throws Exception {
        CheckpointRecord latest = findLatest(sourceKey);
        if (latest == null) {
            throw new IllegalStateException("当前来源键没有可回滚的 checkpoint。");
        }
        return rollback(latest.getCheckpointId());
    }

    @Override
    public CheckpointRecord rollback(String checkpointId) throws Exception {
        CheckpointRecord record = findById(checkpointId);
        if (record == null) {
            throw new IllegalStateException("未找到 checkpoint：" + checkpointId);
        }

        ONode manifest =
                ONode.ofJson(FileUtil.readUtf8String(FileUtil.file(record.getManifestPath())));
        ONode filesNode = manifest.get("files");
        for (int i = 0; i < filesNode.size(); i++) {
            ONode item = filesNode.get(i);
            File target = requireSafeRollbackTarget(item.get("path").getString());
            boolean existed = item.get("exists").getBoolean();
            if (!existed) {
                if (target.exists()) {
                    FileUtil.del(target);
                }
                continue;
            }

            File snapshot = requireSafeSnapshot(record, item.get("snapshot").getString());
            FileUtil.mkParentDirs(target);
            FileUtil.copy(snapshot, target, true);
        }

        record.setRestoredAt(System.currentTimeMillis());
        updateRestoredAt(record);
        return record;
    }

    private File requireSafeRollbackTarget(String path) throws Exception {
        File target = FileUtil.file(path).getCanonicalFile();
        File project = new File(System.getProperty("user.dir")).getCanonicalFile();
        File runtime = new File(appConfig.getRuntime().getHome()).getCanonicalFile();
        if (isUnder(target, project) || isUnder(target, runtime)) {
            return target;
        }
        throw new IllegalArgumentException("Checkpoint target is outside allowed roots: " + path);
    }

    private File requireSafeSnapshot(CheckpointRecord record, String path) throws Exception {
        File snapshot = FileUtil.file(path).getCanonicalFile();
        File checkpointDir = FileUtil.file(record.getCheckpointDir()).getCanonicalFile();
        if (!isUnder(snapshot, checkpointDir)) {
            throw new IllegalArgumentException(
                    "Checkpoint snapshot is outside checkpoint directory: " + path);
        }
        return snapshot;
    }

    private boolean isUnder(File file, File root) {
        String filePath = file.getAbsolutePath();
        String rootPath = root.getAbsolutePath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    @Override
    public boolean hasRecentCheckpoint(String sourceKey, long sinceEpochMillis) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select count(*) from checkpoints where source_key = ? and created_at >= ?");
            statement.setString(1, sourceKey);
            statement.setLong(2, sinceEpochMillis);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() && resultSet.getInt(1) > 0;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public List<CheckpointRecord> listRecent(String sourceKey, int limit) throws Exception {
        List<CheckpointRecord> results = new ArrayList<CheckpointRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select checkpoint_id, source_key, session_id, checkpoint_dir, manifest_path, created_at, restored_at from checkpoints where source_key = ? order by created_at desc limit ?");
            statement.setString(1, sourceKey);
            statement.setInt(2, Math.max(1, limit));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    results.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return results;
    }

    @Override
    public Map<String, Object> preview(String checkpointId) throws Exception {
        CheckpointRecord record = findById(checkpointId);
        if (record == null) {
            throw new IllegalStateException("未找到 checkpoint：" + checkpointId);
        }

        ONode manifest =
                ONode.ofJson(FileUtil.readUtf8String(FileUtil.file(record.getManifestPath())));
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        ONode filesNode = manifest.get("files");
        for (int i = 0; i < filesNode.size(); i++) {
            ONode item = filesNode.get(i);
            Map<String, Object> file = new LinkedHashMap<String, Object>();
            file.put("path", item.get("path").getString());
            file.put("exists", item.get("exists").getBoolean());
            file.put("snapshot", item.get("snapshot").getString());
            files.add(file);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("checkpoint_id", record.getCheckpointId());
        result.put("source_key", record.getSourceKey());
        result.put("session_id", record.getSessionId());
        result.put("created_at", record.getCreatedAt());
        result.put("restored_at", record.getRestoredAt());
        result.put("files", files);
        return result;
    }

    /** 保存 checkpoint 记录。 */
    private void saveRecord(CheckpointRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into checkpoints (checkpoint_id, source_key, session_id, checkpoint_dir, manifest_path, created_at, restored_at) values (?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getCheckpointId());
            statement.setString(2, record.getSourceKey());
            statement.setString(3, record.getSessionId());
            statement.setString(4, record.getCheckpointDir());
            statement.setString(5, record.getManifestPath());
            statement.setLong(6, record.getCreatedAt());
            statement.setLong(7, record.getRestoredAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /** 更新恢复时间。 */
    private void updateRestoredAt(CheckpointRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update checkpoints set restored_at = ? where checkpoint_id = ?");
            statement.setLong(1, record.getRestoredAt());
            statement.setString(2, record.getCheckpointId());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /** 查询最新 checkpoint。 */
    private CheckpointRecord findLatest(String sourceKey) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select checkpoint_id, source_key, session_id, checkpoint_dir, manifest_path, created_at, restored_at from checkpoints where source_key = ? order by created_at desc limit 1");
            statement.setString(1, sourceKey);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? map(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /** 通过 id 查询 checkpoint。 */
    private CheckpointRecord findById(String checkpointId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select checkpoint_id, source_key, session_id, checkpoint_dir, manifest_path, created_at, restored_at from checkpoints where checkpoint_id = ?");
            statement.setString(1, checkpointId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? map(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /** 清理超额 checkpoint。 */
    private void pruneOldRecords(String sourceKey) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select checkpoint_id, checkpoint_dir from checkpoints where source_key = ? order by created_at desc");
            statement.setString(1, sourceKey);
            ResultSet resultSet = statement.executeQuery();
            try {
                List<String> ids = new ArrayList<String>();
                List<String> dirs = new ArrayList<String>();
                while (resultSet.next()) {
                    ids.add(resultSet.getString("checkpoint_id"));
                    dirs.add(resultSet.getString("checkpoint_dir"));
                }

                for (int i = appConfig.getRollback().getMaxCheckpointsPerSource();
                        i < ids.size();
                        i++) {
                    deleteRecord(ids.get(i));
                    FileUtil.del(dirs.get(i));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /** 删除 checkpoint 记录。 */
    private void deleteRecord(String checkpointId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("delete from checkpoints where checkpoint_id = ?");
            statement.setString(1, checkpointId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /** 结果集映射。 */
    private CheckpointRecord map(ResultSet resultSet) throws Exception {
        CheckpointRecord record = new CheckpointRecord();
        record.setCheckpointId(resultSet.getString("checkpoint_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setCheckpointDir(resultSet.getString("checkpoint_dir"));
        record.setManifestPath(resultSet.getString("manifest_path"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setRestoredAt(resultSet.getLong("restored_at"));
        return record;
    }
}
