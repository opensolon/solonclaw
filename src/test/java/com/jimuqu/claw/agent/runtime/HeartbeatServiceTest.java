package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.enums.SystemEventPolicy;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.runtime.api.ConversationAgent;
import com.jimuqu.claw.agent.runtime.impl.ConversationScheduler;
import com.jimuqu.claw.agent.runtime.impl.HeartbeatService;
import com.jimuqu.claw.agent.runtime.impl.SystemEventRunner;
import com.jimuqu.claw.agent.runtime.support.SystemEventRequest;
import com.jimuqu.claw.agent.store.RuntimeStoreService;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HeartbeatServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void tickSubmitsHeartbeatEventToSystemEventRunner() {
        Path workspace = tempDir.resolve("workspace");
        FileUtil.mkdir(workspace.toFile());
        FileUtil.writeUtf8String("请汇报当前状态", workspace.resolve("HEARTBEAT.md").toFile());

        RuntimeStoreService store = new RuntimeStoreService(tempDir.resolve("runtime").toFile());
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-9", "user-9");
        store.rememberReplyTarget("dingtalk:group:group-9", replyTarget);

        SolonClawProperties properties = new SolonClawProperties();
        properties.setWorkspace(workspace.toString());

        ConversationScheduler scheduler = new ConversationScheduler(1);
        try {
            CapturingSystemEventRunner runner = new CapturingSystemEventRunner(store, scheduler, properties);
            HeartbeatService heartbeatService = new HeartbeatService(runner, store, properties);

            heartbeatService.tick();

            SystemEventRequest request = runner.lastRequest.get();
            assertNotNull(request);
            assertEquals(RuntimeSourceKind.HEARTBEAT_EVENT, request.getSourceKind());
            assertEquals(SystemEventPolicy.INTERNAL_ONLY, request.getPolicy());
            assertEquals("请汇报当前状态", request.getContent());
            assertEquals("dingtalk:group:group-9", request.getSessionKey());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void tickIgnoresCommentOnlyHeartbeatFile() {
        Path workspace = tempDir.resolve("workspace-comment-only");
        FileUtil.mkdir(workspace.toFile());
        FileUtil.writeUtf8String(
                "# HEARTBEAT.md\n\n# 保持此文件为空（或仅包含注释）以跳过心跳 API 调用。\n\n# 默认注释不应触发心跳。\n",
                workspace.resolve("HEARTBEAT.md").toFile()
        );

        RuntimeStoreService store = new RuntimeStoreService(tempDir.resolve("runtime-comment-only").toFile());
        ReplyTarget replyTarget = new ReplyTarget(ChannelType.DINGTALK, ConversationType.GROUP, "group-10", "user-10");
        store.rememberReplyTarget("dingtalk:group:group-10", replyTarget);

        SolonClawProperties properties = new SolonClawProperties();
        properties.setWorkspace(workspace.toString());

        ConversationScheduler scheduler = new ConversationScheduler(1);
        try {
            CapturingSystemEventRunner runner = new CapturingSystemEventRunner(store, scheduler, properties);
            HeartbeatService heartbeatService = new HeartbeatService(runner, store, properties);

            heartbeatService.tick();

            assertNull(runner.lastRequest.get());
        } finally {
            scheduler.shutdown();
        }
    }

    private static class CapturingSystemEventRunner extends SystemEventRunner {
        private final AtomicReference<SystemEventRequest> lastRequest = new AtomicReference<SystemEventRequest>();

        private CapturingSystemEventRunner(
                RuntimeStoreService runtimeStoreService,
                ConversationScheduler conversationScheduler,
                SolonClawProperties properties
        ) {
            super(
                    (ConversationAgent) (request, progressConsumer) -> "noop",
                    runtimeStoreService,
                    conversationScheduler,
                    new ChannelRegistry(),
                    properties
            );
        }

        @Override
        public String submit(SystemEventRequest request) {
            lastRequest.set(request);
            return "captured-heartbeat";
        }
    }
}
