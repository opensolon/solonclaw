package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.PlatformAdminRecord;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.support.constants.GatewayBehaviorConstants;
import org.junit.jupiter.api.Test;

public class GatewayAuthorizationServiceTest {
    @Test
    void shouldHonorQqbotChannelAllowAllUsers() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getChannels().getQqbot().setAllowAllUsers(true);

        GatewayMessage message = new GatewayMessage(PlatformType.QQBOT, "chat", "qq-user", "hello");

        assertThat(env.gatewayAuthorizationService.isAuthorized(message)).isTrue();
    }

    @Test
    void shouldHonorYuanbaoChannelAllowlistAndUnauthorizedBehavior() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getChannels().getYuanbao().getAllowedUsers().add("allowed-user");
        env.appConfig
                .getChannels()
                .getYuanbao()
                .setUnauthorizedDmBehavior(
                        GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE);
        createAdmin(env, PlatformType.YUANBAO);

        GatewayMessage allowed =
                new GatewayMessage(PlatformType.YUANBAO, "chat", "allowed-user", "hello");
        GatewayMessage stranger =
                new GatewayMessage(PlatformType.YUANBAO, "chat", "stranger", "hello");

        assertThat(env.gatewayAuthorizationService.isAuthorized(allowed)).isTrue();
        GatewayReply preAuth = env.gatewayAuthorizationService.preAuthorize(stranger);
        assertThat(preAuth).isNull();
    }

    private void createAdmin(TestEnvironment env, PlatformType platform) throws Exception {
        PlatformAdminRecord admin = new PlatformAdminRecord();
        admin.setPlatform(platform);
        admin.setUserId("admin-user");
        admin.setUserName("admin");
        admin.setChatId("admin-chat");
        admin.setCreatedAt(System.currentTimeMillis());
        env.gatewayPolicyRepository.createPlatformAdminIfAbsent(admin);
    }
}
