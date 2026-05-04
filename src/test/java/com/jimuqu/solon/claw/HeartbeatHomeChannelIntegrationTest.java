package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.context.PersonaWorkspaceService;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.scheduler.HeartbeatScheduler;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HeartbeatHomeChannelIntegrationTest {
    @Test
    void shouldUseHomeChannelConfiguredBySetHome() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getAgent().getHeartbeat().setIntervalMinutes(30);
        env.appConfig.getChannels().getFeishu().setEnabled(true);

        GatewayMessage initDm =
                new GatewayMessage(PlatformType.FEISHU, "dm-admin", "admin-user", "hello");
        env.gatewayAuthorizationService.preAuthorize(initDm);
        GatewayMessage adminDm =
                new GatewayMessage(
                        PlatformType.FEISHU, "dm-admin", "admin-user", "/pairing claim-admin");
        GatewayReply claim = env.gatewayAuthorizationService.claimAdmin(adminDm);
        assertThat(claim.getContent()).contains("唯一管理员");

        GatewayMessage group =
                new GatewayMessage(
                        PlatformType.FEISHU, "group-heartbeat", "admin-user", "/sethome");
        group.setChatType("group");
        group.setChatName("Heartbeat Group");
        group.setUserName("Alice");
        GatewayReply sethome = env.gatewayAuthorizationService.setHome(group);
        assertThat(sethome.getContent()).contains("Home Channel");

        PersonaWorkspaceService workspaceService = new PersonaWorkspaceService(env.appConfig);
        workspaceService.write("heartbeat", "- 检查今天是否有需要提醒的事项");

        CapturingOrchestrator orchestrator = new CapturingOrchestrator();
        CapturingDeliveryService deliveryService = new CapturingDeliveryService();
        HeartbeatScheduler scheduler =
                new HeartbeatScheduler(
                        env.appConfig,
                        env.gatewayPolicyRepository,
                        orchestrator,
                        deliveryService,
                        workspaceService);

        scheduler.tick();

        assertThat(orchestrator.calls).isEqualTo(1);
        assertThat(orchestrator.lastMessage).isNotNull();
        assertThat(orchestrator.lastMessage.sourceKey())
                .isEqualTo("FEISHU:group-heartbeat:__heartbeat__");
        assertThat(deliveryService.requests).hasSize(1);
        assertThat(deliveryService.requests.get(0).getPlatform()).isEqualTo(PlatformType.FEISHU);
        assertThat(deliveryService.requests.get(0).getChatId()).isEqualTo("group-heartbeat");
        assertThat(deliveryService.requests.get(0).getText()).isEqualTo("heartbeat alert");
    }

    private static class CapturingOrchestrator
            implements com.jimuqu.solon.claw.core.service.ConversationOrchestrator {
        private int calls;
        private GatewayMessage lastMessage;

        @Override
        public GatewayReply handleIncoming(GatewayMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GatewayReply runScheduled(GatewayMessage syntheticMessage) {
            calls++;
            lastMessage = syntheticMessage;
            return GatewayReply.ok("heartbeat alert");
        }

        @Override
        public GatewayReply resumePending(String sourceKey) {
            throw new UnsupportedOperationException();
        }
    }

    private static class CapturingDeliveryService
            implements com.jimuqu.solon.claw.core.service.DeliveryService {
        private final List<DeliveryRequest> requests = new ArrayList<DeliveryRequest>();

        @Override
        public void deliver(DeliveryRequest request) {
            requests.add(request);
        }

        @Override
        public List<com.jimuqu.solon.claw.core.model.ChannelStatus> statuses() {
            return new ArrayList<com.jimuqu.solon.claw.core.model.ChannelStatus>();
        }
    }
}
