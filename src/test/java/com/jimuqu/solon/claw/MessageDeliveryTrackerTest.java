package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.tool.runtime.MessageDeliveryTracker;
import org.junit.jupiter.api.Test;

public class MessageDeliveryTrackerTest {
    @Test
    void shouldSuppressDuplicateFinalReplyForSameSourceMediaEcho() {
        MessageDeliveryTracker.recordEcho(
                "WEIXIN:chat-a:user-a",
                PlatformType.WEIXIN,
                "chat-a",
                PlatformType.WEIXIN,
                "chat-a",
                "百度首页截图发你了。",
                true);

        boolean suppressed =
                MessageDeliveryTracker.consumeDuplicateFinalReply(
                        "WEIXIN:chat-a:user-a", "百度首页截图发你了。");

        assertThat(suppressed).isTrue();
    }

    @Test
    void shouldNotSuppressWhenReplyDiffers() {
        MessageDeliveryTracker.recordEcho(
                "WEIXIN:chat-a:user-a",
                PlatformType.WEIXIN,
                "chat-a",
                PlatformType.WEIXIN,
                "chat-a",
                "百度首页截图发你了。",
                true);

        boolean suppressed =
                MessageDeliveryTracker.consumeDuplicateFinalReply(
                        "WEIXIN:chat-a:user-a", "截图已经发送，同时我还帮你裁切了顶部导航。");

        assertThat(suppressed).isFalse();
    }
}
