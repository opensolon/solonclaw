package com.jimuqu.solon.claw.storage.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;

/** SqlitePreferenceStore 实现。 */
@RequiredArgsConstructor
public class SqlitePreferenceStore {
    public static final String GLOBAL_SCOPE = "__global__";

    private final SqliteDatabase database;

    public boolean isToolEnabled(String sourceKey, String toolName) throws SQLException {
        return readScopedBoolean("tool_toggles", "tool_name", sourceKey, toolName, true);
    }

    public void setToolEnabled(String sourceKey, String toolName, boolean enabled)
            throws SQLException {
        writeBoolean("tool_toggles", "tool_name", sourceKey, toolName, enabled);
    }

    public boolean isToolEnabledGlobal(String toolName) throws SQLException {
        return readBoolean("tool_toggles", "tool_name", GLOBAL_SCOPE, toolName, true);
    }

    public void setToolEnabledGlobal(String toolName, boolean enabled) throws SQLException {
        writeBoolean("tool_toggles", "tool_name", GLOBAL_SCOPE, toolName, enabled);
    }

    public boolean isSkillEnabled(String sourceKey, String skillName) throws SQLException {
        return readScopedBoolean("skill_states", "skill_name", sourceKey, skillName, true);
    }

    public void setSkillEnabled(String sourceKey, String skillName, boolean enabled)
            throws SQLException {
        writeBoolean("skill_states", "skill_name", sourceKey, skillName, enabled);
    }

    public boolean isSkillEnabledGlobal(String skillName) throws SQLException {
        return readBoolean("skill_states", "skill_name", GLOBAL_SCOPE, skillName, true);
    }

    public void setSkillEnabledGlobal(String skillName, boolean enabled) throws SQLException {
        writeBoolean("skill_states", "skill_name", GLOBAL_SCOPE, skillName, enabled);
    }

    private boolean readScopedBoolean(
            String tableName,
            String nameColumn,
            String sourceKey,
            String nameValue,
            boolean defaultValue)
            throws SQLException {
        Boolean scoped = readBooleanIfPresent(tableName, nameColumn, sourceKey, nameValue);
        if (scoped != null) {
            return scoped.booleanValue();
        }

        Boolean global = readBooleanIfPresent(tableName, nameColumn, GLOBAL_SCOPE, nameValue);
        if (global != null) {
            return global.booleanValue();
        }

        return defaultValue;
    }

    private boolean readBoolean(
            String tableName,
            String nameColumn,
            String sourceKey,
            String nameValue,
            boolean defaultValue)
            throws SQLException {
        Boolean value = readBooleanIfPresent(tableName, nameColumn, sourceKey, nameValue);
        return value == null ? defaultValue : value.booleanValue();
    }

    private Boolean readBooleanIfPresent(
            String tableName, String nameColumn, String sourceKey, String nameValue)
            throws SQLException {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select enabled from "
                                    + tableName
                                    + " where source_key = ? and "
                                    + nameColumn
                                    + " = ?");
            statement.setString(1, sourceKey);
            statement.setString(2, nameValue);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return resultSet.getInt(1) == 1;
                }
                return null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private void writeBoolean(
            String tableName,
            String nameColumn,
            String sourceKey,
            String nameValue,
            boolean enabled)
            throws SQLException {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into "
                                    + tableName
                                    + " (source_key, "
                                    + nameColumn
                                    + ", enabled) values (?, ?, ?)");
            statement.setString(1, sourceKey);
            statement.setString(2, nameValue);
            statement.setInt(3, enabled ? 1 : 0);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }
}
