package com.jimuqu.solon.claw.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.AgentRunContext;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.DelegationResult;
import com.jimuqu.solon.claw.core.model.DelegationTask;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.DelegationService;
import com.jimuqu.solon.claw.storage.repository.SqlitePreferenceStore;
import com.jimuqu.solon.claw.support.ConversationOrchestratorHolder;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.constants.ToolNameConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.noear.snack4.ONode;

/** 默认子代理委托服务。 */
public class DefaultDelegationService implements DelegationService {
    /** 委托日志器。 */
    private static final Logger log = LoggerFactory.getLogger(DefaultDelegationService.class);

    /** 子代理固定禁用的工具。 */
    private static final List<String> BLOCKED_TOOLS =
            Arrays.asList(
                    ToolNameConstants.DELEGATE_TASK,
                    ToolNameConstants.MEMORY,
                    ToolNameConstants.SEND_MESSAGE,
                    ToolNameConstants.CRONJOB,
                    ToolNameConstants.EXECUTE_PYTHON,
                    ToolNameConstants.EXECUTE_JS);

    /** 当前系统已知工具清单。 */
    private static final List<String> ALL_TOOLS =
            Arrays.asList(
                    ToolNameConstants.FILE_READ,
                    ToolNameConstants.FILE_WRITE,
                    ToolNameConstants.FILE_LIST,
                    ToolNameConstants.FILE_DELETE,
                    ToolNameConstants.EXECUTE_SHELL,
                    ToolNameConstants.EXECUTE_PYTHON,
                    ToolNameConstants.EXECUTE_JS,
                    ToolNameConstants.GET_CURRENT_TIME,
                    ToolNameConstants.TODO,
                    ToolNameConstants.DELEGATE_TASK,
                    ToolNameConstants.MEMORY,
                    ToolNameConstants.SESSION_SEARCH,
                    ToolNameConstants.SKILLS_LIST,
                    ToolNameConstants.SKILL_VIEW,
                    ToolNameConstants.SKILL_MANAGE,
                    ToolNameConstants.SEND_MESSAGE,
                    ToolNameConstants.CRONJOB,
                    ToolNameConstants.CODESEARCH,
                    ToolNameConstants.WEBSEARCH,
                    ToolNameConstants.WEBFETCH);

    /** 对话编排器。 */
    private final ConversationOrchestratorHolder conversationHolder;

    /** 工具注册表。 */
    private final SqlitePreferenceStore preferenceStore;

    /** 会话仓储。 */
    private final SessionRepository sessionRepository;

    /** Agent run 轨迹仓储。 */
    private final AgentRunRepository agentRunRepository;

    private final AppConfig appConfig;

    private final AgentRunControlService agentRunControlService;

    private final ConcurrentMap<String, SubagentRunRecord> activeRegistry =
            new ConcurrentHashMap<String, SubagentRunRecord>();

    private final Semaphore concurrencyLimiter;

    /** Hermes 风格暂停新子代理 spawn。 */
    private volatile boolean spawnPaused;

    public DefaultDelegationService(
            ConversationOrchestratorHolder conversationHolder,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository) {
        this(conversationHolder, preferenceStore, sessionRepository, null, null, null);
    }

    public DefaultDelegationService(
            ConversationOrchestratorHolder conversationHolder,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository,
            AgentRunRepository agentRunRepository) {
        this(conversationHolder, preferenceStore, sessionRepository, agentRunRepository, null, null);
    }

    public DefaultDelegationService(
            ConversationOrchestratorHolder conversationHolder,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository,
            AgentRunRepository agentRunRepository,
            AppConfig appConfig) {
        this(
                conversationHolder,
                preferenceStore,
                sessionRepository,
                agentRunRepository,
                appConfig,
                null);
    }

    public DefaultDelegationService(
            ConversationOrchestratorHolder conversationHolder,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository,
            AgentRunRepository agentRunRepository,
            AppConfig appConfig,
            AgentRunControlService agentRunControlService) {
        this.conversationHolder = conversationHolder;
        this.preferenceStore = preferenceStore;
        this.sessionRepository = sessionRepository;
        this.agentRunRepository = agentRunRepository;
        this.appConfig = appConfig;
        this.agentRunControlService = agentRunControlService;
        int maxConcurrency =
                appConfig == null ? 3 : Math.max(1, appConfig.getTask().getSubagentMaxConcurrency());
        this.concurrencyLimiter = new Semaphore(maxConcurrency, true);
    }

