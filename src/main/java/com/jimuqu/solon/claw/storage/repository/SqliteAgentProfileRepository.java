package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.agent.AgentProfile;
import com.jimuqu.solon.claw.agent.AgentProfileRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SqliteAgentProfileRepository implements AgentProfileRepository {
    private final SqliteDatabase database;

    @Override
    public AgentProfile save(AgentProfile profile) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into agent_profiles (agent_name, display_name, description, role_prompt, default_model, model, allowed_tools_json, skills_json, memory, enabled, last_used_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, profile.getAgentName());
            statement.setString(2, profile.getDisplayName());
            statement.setString(3, profile.getDescription());
            statement.setString(4, profile.getRolePrompt());
            statement.setString(5, profile.getDefaultModel());
            statement.setString(6, profile.getDefaultModel());
            statement.setString(7, profile.getAllowedToolsJson());
            statement.setString(8, profile.getSkillsJson());
            statement.setString(9, profile.getMemory());
            statement.setInt(10, profile.isEnabled() ? 1 : 0);
            statement.setLong(11, profile.getLastUsedAt());
            statement.setLong(12, profile.getCreatedAt());
            statement.setLong(13, profile.getUpdatedAt());
            statement.executeUpdate();
            statement.close();
            return profile;
        } finally {
            connection.close();
        }
    }

    @Override
    public AgentProfile findByName(String agentName) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_profiles where agent_name = ?");
            statement.setString(1, agentName);
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

    @Override
    public List<AgentProfile> listAll() throws Exception {
        List<AgentProfile> profiles = new ArrayList<AgentProfile>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_profiles order by updated_at desc");
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    profiles.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return profiles;
    }

    @Override
    public void deleteByName(String agentName) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("delete from agent_profiles where agent_name = ?");
            statement.setString(1, agentName);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    private AgentProfile map(ResultSet rs) throws Exception {
        AgentProfile profile = new AgentProfile();
        profile.setAgentName(rs.getString("agent_name"));
        profile.setDisplayName(rs.getString("display_name"));
        profile.setDescription(rs.getString("description"));
        profile.setRolePrompt(rs.getString("role_prompt"));
        profile.setDefaultModel(rs.getString("default_model"));
        profile.setAllowedToolsJson(rs.getString("allowed_tools_json"));
        profile.setSkillsJson(rs.getString("skills_json"));
        profile.setMemory(rs.getString("memory"));
        profile.setEnabled(rs.getInt("enabled") != 0);
        profile.setLastUsedAt(rs.getLong("last_used_at"));
        profile.setCreatedAt(rs.getLong("created_at"));
        profile.setUpdatedAt(rs.getLong("updated_at"));
        return profile;
    }
}
