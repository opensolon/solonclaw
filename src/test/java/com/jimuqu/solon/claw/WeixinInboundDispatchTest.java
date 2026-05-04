package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.core.service.InboundMessageHandler;
import com.jimuqu.solon.claw.gateway.platform.weixin.WeiXinChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

public class WeixinInboundDispatchTest {
    @Test
    void shouldSplitLongOutboundTextAtWeixinSafeLimit() throws Exception {
        WeiXinChannelAdapter adapter = newAdapter();
        Method split =
                WeiXinChannelAdapter.class.getDeclaredMethod("splitTextForDelivery", String.class);
        split.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> chunks = (List<String>) split.invoke(adapter, repeat("a", 2001));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(2000);
        assertThat(chunks.get(1)).hasSize(1);
    }

    @Test
    void shouldStillSplitMultilineTextByLineWhenConfigured() throws Exception {
        AppConfig config = newConfig();
        config.getChannels().getWeixin().setSplitMultilineMessages(true);
        WeiXinChannelAdapter adapter = newAdapter(config);
        Method split =
                WeiXinChannelAdapter.class.getDeclaredMethod("splitTextForDelivery", String.class);
        split.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> chunks = (List<String>) split.invoke(adapter, "第一行\n\n第二行");

        assertThat(chunks).containsExactly("第一行", "第二行");
    }

    @Test
    void shouldDispatchInboundOffThePollingThread() throws Exception {
        AppConfig config = newConfig();
        config.getChannels().getWeixin().setEnabled(true);
        config.getChannels().getWeixin().setAccountId("wx-bot");
        config.getChannels().getWeixin().setGroupPolicy("open");

        WeiXinChannelAdapter adapter =
                new WeiXinChannelAdapter(
                        config.getChannels().getWeixin(),
                        new InMemoryChannelStateRepository(),
                        new AttachmentCacheService(config));

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> handlerThread = new AtomicReference<String>();
        final String callerThread = Thread.currentThread().getName();
        adapter.setInboundMessageHandler(
                new InboundMessageHandler() {
                    @Override
                    public void handle(com.jimuqu.solon.claw.core.model.GatewayMessage message) {
                        handlerThread.set(Thread.currentThread().getName());
                        latch.countDown();
                    }
                });

        Method processInbound =
                WeiXinChannelAdapter.class.getDeclaredMethod("processInboundMessage", ONode.class);
        processInbound.setAccessible(true);
        processInbound.invoke(
                adapter,
                ONode.ofJson(
                        "{"
                                + "\"from_user_id\":\"wx-user\","
                                + "\"message_id\":\"msg-1\","
                                + "\"room_id\":\"room-1\","
                                + "\"item_list\":[{\"type\":1,\"text_item\":{\"text\":\"hello\"}}]"
                                + "}"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(handlerThread.get()).isNotBlank();
        assertThat(handlerThread.get()).isNotEqualTo(callerThread);

        adapter.disconnect();
    }

    private WeiXinChannelAdapter newAdapter() throws Exception {
        return newAdapter(newConfig());
    }

    private WeiXinChannelAdapter newAdapter(AppConfig config) {
        return new WeiXinChannelAdapter(
                config.getChannels().getWeixin(),
                new InMemoryChannelStateRepository(),
                new AttachmentCacheService(config));
    }

    private String repeat(String text, int count) {
        StringBuilder builder = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(text);
        }
        return builder.toString();
    }

    private AppConfig newConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-weixin-dispatch-test").toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.getRuntime().setContextDir(new File(runtimeHome, "context").getAbsolutePath());
        config.getRuntime().setSkillsDir(new File(runtimeHome, "skills").getAbsolutePath());
        config.getRuntime().setCacheDir(new File(runtimeHome, "cache").getAbsolutePath());
        config.getRuntime()
                .setStateDb(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        config.getRuntime().setConfigFile(new File(runtimeHome, "config.yml").getAbsolutePath());
        config.getRuntime().setLogsDir(new File(runtimeHome, "logs").getAbsolutePath());
        return config;
    }

    private static class InMemoryChannelStateRepository implements ChannelStateRepository {
        @Override
        public String get(PlatformType platform, String scopeKey, String stateKey) {
            return null;
        }

        @Override
        public void put(
                PlatformType platform, String scopeKey, String stateKey, String stateValue) {}

        @Override
        public void delete(PlatformType platform, String scopeKey, String stateKey) {}

        @Override
        public List<StateItem> list(PlatformType platform, String scopeKey) {
            return Collections.emptyList();
        }
    }
}
