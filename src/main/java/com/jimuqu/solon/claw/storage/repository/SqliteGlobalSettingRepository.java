package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import lombok.RequiredArgsConstructor;

/** SQLite 全局设置仓储。 */
@RequiredArgsConstructor
public class SqliteGlobalSettingRepository implements GlobalSettingRepository {
    private final SqliteDatabase database;

    @Override
    public String get(String key) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select setting_value from global_settings where setting_key = ?");
            statement.setString(1, key);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? resultSet.getString(1) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public void set(String key, String value) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into global_settings (setting_key, setting_value, updated_at) values (?, ?, ?)");
            statement.setString(1, key);
            statement.setString(2, value == null ? "" : value);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public void remove(String key) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "delete from global_settings where setting_key = ?");
            statement.setString(1, key);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }
}