    @Override
    public DelegationResult delegateSingle(String sourceKey, String prompt, String context)
            throws Exception {
        DelegationTask task = new DelegationTask();
        task.setName("delegate");
        task.setPrompt(prompt);
        task.setContext(context);
        return delegateSingle(sourceKey, task);
    }

    @Override
    public DelegationResult delegateSingle(String sourceKey, DelegationTask task) throws Exception {
        String prompt = task == null ? null : task.getPrompt();
        if (StrUtil.isBlank(prompt)) {
            return failureResult("delegate", "委托任务不能为空。");
        }
        if (spawnPaused) {
            return failureResult("delegate", "Subagent spawning is paused.");
        }
        AgentRunContext parentContext = AgentRunContext.current();
        int depth = resolveDepth(parentContext);
        int maxDepth = appConfig == null ? 1 : Math.max(1, appConfig.getTask().getSubagentMaxDepth());
        if (depth > maxDepth) {
            if (parentContext != null) {
                parentContext.event("subagent.rejected", "子 Agent depth 超限：" + depth + "/" + maxDepth);
            }
            return failureResult("delegate", "Subagent depth limit exceeded.");
        }
        if (!concurrencyLimiter.tryAcquire()) {
            if (parentContext != null) {
                parentContext.event("subagent.rejected", "子 Agent 并发数已达上限");
            }
            return failureResult("delegate", "Subagent concurrency limit exceeded.");
        }

        try {
            SessionRecord parentSession = sessionRepository.getBoundSession(sourceKey);
            String subagentId = "sa-" + IdSupport.newId();
            String childSourceKey = sourceKey + ":delegate:" + IdSupport.newId();
            cloneToolVisibility(sourceKey, childSourceKey);
            applyAllowedTools(childSourceKey, task == null ? null : task.getAllowedTools());
            applyBlockedTools(childSourceKey);
            prepareChildSession(childSourceKey, parentSession);

            if (conversationHolder.get() == null) {
                return failureResult("delegate", "Conversation orchestrator is not ready");
            }
            GatewayMessage message =
                    new GatewayMessage(PlatformType.MEMORY, "", "", decoratePrompt(task));
            message.setSourceKeyOverride(childSourceKey);
            SubagentRunRecord subagent =
                    startSubagent(subagentId, sourceKey, childSourceKey, task, parentContext, depth);
            if (isInterrupted(subagentId)) {
                finishInterrupted(subagent, "Subagent interrupted before start.");
                return failureResult(subagent.getName(), "Subagent interrupted before start.");
            }
            GatewayReply reply = conversationHolder.get().handleIncoming(message);
            finishSubagent(subagent, reply);

            DelegationResult result = new DelegationResult();
            result.setSubagentId(subagentId);
            result.setName(
                    StrUtil.blankToDefault(task == null ? null : task.getName(), "delegate"));
            result.setSessionId(reply == null ? null : reply.getSessionId());
            result.setSourceKey(childSourceKey);
            result.setContent(reply == null ? "" : reply.getContent());
            result.setError(reply != null && reply.isError());
            if (subagent.getChildRunId() != null) {
                result.setRunId(subagent.getChildRunId());
            }
            return result;
        } catch (Exception e) {
            log.warn("delegateSingle failed: sourceKey={}, prompt={}", sourceKey, prompt, e);
            return failureResult("delegate", e.getMessage());
        } finally {
            concurrencyLimiter.release();
        }
    }

    @Override
    public void setSpawnPaused(boolean paused) {
        this.spawnPaused = paused;
    }

    @Override
    public boolean isSpawnPaused() {
        return spawnPaused;
    }

    @Override
    public boolean interruptSubagent(String subagentId) {
        SubagentRunRecord record = activeRegistry.get(subagentId);
        if (record == null) {
            return false;
        }
        record.setInterruptRequested(true);
        record.setStatus("interrupting");
        record.setHeartbeatAt(System.currentTimeMillis());
        saveSubagent(record);
        if (agentRunControlService != null) {
            agentRunControlService.stop(record.getChildSourceKey());
        }
        return true;
    }

