package com.jimuqu.claw.channel.dingtalk;

import com.alibaba.fastjson.JSONObject;
import com.jimuqu.claw.channel.dingtalk.sender.DingTalkRobotSender;
import com.jimuqu.claw.config.props.DingTalkProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkRobotSenderTest {
    @Test
    void buildsMarkdownPayloadFromContent() throws Exception {
        DingTalkRobotSender sender = new DingTalkRobotSender(null, new DingTalkProperties(), null);

        String payload = sender.markdownMessageParam("#### 杭州天气\n> 9度，西北风1级");
        JSONObject json = JSONObject.parseObject(payload);

        assertEquals("sampleMarkdown", sender.resolveMsgKey("#### 杭州天气"));
        assertEquals("杭州天气", json.getString("title"));
        assertTrue(json.getString("text").contains("9度"));
    }

    @Test
    void fallsBackToDefaultTitleWhenContentIsBlank() throws Exception {
        DingTalkRobotSender sender = new DingTalkRobotSender(null, new DingTalkProperties(), null);

        assertEquals("SolonClaw", sender.resolveMarkdownTitle("   "));
    }

    @Test
    void truncatesMarkdownPayloadWhenCharacterCountExceedsLimit() throws Exception {
        DingTalkRobotSender sender = new DingTalkRobotSender(null, new DingTalkProperties(), null);
        String content = repeat("a", 7000);

        String payload = sender.markdownMessageParam(content);
        JSONObject json = JSONObject.parseObject(payload);
        String text = json.getString("text");

        assertTrue(text.contains("已截断"));
        assertTrue(text.length() <= 5000);
    }

    @Test
    void truncatesMarkdownPayloadBySimpleLengthLimit() throws Exception {
        DingTalkRobotSender sender = new DingTalkRobotSender(null, new DingTalkProperties(), null);
        String content = repeat("中", 6500);

        String normalized = sender.normalizeMarkdownContent(content);

        assertTrue(normalized.contains("已截断"));
        assertTrue(normalized.length() <= 5000);
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}


