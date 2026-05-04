package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.storage.session.SqliteAgentSession;
import com.jimuqu.solon.claw.support.TestEnvironment;
import org.junit.jupiter.api.Test;

public class DangerousCommandApprovalCommandTest {
    @Test
    void shouldApproveDangerousCommandForSessionAndResume() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.gatewayService.handle(env.message("room-1", "user-1", "hello"));
        env.gatewayAuthorizationService.claimAdmin(
                env.message("room-1", "user-1", "/pairing claim-admin"));

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-1:user-1");
        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        env.dangerousCommandApprovalService.storePendingApproval(
                agentSession,
                "execute_shell",
                "recursive_delete",
                "recursive delete",
                "rm -rf runtime/cache");

        GatewayReply reply = env.send("room-1", "user-1", "/approve session");
        SessionRecord updated = env.sessionRepository.getBoundSession("MEMORY:room-1:user-1");
        SqliteAgentSession updatedAgentSession =
                new SqliteAgentSession(updated, env.sessionRepository);

        assertThat(reply.getContent()).isEqualTo("echo:resume");
        assertThat(env.dangerousCommandApprovalService.getPendingApproval(updatedAgentSession))
                .isNull();
        assertThat(
                        env.dangerousCommandApprovalService.isSessionApproved(
                                updatedAgentSession, "recursive_delete"))
                .isTrue();
        assertThat(
                        env.dangerousCommandApprovalService.isSessionApproved(
                                updatedAgentSession,
                                "execute_shell",
                                "recursive_delete",
                                "rm -rf runtime/cache"))
                .isTrue();
        assertThat(
                        env.dangerousCommandApprovalService.isSessionApproved(
                                updatedAgentSession,
                                "execute_shell",
                                "recursive_delete",
                                "rm -rf runtime/logs"))
                .isTrue();
    }

    @Test
    void shouldPersistAlwaysApprovalPattern() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.gatewayService.handle(env.message("room-2", "user-2", "hello"));
        env.gatewayAuthorizationService.claimAdmin(
                env.message("room-2", "user-2", "/pairing claim-admin"));

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-2:user-2");
        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        env.dangerousCommandApprovalService.storePendingApproval(
                agentSession,
                "execute_shell",
                "recursive_delete",
                "recursive delete",
                "rm -rf runtime/cache");

        GatewayReply reply = env.send("room-2", "user-2", "/approve always");

        assertThat(reply.getContent()).isEqualTo("echo:resume");
        assertThat(env.dangerousCommandApprovalService.isAlwaysApproved("recursive_delete"))
                .isTrue();
        assertThat(
                        env.dangerousCommandApprovalService.isAlwaysApproved(
                                "execute_shell", "recursive_delete", "rm -rf runtime/cache"))
                .isTrue();
        assertThat(
                        env.dangerousCommandApprovalService.isAlwaysApproved(
                                "execute_shell", "recursive_delete", "rm -rf runtime/logs"))
                .isTrue();
    }

    @Test
    void shouldSkipNewRunWhenDangerousApprovalIsPending() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.gatewayService.handle(env.message("room-4", "user-4", "hello"));
        env.gatewayAuthorizationService.claimAdmin(
                env.message("room-4", "user-4", "/pairing claim-admin"));

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-4:user-4");
        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        env.dangerousCommandApprovalService.storePendingApproval(
                agentSession,
                "execute_shell",
                "git_reset_hard",
                "git reset --hard (destroys uncommitted changes)",
                "git reset --hard origin/main");

        GatewayReply blocked =
                env.conversationOrchestrator.runScheduled(
                        env.message("room-4", "user-4", "执行日志巡检"));
        SessionRecord afterBlockedRun =
                env.sessionRepository.getBoundSession("MEMORY:room-4:user-4");
        SqliteAgentSession afterBlockedAgentSession =
                new SqliteAgentSession(afterBlockedRun, env.sessionRepository);

        assertThat(blocked.getContent()).contains("待审批的危险命令");
        assertThat(blocked.getContent()).contains("本次请求已跳过");
        assertThat(afterBlockedRun.getNdjson()).doesNotContain("执行日志巡检");
        assertThat(env.dangerousCommandApprovalService.getPendingApproval(afterBlockedAgentSession))
                .isNotNull();

        GatewayReply approved = env.send("room-4", "user-4", "/approve always");
        assertThat(approved.getContent()).isEqualTo("echo:resume");
        assertThat(
                        env.dangerousCommandApprovalService.isAlwaysApproved(
                                "execute_shell", "git_reset_hard", "git reset --hard origin/main"))
                .isTrue();
    }

    @Test
    void shouldReturnChineseMessageWhenApproveHasNoPendingCommand() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.gatewayService.handle(env.message("room-3", "user-3", "hello"));
        env.gatewayAuthorizationService.claimAdmin(
                env.message("room-3", "user-3", "/pairing claim-admin"));

        GatewayReply reply = env.send("room-3", "user-3", "/approve always");

        assertThat(reply.getContent()).contains("待审批的危险命令");
        assertThat(reply.getContent()).doesNotContain("???");
    }
}
