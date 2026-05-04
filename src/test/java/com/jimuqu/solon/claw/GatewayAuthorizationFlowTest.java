package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.support.TestEnvironment;
import org.junit.jupiter.api.Test;

public class GatewayAuthorizationFlowTest {
    @Test
    void shouldRequirePairingForSecondUserAfterAdminBootstrap() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");

        GatewayReply pairPrompt = env.send("user-chat", "user-2", "hi there");
        assertThat(pairPrompt.getContent()).contains("pairing code");
        String code = pairPrompt.getContent().split("`")[1];

        GatewayReply groupUnauthorized =
                env.gatewayService.handle(
                        env.message(
                                "group-1", "user-2", "group", "Dev Group", "Bob", "hello group"));
        assertThat(groupUnauthorized).isNull();

        GatewayReply pending = env.send("admin-chat", "admin-user", "/pairing pending memory");
        assertThat(pending.getContent()).contains(code);

        GatewayReply approve =
                env.send("admin-chat", "admin-user", "/pairing approve memory " + code);
        assertThat(approve.getContent()).contains("已批准");

        GatewayReply normalReply = env.send("user-chat", "user-2", "hello after pairing");
        assertThat(normalReply.getContent()).contains("echo:hello after pairing");
    }
}
