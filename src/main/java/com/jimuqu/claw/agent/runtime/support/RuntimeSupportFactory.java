package com.jimuqu.claw.agent.runtime.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.channel.ChannelRegistry;
import com.jimuqu.claw.agent.model.envelope.OutboundEnvelope;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.runtime.api.NotificationSupport;
import com.jimuqu.claw.agent.runtime.api.RunQuerySupport;
import com.jimuqu.claw.agent.store.RuntimeStoreService;

import java.util.List;

/**
 * 消除 AgentRuntimeService 和 SystemEventRunner 中重复的 Support 构建逻辑。
 */
public final class RuntimeSupportFactory {
    private RuntimeSupportFactory() {
    }

    /**
     * 构建基于会话的任务状态查询支持。
     *
     * @param store      运行时存储服务
     * @param sessionKey 会话键
     * @return 任务状态查询支持
     */
    public static RunQuerySupport buildRunQuerySupport(RuntimeStoreService store, String sessionKey) {
        return new RunQuerySupport() {
            @Override
            public List<AgentRun> listChildRuns(int limit) {
                return store.listChildRuns(sessionKey, limit);
            }

            @Override
            public AgentRun getRun(String runId) {
                AgentRun run = store.getRun(runId);
                if (run == null) {
                    return null;
                }
                if (StrUtil.equals(sessionKey, run.getParentSessionKey()) || StrUtil.equals(sessionKey, run.getSessionKey())) {
                    return run;
                }
                return null;
            }

            @Override
            public AgentRun getLatestChildRun() {
                return store.getLatestChildRun(sessionKey);
            }

            @Override
            public ParentRunChildrenSummary getChildSummary(String parentRunId, String batchKey) {
                String resolvedParentRunId = parentRunId;
                if (StrUtil.isBlank(resolvedParentRunId)) {
                    AgentRun latestParent = store.getLatestParentRunWithChildren(sessionKey);
                    resolvedParentRunId = latestParent == null ? null : latestParent.getRunId();
                }
                if (StrUtil.isBlank(resolvedParentRunId)) {
                    return null;
                }

                ParentRunChildrenSummary summary = store.summarizeChildRuns(
                        resolvedParentRunId,
                        StrUtil.blankToDefault(StrUtil.trim(batchKey), null)
                );
                return summary.getTotalChildren() == 0 ? null : summary;
            }
        };
    }

    /**
     * 构建基于会话的主动通知支持（从 store 动态查找 ReplyTarget）。
     *
     * @param store    运行时存储服务
     * @param registry 渠道注册表
     * @param sessionKey 会话键
     * @param runId    当前运行标识
     * @return 主动通知支持
     */
    public static NotificationSupport buildNotificationSupport(
            RuntimeStoreService store, ChannelRegistry registry, String sessionKey, String runId) {
        return (message, progress) -> {
            NotificationResult result = new NotificationResult();
            result.setSessionKey(sessionKey);

            if (StrUtil.isBlank(message)) {
                result.setDelivered(false);
                result.setMessage("message 不能为空");
                return result;
            }

            ReplyTarget replyTarget = store.getReplyTarget(sessionKey);
            if (replyTarget == null) {
                result.setDelivered(false);
                result.setMessage("当前会话没有可用的 ReplyTarget，无法主动通知");
                return result;
            }

            return doSendNotification(store, registry, runId, result, replyTarget, message, progress);
        };
    }

    /**
     * 构建基于指定 ReplyTarget 的主动通知支持。
     *
     * @param store       运行时存储服务
     * @param registry    渠道注册表
     * @param sessionKey  会话键
     * @param replyTarget 固定回复目标
     * @param runId       当前运行标识
     * @return 主动通知支持
     */
    public static NotificationSupport buildNotificationSupport(
            RuntimeStoreService store, ChannelRegistry registry, String sessionKey, ReplyTarget replyTarget, String runId) {
        return (message, progress) -> {
            NotificationResult result = new NotificationResult();
            result.setSessionKey(sessionKey);

            if (StrUtil.isBlank(message)) {
                result.setDelivered(false);
                result.setMessage("message 不能为空");
                return result;
            }
            if (replyTarget == null) {
                result.setDelivered(false);
                result.setMessage("当前系统事件没有可用的 ReplyTarget");
                return result;
            }

            return doSendNotification(store, registry, runId, result, replyTarget, message, progress);
        };
    }

    private static NotificationResult doSendNotification(
            RuntimeStoreService store, ChannelRegistry registry,
            String runId, NotificationResult result, ReplyTarget replyTarget,
            String message, boolean progress) {
        OutboundEnvelope outboundEnvelope = new OutboundEnvelope();
        outboundEnvelope.setRunId(runId);
        outboundEnvelope.setReplyTarget(replyTarget);
        outboundEnvelope.setContent(message);
        outboundEnvelope.setProgress(progress);
        DeliveryResult deliveryResult = registry.send(outboundEnvelope);
        RuntimeDeliveryHelper.recordDeliveryResult(store, runId, deliveryResult);
        RuntimeDeliveryHelper.applyDeliveryResult(result, deliveryResult);
        store.appendRunEvent(runId, progress ? "notify_progress" : "notify", message);

        result.setDelivered(deliveryResult.isDelivered());
        if (StrUtil.isBlank(result.getMessage())) {
            result.setMessage("sent to " + replyTarget.getChannelType() + ":" + replyTarget.getConversationId());
        }
        return result;
    }
}
