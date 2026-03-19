package com.jimuqu.claw.channel.feishu;

import com.alibaba.fastjson.JSONObject;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.ConversationType;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.channel.feishu.gateway.FeishuMessageGateway;
import com.jimuqu.claw.channel.feishu.sender.FeishuBotSender;
import com.jimuqu.claw.config.SolonClawProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeishuBotSenderTest {
    @Test
    void buildsMarkdownCardPayloadFromContent() {
        FeishuBotSender sender = new FeishuBotSender(new SolonClawProperties.Feishu(), new RecordingGateway());

        String payload = sender.cardMessageParam("#### 杭州天气\n> 9度，西北风1级");
        JSONObject root = JSONObject.parseObject(payload);

        assertEquals("2.0", root.getString("schema"));
        assertEquals("markdown", root.getJSONObject("body")
                .getJSONArray("elements")
                .getJSONObject(0)
                .getString("tag"));
        assertTrue(root.getJSONObject("body")
                .getJSONArray("elements")
                .getJSONObject(0)
                .getString("content")
                .contains("9度"));
    }

    @Test
    void patchesExistingProgressMessageForSameRun() {
        SolonClawProperties.Feishu properties = new SolonClawProperties.Feishu();
        properties.setStreamingReply(true);
        RecordingGateway gateway = new RecordingGateway();
        FeishuBotSender sender = new FeishuBotSender(properties, gateway);

        sender.send(outbound("run-1", "thinking-1", true));
        sender.send(outbound("run-1", "thinking-2", true));
        sender.send(outbound("run-1", "final-answer", false));

        assertEquals(1, gateway.createdMessageIds.size());
        assertEquals(2, gateway.patchedMessageIds.size());
        assertEquals("msg-1", gateway.patchedMessageIds.get(0));
        assertEquals("msg-1", gateway.patchedMessageIds.get(1));
        assertTrue(gateway.patchedContents.get(1).contains("final-answer"));
    }

    private OutboundEnvelope outbound(String runId, String content, boolean progress) {
        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setRunId(runId);
        outboundEnvelope.setContent(content);
        outboundEnvelope.setProgress(progress);
        outboundEnvelope.setReplyTarget(new ReplyTarget(ChannelType.FEISHU, ConversationType.GROUP, "oc_demo", "ou_demo"));
        return outboundEnvelope;
    }

    private static class RecordingGateway implements FeishuMessageGateway {
        private final List<String> createdMessageIds = new ArrayList<>();
        private final List<String> patchedMessageIds = new ArrayList<>();
        private final List<String> createdContents = new ArrayList<>();
        private final List<String> patchedContents = new ArrayList<>();

        @Override
        public String createCardMessage(String chatId, String cardContent) {
            String messageId = "msg-" + (createdMessageIds.size() + 1);
            createdMessageIds.add(messageId);
            createdContents.add(cardContent);
            return messageId;
        }

        @Override
        public void patchCardMessage(String messageId, String cardContent) {
            patchedMessageIds.add(messageId);
            patchedContents.add(cardContent);
        }
    }
}

