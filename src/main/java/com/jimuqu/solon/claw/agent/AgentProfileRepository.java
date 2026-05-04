package com.jimuqu.solon.claw.agent;

import java.util.List;

public interface AgentProfileRepository {
    AgentProfile save(AgentProfile profile) throws Exception;

    AgentProfile findByName(String agentName) throws Exception;

    List<AgentProfile> listAll() throws Exception;

    void deleteByName(String agentName) throws Exception;
}
