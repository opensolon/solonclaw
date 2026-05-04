package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.PlatformAdminRecord;
import com.jimuqu.solon.claw.support.TestEnvironment;
import org.junit.jupiter.api.Test;

public class PlatformAdminBootstrapTest {
    @Test
    void shouldBootstrapSingleAdminFromFirstPrivateUser() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        GatewayReply groupReply =
                env.gatewayService.handle(
                        env.message(
                                "group-1",
                                "user-a",
                                "group",
                                "Dev Group",
                                "Alice",
                                "hello from group"));
        assertThat(groupReply).isNull();

        GatewayReply dmPrompt =
                env.gatewayService.handle(
                        env.message("dm-1", "user-a", "dm", "Alice DM", "Alice", "hello"));
        assertThat(dmPrompt.getContent()).contains("/pairing claim-admin");

        GatewayReply secondUserPrompt =
                env.gatewayService.handle(
                        env.message("dm-2", "user-b", "dm", "Bob DM", "Bob", "hello"));
        assertThat(secondUserPrompt.getContent()).contains("等待首个私聊认领人");

        GatewayReply claimReply =
                env.gatewayService.handle(
                        env.message(
                                "dm-1",
                                "user-a",
                                "dm",
                                "Alice DM",
                                "Alice",
                                "/pairing claim-admin"));
        assertThat(claimReply.getContent()).contains("唯一管理员");

        PlatformAdminRecord admin =
                env.gatewayPolicyRepository.getPlatformAdmin(PlatformType.MEMORY);
        assertThat(admin).isNotNull();
        assertThat(admin.getUserId()).isEqualTo("user-a");

        GatewayReply secondClaim =
                env.gatewayService.handle(
                        env.message(
                                "dm-2", "user-b", "dm", "Bob DM", "Bob", "/pairing claim-admin"));
        assertThat(secondClaim.getContent()).contains("pairing code");
    }
}
