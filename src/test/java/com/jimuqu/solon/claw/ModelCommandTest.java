package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.support.TestEnvironment;
import org.junit.jupiter.api.Test;

public class ModelCommandTest {
    @Test
    void shouldShowAndSetSessionAndGlobalModel() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");

        GatewayReply showReply = env.send("admin-chat", "admin-user", "/model");
        assertThat(showReply.getContent()).contains("current.provider=").contains("global.model=");

        GatewayReply sessionReply = env.send("admin-chat", "admin-user", "/model default:gpt-5.2");
        assertThat(sessionReply.getContent()).contains("default:gpt-5.2");
        assertThat(
                        env.sessionRepository
                                .getBoundSession("MEMORY:admin-chat:admin-user")
                                .getModelOverride())
                .isEqualTo("default:gpt-5.2");

        GatewayReply globalReply =
                env.send("admin-chat", "admin-user", "/model --global default:claude-sonnet-4");
        assertThat(globalReply.getContent()).contains("default:claude-sonnet-4");
        assertThat(env.appConfig.getLlm().getProvider()).isEqualTo("default");
        assertThat(env.appConfig.getLlm().getModel()).isEqualTo("claude-sonnet-4");
    }
}
