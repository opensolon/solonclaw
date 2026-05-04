package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.AgentRunContext;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.gateway.feedback.ConversationFeedbackSink;
import java.util.List;

/** 大模型调用网关接口。 */
public interface LlmGateway {
    /**
     * 发起一次聊天调用。
     *
     * @param session 当前会话
     * @param systemPrompt 系统提示词
     * @param userMessage 用户输入
     * @param toolObjects 当前可用工具
     * @return 模型调用结果
     */
    LlmResult chat(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects)
            throws Exception;

    /** 发起一次带中间态反馈的聊天调用。 */
    default LlmResult chat(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink)
            throws Exception {
        return chat(session, systemPrompt, userMessage, toolObjects);
    }

    /** 发起一次带中间态反馈与 dashboard 事件输出的聊天调用。 */
    default LlmResult chat(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink)
            throws Exception {
        return chat(session, systemPrompt, userMessage, toolObjects, feedbackSink);
    }

    /**
     * 恢复一次因人工审批而挂起的会话。
     *
     * @param session 当前会话
     * @param systemPrompt 系统提示词
     * @param toolObjects 当前可用工具
     * @return 模型调用结果
     */
    LlmResult resume(SessionRecord session, String systemPrompt, List<Object> toolObjects)
            throws Exception;

    /** 恢复一次因人工审批而挂起的会话，并附带中间态反馈。 */
    default LlmResult resume(
            SessionRecord session,
            String systemPrompt,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink)
            throws Exception {
        return resume(session, systemPrompt, toolObjects);
    }

    /** 恢复一次因人工审批而挂起的会话，并附带 dashboard 事件输出。 */
    default LlmResult resume(
            SessionRecord session,
            String systemPrompt,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink)
            throws Exception {
        return resume(session, systemPrompt, toolObjects, feedbackSink);
    }

    /** 执行一次已解析 provider 的 ReAct 调用，不在网关内做 fallback。 */
    default LlmResult executeOnce(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> toolObjects,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume,
            AppConfig.LlmConfig resolved,
            AgentRunContext runContext)
            throws Exception {
        if (resume) {
            return resume(session, systemPrompt, toolObjects, feedbackSink, eventSink);
        }
        return chat(session, systemPrompt, userMessage, toolObjects, feedbackSink, eventSink);
    }
}
