package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.CronJobRecord;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** SqliteCronJobRepository 实现。 */
@RequiredArgsConstructor
public class SqliteCronJobRepository implements CronJobRepository {
    private final SqliteDatabase database;

    public CronJobRecord save(CronJobRecord job) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into cron_jobs (job_id, name, cron_expr, prompt, source_key, deliver_platform, deliver_chat_id, status, next_run_at, last_run_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, job.getJobId());
            statement.setString(2, job.getName());
            statement.setString(3, job.getCronExpr());
            statement.setString(4, job.getPrompt());
            statement.setString(5, job.getSourceKey());
            statement.setString(6, job.getDeliverPlatform());
            statement.setString(7, job.getDeliverChatId());
            statement.setString(8, job.getStatus());
            statement.setLong(9, job.getNextRunAt());
            statement.setLong(10, job.getLastRunAt());
            statement.setLong(11, job.getCreatedAt());
            statement.setLong(12, job.getUpdatedAt());
            statement.executeUpdate();
            statement.close();
            return job;
        } finally {
            connection.close();
        }
    }

    public CronJobRecord findById(String jobId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select * from cron_jobs where job_id = ?");
            statement.setString(1, jobId);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return map(resultSet);
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }

        return null;
    }

    public List<CronJobRecord> listBySource(String sourceKey) throws Exception {
        List<CronJobRecord> jobs = new ArrayList<CronJobRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from cron_jobs where source_key = ? order by updated_at desc");
            statement.setString(1, sourceKey);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    jobs.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }

        return jobs;
    }

    @Override
    public List<CronJobRecord> listAll() throws Exception {
        List<CronJobRecord> jobs = new ArrayList<CronJobRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select * from cron_jobs order by updated_at desc");
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    jobs.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return jobs;
    }

    public List<CronJobRecord> listDue(long nowEpochMillis) throws Exception {
        List<CronJobRecord> jobs = new ArrayList<CronJobRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from cron_jobs where status = 'ACTIVE' and next_run_at <= ? order by next_run_at asc");
            statement.setLong(1, nowEpochMillis);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    jobs.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }

        return jobs;
    }

    public void delete(String jobId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("delete from cron_jobs where job_id = ?");
            statement.setString(1, jobId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public void updateStatus(String jobId, String status) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update cron_jobs set status = ?, updated_at = ? where job_id = ?");
            statement.setString(1, status);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, jobId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public void markRun(String jobId, long lastRunAt, long nextRunAt) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update cron_jobs set last_run_at = ?, next_run_at = ?, updated_at = ? where job_id = ?");
            statement.setLong(1, lastRunAt);
            statement.setLong(2, nextRunAt);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, jobId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    private CronJobRecord map(ResultSet resultSet) throws Exception {
        CronJobRecord record = new CronJobRecord();
        record.setJobId(resultSet.getString("job_id"));
        record.setName(resultSet.getString("name"));
        record.setCronExpr(resultSet.getString("cron_expr"));
        record.setPrompt(resultSet.getString("prompt"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setDeliverPlatform(resultSet.getString("deliver_platform"));
        record.setDeliverChatId(resultSet.getString("deliver_chat_id"));
        record.setStatus(resultSet.getString("status"));
        record.setNextRunAt(resultSet.getLong("next_run_at"));
        record.setLastRunAt(resultSet.getLong("last_run_at"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setUpdatedAt(resultSet.getLong("updated_at"));
        return record;
    }
}
