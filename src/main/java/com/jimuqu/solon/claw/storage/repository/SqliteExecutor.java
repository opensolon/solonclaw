package com.jimuqu.solon.claw.storage.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Lightweight SQLite execution helper with consistent resource handling. */
public class SqliteExecutor {
    private final SqliteDatabase database;

    public SqliteExecutor(SqliteDatabase database) {
        this.database = database;
    }

    public <T> T query(String sql, Binder binder, RowMapper<T> mapper) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            try {
                if (binder != null) {
                    binder.bind(statement);
                }
                ResultSet resultSet = statement.executeQuery();
                try {
                    return mapper.map(resultSet);
                } finally {
                    resultSet.close();
                }
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public int update(String sql, Binder binder) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            try {
                if (binder != null) {
                    binder.bind(statement);
                }
                return statement.executeUpdate();
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public <T> T transaction(TransactionCallback<T> callback) throws Exception {
        Connection connection = database.openConnection();
        boolean oldAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            T result = callback.doInTransaction(connection);
            connection.commit();
            return result;
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
            connection.close();
        }
    }

    public interface Binder {
        void bind(PreparedStatement statement) throws Exception;
    }

    public interface RowMapper<T> {
        T map(ResultSet resultSet) throws Exception;
    }

    public interface TransactionCallback<T> {
        T doInTransaction(Connection connection) throws Exception;
    }
}
