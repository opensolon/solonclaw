package com.jimuqu.solon.claw.tool.runtime;

import com.jimuqu.solon.claw.agent.AgentProfileService;
import com.jimuqu.solon.claw.core.model.ToolResultEnvelope;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** Agent 管理工具。 */
@RequiredArgsConstructor
public class AgentTools {
    private final AgentProfileService agentProfileService;
    private final SessionRepository sessionRepository;
    private final String sourceKey;

    @ToolMapping(
            name = "agent_manage",
            description =
                    "Manage named Agents with slash-command compatible args: list, use <name>, create <name> [role], show <name>, model/tools/skills/memory <name> ..., delete <name>. The built-in default Agent cannot be edited or deleted.")
    public String agentManage(
            @Param(
                            name = "args",
                            description =
                                    "Agent command arguments, for example: list, create coder 你是代码助手, use coder, tools coder file_read,skills_list")
                    String args) {
        try {
            String result = agentProfileService.handleCommand(args, sessionRepository, sourceKey);
            return ToolResultEnvelope.ok("Agent 管理完成").preview(result).toJson();
        } catch (Exception e) {
            return ToolResultEnvelope.error(
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    .toJson();
        }
    }
}