    @Override
    public List<Map<String, Object>> activeSubagents() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (SubagentRunRecord record : activeRegistry.values()) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("subagent_id", record.getSubagentId());
            map.put("parent_run_id", record.getParentRunId());
            map.put("child_run_id", record.getChildRunId());
            map.put("source_key", record.getChildSourceKey());
            map.put("status", record.getStatus());
            map.put("depth", record.getDepth());
            map.put("heartbeat_at", record.getHeartbeatAt());
            map.put("output_tail", record.getOutputTailJson());
            list.add(map);
        }
        return list;
    }

    @Override
    public List<DelegationResult> delegateBatch(final String sourceKey, List<DelegationTask> tasks)
            throws Exception {
        List<DelegationResult> results = new ArrayList<DelegationResult>();
        if (tasks == null || tasks.isEmpty()) {
            return results;
        }

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        Math.min(
                                appConfig == null
                                        ? 3
                                        : Math.max(1, appConfig.getTask().getSubagentMaxConcurrency()),
                                tasks.size()));
        try {
            List<Future<DelegationResult>> futures = new ArrayList<Future<DelegationResult>>();
            for (final DelegationTask task : tasks) {
                futures.add(
                        executorService.submit(
                                new Callable<DelegationResult>() {
                                    @Override
                                    public DelegationResult call() throws Exception {
                                        DelegationResult result = delegateSingle(sourceKey, task);
                                        result.setName(
                                                StrUtil.blankToDefault(task.getName(), "delegate"));
                                        return result;
                                    }
                                }));
            }

            for (Future<DelegationResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    log.warn("delegateBatch child failed: sourceKey={}", sourceKey, e);
                    results.add(failureResult("delegate", e.getMessage()));
                }
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    /** 复制父来源键的工具可见性。 */
    private void cloneToolVisibility(String parentSourceKey, String childSourceKey)
            throws Exception {
        for (String toolName : ALL_TOOLS) {
            boolean enabled = preferenceStore.isToolEnabled(parentSourceKey, toolName);
            preferenceStore.setToolEnabled(childSourceKey, toolName, enabled);
        }
    }

    /** 对子会话应用固定黑名单。 */
    private void applyBlockedTools(String childSourceKey) throws Exception {
        for (String blockedTool : BLOCKED_TOOLS) {
            preferenceStore.setToolEnabled(childSourceKey, blockedTool, false);
        }
    }

    private void applyAllowedTools(String childSourceKey, List<String> allowedTools)
            throws Exception {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return;
        }
        for (String toolName : ALL_TOOLS) {
            preferenceStore.setToolEnabled(childSourceKey, toolName, false);
        }
        for (String toolName : allowedTools) {
            if (toolName != null && ALL_TOOLS.contains(toolName.trim())) {
                preferenceStore.setToolEnabled(childSourceKey, toolName.trim(), true);
            }
        }
    }

    /** 预先创建子会话并写入父会话关系。 */
    private void prepareChildSession(String childSourceKey, SessionRecord parentSession)
            throws Exception {
        SessionRecord existing = sessionRepository.getBoundSession(childSourceKey);
        if (existing != null) {
            return;
        }

        SessionRecord childSession = sessionRepository.bindNewSession(childSourceKey);
        if (parentSession != null) {
            childSession.setParentSessionId(parentSession.getSessionId());
        }
        sessionRepository.save(childSession);
    }

    /** 拼接委托上下文。 */
    private String decoratePrompt(DelegationTask task) {
        String prompt = task == null ? "" : task.getPrompt();
        String context = task == null ? "" : task.getContext();
        StringBuilder buffer = new StringBuilder();
        buffer.append("任务目标:\n").append(prompt);
        if (StrUtil.isBlank(context)) {
            context = "";
        }
        if (StrUtil.isNotBlank(context)) {
            buffer.append("\n\n补充上下文:\n").append(context);
        }
        if (task != null && StrUtil.isNotBlank(task.getExpectedOutput())) {
            buffer.append("\n\n期望输出:\n").append(task.getExpectedOutput());
        }
        if (task != null && StrUtil.isNotBlank(task.getWriteScope())) {
            buffer.append("\n\n写入范围:\n").append(task.getWriteScope());
        }
        return buffer.toString();
    }

    /** 构造失败结果，避免单个子任务异常打断整个批次。 */
    private DelegationResult failureResult(String name, String message) {
        DelegationResult result = new DelegationResult();
        result.setName(StrUtil.blankToDefault(name, "delegate"));
        result.setError(true);
        result.setContent(StrUtil.blankToDefault(message, "delegation failed"));
        return result;
    }

    private SubagentRunRecord startSubagent(
            String subagentId,
            String parentSourceKey,
            String childSourceKey,
            DelegationTask task,
            AgentRunContext parentContext,
            int depth) {
        SubagentRunRecord record = new SubagentRunRecord();
        long now = System.currentTimeMillis();
        record.setSubagentId(subagentId);
        record.setParentRunId(parentContext == null ? null : parentContext.getRunId());
        record.setParentSourceKey(parentSourceKey);
        record.setChildSourceKey(childSourceKey);
        record.setName(StrUtil.blankToDefault(task == null ? null : task.getName(), "delegate"));
        record.setGoalPreview(
                com.jimuqu.solon.claw.core.model.AgentRunContext.safe(
                        task == null ? null : task.getPrompt(), 1000));
        record.setStatus("running");
        record.setActive(true);
        record.setDepth(depth);
        record.setStartedAt(now);
        record.setHeartbeatAt(now);
        saveSubagent(record);
        activeRegistry.put(subagentId, record);
        if (parentContext != null) {
            parentContext.event("subagent.spawned", "子 Agent 已启动：" + record.getName());
            incrementSubtaskCount(parentContext.getRunId());
        }
        return record;
    }

    private void finishSubagent(SubagentRunRecord record, GatewayReply reply) {
        if (record == null) {
            return;
        }
        record.setStatus(reply != null && reply.isError() ? "failed" : "success");
        record.setSessionId(reply == null ? null : reply.getSessionId());
        record.setChildRunId(resolveLatestRunId(record.getChildSourceKey(), record.getSessionId()));
        record.setError(reply != null && reply.isError() ? reply.getContent() : null);
        record.setOutputTailJson(buildTailJson(reply == null ? "" : reply.getContent()));
        record.setActive(false);
        record.setFinishedAt(System.currentTimeMillis());
        record.setHeartbeatAt(record.getFinishedAt());
        saveSubagent(record);
        activeRegistry.remove(record.getSubagentId());
    }

    private void finishInterrupted(SubagentRunRecord record, String message) {
        if (record == null) {
            return;
        }
        record.setStatus("interrupted");
        record.setError(message);
        record.setActive(false);
        record.setInterruptRequested(true);
        record.setFinishedAt(System.currentTimeMillis());
        record.setHeartbeatAt(record.getFinishedAt());
        saveSubagent(record);
        activeRegistry.remove(record.getSubagentId());
    }

    private boolean isInterrupted(String subagentId) {
        SubagentRunRecord record = activeRegistry.get(subagentId);
        return record != null && record.isInterruptRequested();
    }

    private int resolveDepth(AgentRunContext parentContext) {
        if (parentContext == null || StrUtil.isBlank(parentContext.getRunId())) {
            return 1;
        }
        try {
            AgentRunRecord parent = agentRunRepository == null ? null : agentRunRepository.findRun(parentContext.getRunId());
            if (parent != null && "subagent".equals(parent.getRunKind())) {
                return 2;
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private void incrementSubtaskCount(String parentRunId) {
        if (agentRunRepository == null || StrUtil.isBlank(parentRunId)) {
            return;
        }
        try {
            AgentRunRecord parent = agentRunRepository.findRun(parentRunId);
            if (parent != null) {
                parent.setSubtaskCount(parent.getSubtaskCount() + 1);
                parent.setLastActivityAt(System.currentTimeMillis());
                agentRunRepository.saveRun(parent);
            }
        } catch (Exception ignored) {
        }
    }

    private String resolveLatestRunId(String childSourceKey, String sessionId) {
        if (agentRunRepository == null || StrUtil.isBlank(childSourceKey)) {
            return null;
        }
        try {
            List<AgentRunRecord> runs = agentRunRepository.listActiveBySource(childSourceKey, 1);
            if (!runs.isEmpty()) {
                return runs.get(0).getRunId();
            }
            if (StrUtil.isNotBlank(sessionId)) {
                List<AgentRunRecord> bySession = agentRunRepository.listBySession(sessionId, 1);
                if (!bySession.isEmpty()) {
                    return bySession.get(0).getRunId();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void saveSubagent(SubagentRunRecord record) {
        if (agentRunRepository == null) {
            return;
        }
        try {
            agentRunRepository.saveSubagentRun(record);
        } catch (Exception ignored) {
        }
    }

    private String buildTailJson(String content) {
        ONode array = new ONode().asArray();
        ONode item = new ONode().asObject();
        item.set(
                "preview",
                com.jimuqu.solon.claw.core.model.AgentRunContext.safe(content, 1000));
        item.set("is_error", false);
        array.add(item);
        return array.toJson();
    }
}
