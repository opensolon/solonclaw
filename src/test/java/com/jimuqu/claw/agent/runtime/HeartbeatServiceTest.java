package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.channel.ChannelAdapter;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.AgentRuntimeService;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.HeartbeatService;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证心跳服务只会触发静默内部运行，不会直接向外部渠道发送消息。
 */
class HeartbeatServiceTest {
    /** 临时测试目录。 */
    @TempDir
    Path tempDir;

    /**
     * 验证一次心跳轮询会触发静默内部运行。
     *
     * @throws Exception 执行异常
     */
    @Test
    void tickRunsHeartbeatSilentlyWithoutOutboundReply() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        FileUtil.mkdir(workspace.toFile());
        FileUtil.writeUtf8String("请汇报当前状态", workspace.resolve("HEARTBEAT.md").toFile());

        RuntimeStoreService store = new RuntimeStoreService(tempDir.resolve("runtime").toFile());
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-9", "user-9");
        store.rememberReplyTarget("dingtalk:group:group-9", replyTarget);

        CountDownLatch executed = new CountDownLatch(1);
        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            executed.countDown();
            return "heartbeat:" + request.getCurrentMessage();
        };
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        SolonClawProperties properties = new SolonClawProperties();
        properties.setWorkspace(workspace.toString());

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            HeartbeatService heartbeatService = new HeartbeatService(runtimeService, store, properties);

            heartbeatService.tick();

            assertTrue(executed.await(3, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertTrue(adapter.messages.isEmpty());
            assertEquals(0, store.readConversationEvents("dingtalk:group:group-9").size());
        } finally {
            scheduler.shutdown();
        }
    }

    /**
     * 验证只有注释的 HEARTBEAT.md 不会触发心跳运行。
     */
    @Test
    void tickIgnoresCommentOnlyHeartbeatFile() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        FileUtil.mkdir(workspace.toFile());
        FileUtil.writeUtf8String(
                "# HEARTBEAT.md\n\n# 保持此文件为空（或仅包含注释）以跳过心跳 API 调用。\n\n# 默认注释不应触发心跳。\n",
                workspace.resolve("HEARTBEAT.md").toFile()
        );

        RuntimeStoreService store = new RuntimeStoreService(tempDir.resolve("runtime").toFile());
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-9", "user-9");
        store.rememberReplyTarget("dingtalk:group:group-9", replyTarget);

        CountDownLatch executed = new CountDownLatch(1);
        ConversationAgent conversationAgent = (request, progressConsumer) -> {
            executed.countDown();
            return "heartbeat:" + request.getCurrentMessage();
        };
        ConversationScheduler scheduler = new ConversationScheduler(1);
        ChannelRegistry registry = new ChannelRegistry();
        RecordingChannelAdapter adapter = new RecordingChannelAdapter();
        registry.register(adapter);

        SolonClawProperties properties = new SolonClawProperties();
        properties.setWorkspace(workspace.toString());

        try {
            AgentRuntimeService runtimeService = new AgentRuntimeService(conversationAgent, store, scheduler, registry, properties);
            HeartbeatService heartbeatService = new HeartbeatService(runtimeService, store, properties);

            heartbeatService.tick();

            assertTrue(!executed.await(500, TimeUnit.MILLISECONDS));
            Thread.sleep(200);
            assertTrue(adapter.messages.isEmpty());
            assertEquals(0, store.readConversationEvents("dingtalk:group:group-9").size());
        } finally {
            scheduler.shutdown();
        }
    }

    /**
     * 记录测试发送消息的伪渠道适配器。
     */
    private static class RecordingChannelAdapter implements ChannelAdapter {
        /** 收到的消息列表。 */
        private final List<String> messages = new CopyOnWriteArrayList<>();

        /**
         * 返回适配器渠道类型。
         *
         * @return 钉钉渠道
         */
        @Override
        public ChannelType channelType() {
            return ChannelType.DINGTALK;
        }

        /**
         * 记录发送内容。
         *
         * @param outboundEnvelope 出站消息
         */
        @Override
        public void send(OutboundEnvelope outboundEnvelope) {
            messages.add(outboundEnvelope.getContent());
        }
    }
}

