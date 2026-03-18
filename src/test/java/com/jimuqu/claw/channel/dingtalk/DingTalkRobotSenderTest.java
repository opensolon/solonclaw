package com.jimuqu.claw.channel.dingtalk;

import com.alibaba.fastjson.JSONObject;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkRobotSenderTest {
    @Test
    void buildsMarkdownPayloadFromContent() throws Exception {
        DingTalkRobotSender sender = new DingTalkRobotSender(null, new SolonClawProperties.DingTalk(), null);

        String payload = sender.markdownMessageParam("#### 杭州天气\n> 9度，西北风1级");
        JSONObject json = JSONObject.parseObject(payload);

        assertEquals("sampleMarkdown", sender.resolveMsgKey("#### 杭州天气"));
        assertEquals("杭州天气", json.getString("title"));
        assertTrue(json.getString("text").contains("9度"));
    }

    @Test
    void fallsBackToDefaultTitleWhenContentIsBlank() throws Exception {
        DingTalkRobotSender sender = new DingTalkRobotSender(null, new SolonClawProperties.DingTalk(), null);

        assertEquals("SolonClaw", sender.resolveMarkdownTitle("   "));
    }
}
