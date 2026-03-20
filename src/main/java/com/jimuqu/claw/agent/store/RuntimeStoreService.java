package com.jimuqu.claw.agent.store;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.jimuqu.claw.agent.model.run.AgentRun;
import com.jimuqu.claw.agent.model.event.ChildRunCompletedData;
import com.jimuqu.claw.agent.model.event.ChildRunSpawnedData;
import com.jimuqu.claw.agent.model.enums.ChannelType;
import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import com.jimuqu.claw.agent.model.event.ConversationEvent;
import com.jimuqu.claw.agent.model.envelope.InboundEnvelope;
import com.jimuqu.claw.agent.model.route.LatestReplyRoute;
import com.jimuqu.claw.agent.model.route.ReplyTarget;
import com.jimuqu.claw.agent.model.event.RunEvent;
import com.jimuqu.claw.agent.model.enums.RunStatus;
import com.jimuqu.claw.agent.runtime.support.ParentRunChildrenSummary;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 负责以文件形式持久化运行任务、会话事件、去重标记和路由信息。
 */
public class RuntimeStoreService {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(RuntimeStoreService.class);
    /** 运行时根目录。 */
    private final File runtimeDir;
    /** 运行任务目录。 */
    private final File runsDir;
    /** 会话事件目录。 */
    private final File conversationsDir;
    /** 去重标记目录。 */
    private final File dedupDir;
    /** 元数据目录。 */
    private final File metaDir;
    /** 媒体目录。 */
    private final File mediaDir;
    /** 文件路径锁表。 */
    private final Map<String, ReentrantLock> pathLocks = new ConcurrentHashMap<>();

    /**
     * 创建运行时存储服务。
     *
     * @param runtimeDir 运行时根目录
     */
    public RuntimeStoreService(File runtimeDir) {
        FileUtil.mkdir(runtimeDir);
        this.runtimeDir = runtimeDir;
        this.runsDir = FileUtil.mkdir(new File(runtimeDir, "runs"));
        this.conversationsDir = FileUtil.mkdir(new File(runtimeDir, "conversations"));
        this.dedupDir = FileUtil.mkdir(new File(runtimeDir, "dedup"));
        this.metaDir = FileUtil.mkdir(new File(runtimeDir, "meta"));
        this.mediaDir = FileUtil.mkdir(new File(runtimeDir, "media"));
        log.info("Runtime store initialized at {}", runtimeDir.getAbsolutePath());
        markIncompleteRunsAborted();
    }

    /**
     * 生成一个新的运行任务标识。
     *
     * @return 运行任务标识
     */
    public String newRunId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 为入站消息注册去重标记。
     *
     * @param channelType 渠道类型
     * @param messageId 消息标识
     * @return 若首次出现则返回 true
     */
    public boolean registerInbound(ChannelType channelType, String messageId) {
        if (StrUtil.isBlank(messageId)) {
            return true;
        }

        String safeName = DigestUtil.sha1Hex(channelType.name() + ":" + messageId);
        File marker = new File(dedupDir, safeName + ".flag");
        ReentrantLock lock = lock(marker);
        lock.lock();
        try {
            if (marker.exists()) {
                return false;
            }

            FileUtil.writeUtf8String(String.valueOf(System.currentTimeMillis()), marker);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 追加一条用户入站事件。
     *
     * @param inboundEnvelope 入站消息
     * @return 新事件版本号
     */
    public long appendInboundConversationEvent(InboundEnvelope inboundEnvelope) {
        ConversationEvent event = new ConversationEvent();
        event.setSessionKey(inboundEnvelope.getSessionKey());
        event.setEventType(resolveInboundEventType(inboundEnvelope));
        event.setSourceKind(inboundEnvelope.getSourceKind());
        event.setSourceMessageId(inboundEnvelope.getMessageId());
        event.setSourceUserVersion(resolveInboundSourceUserVersion(inboundEnvelope));
        event.setRole(resolveInboundEventRole(inboundEnvelope));
        event.setContent(inboundEnvelope.getContent());
        event.setCreatedAt(inboundEnvelope.getReceivedAt());
        return appendConversationEvent(inboundEnvelope.getSessionKey(), event);
    }

    /**
     * 追加一条助手回复事件。
     *
     * @param sessionKey 会话键
     * @param runId 运行任务标识
     * @param sourceMessageId 来源消息标识
     * @param sourceUserVersion 来源用户消息版本
     * @param content 回复内容
     * @return 新事件版本号
     */
    public long appendAssistantConversationEvent(
            String sessionKey,
            String runId,
            String sourceMessageId,
            long sourceUserVersion,
            RuntimeSourceKind sourceKind,
            String content
    ) {
        ConversationEvent event = new ConversationEvent();
        event.setSessionKey(sessionKey);
        event.setEventType("assistant_reply");
        event.setRunId(runId);
        event.setSourceKind(sourceKind);
        event.setSourceMessageId(sourceMessageId);
        event.setSourceUserVersion(sourceUserVersion);
        event.setRole("assistant");
        event.setContent(content);
        event.setCreatedAt(System.currentTimeMillis());
        return appendConversationEvent(sessionKey, event);
    }

    /**
     * 追加一条系统事件。
     *
     * @param sessionKey 会话键
     * @param runId 运行任务标识
     * @param content 事件内容
     * @return 新事件版本号
     */
    public long appendSystemConversationEvent(String sessionKey, String runId, String content) {
        ConversationEvent event = new ConversationEvent();
        event.setSessionKey(sessionKey);
        event.setEventType("system_event");
        event.setRunId(runId);
        event.setSourceKind(RuntimeSourceKind.CHILD_CONTINUATION);
        event.setRole("system");
        event.setContent(content);
        event.setCreatedAt(System.currentTimeMillis());
        return appendConversationEvent(sessionKey, event);
    }

    /**
     * 追加一条结构化的子任务创建事件。
     *
     * @param sessionKey 父会话键
     * @param parentRunId 父运行标识
     * @param sourceUserVersion 所属父输入版本
     * @param childRun 子运行
     * @return 新事件版本号
     */
    public long appendChildRunSpawnedEvent(String sessionKey, String parentRunId, long sourceUserVersion, AgentRun childRun) {
        ChildRunSpawnedData data = new ChildRunSpawnedData();
        data.setParentRunId(parentRunId);
        data.setChildRunId(childRun.getRunId());
        data.setChildSessionKey(childRun.getSessionKey());
        data.setTaskDescription(childRun.getTaskDescription());
        data.setBatchKey(childRun.getBatchKey());

        ConversationEvent event = new ConversationEvent();
        event.setSessionKey(sessionKey);
        event.setEventType("child_run_spawned");
        event.setRunId(parentRunId);
        event.setSourceKind(RuntimeSourceKind.CHILD_CONTINUATION);
        event.setSourceUserVersion(sourceUserVersion);
        event.setRole("system");
        event.setContent("子任务已创建");
        event.setEventDataJson(JSONUtil.toJsonStr(data));
        event.setCreatedAt(System.currentTimeMillis());
        return appendConversationEvent(sessionKey, event);
    }

    /**
     * 追加一条结构化的子任务完成事件。
     *
     * @param sessionKey 父会话键
     * @param parentRunId 父运行标识
     * @param sourceUserVersion 所属父输入版本
     * @param childRun 子运行
     * @return 新事件版本号
     */
    public long appendChildRunCompletedEvent(String sessionKey, String parentRunId, long sourceUserVersion, AgentRun childRun) {
        ChildRunCompletedData data = new ChildRunCompletedData();
        data.setParentRunId(parentRunId);
        data.setChildRunId(childRun.getRunId());
        data.setChildSessionKey(childRun.getSessionKey());
        data.setStatus(childRun.getStatus() == null ? null : childRun.getStatus().name());
        data.setTaskDescription(childRun.getTaskDescription());
        data.setBatchKey(childRun.getBatchKey());
        data.setResult(childRun.getFinalResponse());
        data.setErrorMessage(childRun.getErrorMessage());

        ConversationEvent event = new ConversationEvent();
        event.setSessionKey(sessionKey);
        event.setEventType("child_run_completed");
        event.setRunId(parentRunId);
        event.setSourceKind(RuntimeSourceKind.CHILD_CONTINUATION);
        event.setSourceUserVersion(sourceUserVersion);
        event.setRole("system");
        event.setContent("子任务已完成");
        event.setEventDataJson(JSONUtil.toJsonStr(data));
        event.setCreatedAt(System.currentTimeMillis());
        return appendConversationEvent(sessionKey, event);
    }

    /**
     * 在会话事件文件中追加一条事件。
     *
     * @param sessionKey 会话键
     * @param event 会话事件
     * @return 新事件版本号
     */
    private long appendConversationEvent(String sessionKey, ConversationEvent event) {
        File eventsFile = conversationEventsFile(sessionKey);
        ReentrantLock lock = lock(eventsFile);
        lock.lock();
        try {
            long nextVersion = countLines(eventsFile) + 1L;
            event.setVersion(nextVersion);
            FileUtil.appendUtf8String(JSONUtil.toJsonStr(event) + System.lineSeparator(), eventsFile);
            updateConversationMeta(sessionKey, nextVersion, event.getCreatedAt(), event.getRunId());
            return nextVersion;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取某个版本之前的会话历史并重建成聊天消息列表。
     *
     * @param sessionKey 会话键
     * @param beforeUserVersion 截止用户消息版本
     * @return 聊天历史
     */
    public List<ChatMessage> loadConversationHistoryBefore(String sessionKey, long beforeUserVersion) {
        List<ConversationEvent> allEvents = readConversationEvents(sessionKey);
        List<ConversationEvent> userEvents = new ArrayList<>();
        Map<Long, List<ConversationEvent>> anchoredEventsBySource = new LinkedHashMap<>();
        List<ConversationEvent> unanchoredEvents = new ArrayList<>();

        for (ConversationEvent event : allEvents) {
            if ("user_message".equals(event.getEventType()) && event.getVersion() < beforeUserVersion) {
                userEvents.add(event);
            }
            if ("assistant_reply".equals(event.getEventType()) && event.getSourceUserVersion() < beforeUserVersion) {
                if (event.getSourceUserVersion() > 0) {
                    anchoredEventsBySource.computeIfAbsent(event.getSourceUserVersion(), key -> new ArrayList<>()).add(event);
                } else {
                    unanchoredEvents.add(event);
                }
            }
            if (event.getVersion() < beforeUserVersion && isRenderableSystemEvent(event)) {
                long anchorVersion = event.getSourceUserVersion();
                if (anchorVersion > 0) {
                    anchoredEventsBySource.computeIfAbsent(anchorVersion, key -> new ArrayList<>()).add(event);
                } else {
                    unanchoredEvents.add(event);
                }
            }
        }

        userEvents.sort(Comparator.comparingLong(ConversationEvent::getVersion));
        List<ChatMessage> history = new ArrayList<>();
        for (ConversationEvent userEvent : userEvents) {
            history.add(ChatMessage.ofUser(userEvent.getContent()));
            List<ConversationEvent> anchoredEvents = anchoredEventsBySource.get(userEvent.getVersion());
            if (anchoredEvents != null) {
                anchoredEvents.sort(Comparator.comparingLong(ConversationEvent::getVersion));
                for (ConversationEvent anchoredEvent : anchoredEvents) {
                    history.add(toHistoryMessage(anchoredEvent));
                }
            }
        }

        unanchoredEvents.sort(Comparator.comparingLong(ConversationEvent::getVersion));
        for (ConversationEvent event : unanchoredEvents) {
            history.add(toHistoryMessage(event));
        }

        return history;
    }

    /**
     * 返回某个会话当前最新事件版本号。
     *
     * @param sessionKey 会话键
     * @return 最新事件版本号；若会话为空则返回 0
     */
    public long getLatestConversationVersion(String sessionKey) {
        File file = conversationEventsFile(sessionKey);
        if (!file.exists()) {
            return 0L;
        }
        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            return countLines(file);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 保存运行任务详情。
     *
     * @param agentRun 运行任务
     */
    public void saveRun(AgentRun agentRun) {
        File runFile = runFile(agentRun.getRunId());
        ReentrantLock lock = lock(runFile);
        lock.lock();
        try {
            FileUtil.writeUtf8String(JSONUtil.toJsonStr(agentRun), runFile);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取运行任务详情。
     *
     * @param runId 运行任务标识
     * @return 运行任务；若不存在则返回 null
     */
    public AgentRun getRun(String runId) {
        File runFile = runFile(runId);
        if (!runFile.exists()) {
            return null;
        }
        ReentrantLock lock = lock(runFile);
        lock.lock();
        try {
            if (!runFile.exists()) {
                return null;
            }
            return JSONUtil.toBean(FileUtil.readUtf8String(runFile), AgentRun.class);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 追加一条运行事件。
     *
     * @param runId 运行任务标识
     * @param eventType 事件类型
     * @param message 事件消息
     * @return 运行事件
     */
    public RunEvent appendRunEvent(String runId, String eventType, String message) {
        File file = runEventsFile(runId);
        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            RunEvent runEvent = new RunEvent();
            runEvent.setRunId(runId);
            AgentRun agentRun = getRun(runId);
            if (agentRun != null) {
                runEvent.setSourceKind(agentRun.getSourceKind());
            }
            runEvent.setEventType(eventType);
            runEvent.setMessage(message);
            runEvent.setCreatedAt(System.currentTimeMillis());
            runEvent.setSeq(countLines(file) + 1L);
            FileUtil.appendUtf8String(JSONUtil.toJsonStr(runEvent) + System.lineSeparator(), file);
            return runEvent;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取指定序号之后的运行事件。
     *
     * @param runId 运行任务标识
     * @param afterSeq 起始序号
     * @return 运行事件列表
     */
    public List<RunEvent> getRunEvents(String runId, long afterSeq) {
        File file = runEventsFile(runId);
        if (!file.exists()) {
            return Collections.emptyList();
        }

        List<RunEvent> events = new ArrayList<>();
        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            for (String line : FileUtil.readUtf8Lines(file)) {
                if (StrUtil.isBlank(line)) {
                    continue;
                }
                RunEvent event = JSONUtil.toBean(line, RunEvent.class);
                if (event.getSeq() > afterSeq) {
                    events.add(event);
                }
            }
        } finally {
            lock.unlock();
        }
        return events;
    }

    /**
     * 判断指定运行是否已写入某种事件类型。
     *
     * @param runId 运行任务标识
     * @param eventType 事件类型
     * @return 若存在则返回 true
     */
    public boolean hasRunEventType(String runId, String eventType) {
        File file = runEventsFile(runId);
        if (!file.exists()) {
            return false;
        }

        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            for (String line : FileUtil.readUtf8Lines(file)) {
                if (StrUtil.isBlank(line)) {
                    continue;
                }
                RunEvent event = JSONUtil.toBean(line, RunEvent.class);
                if (StrUtil.equals(eventType, event.getEventType())) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询某个父会话最近的子任务列表。
     *
     * @param parentSessionKey 父会话键
     * @param limit 最大返回条数
     * @return 子任务列表
     */
    public List<AgentRun> listChildRuns(String parentSessionKey, int limit) {
        int resolvedLimit = Math.max(1, limit);
        List<AgentRun> runs = new ArrayList<>();
        List<File> files = FileUtil.loopFiles(runsDir, pathname -> pathname.isFile() && pathname.getName().endsWith(".json"));
        for (File file : files) {
            try {
                AgentRun run = JSONUtil.toBean(FileUtil.readUtf8String(file), AgentRun.class);
                if (run != null && StrUtil.equals(parentSessionKey, run.getParentSessionKey())) {
                    runs.add(run);
                }
            } catch (Exception ignored) {
                // 子任务与聚合查询并发发生时，允许跳过临时不可读文件。
            }
        }
        runs.sort(Comparator.comparingLong(AgentRun::getCreatedAt).reversed());
        return runs.size() > resolvedLimit ? new ArrayList<>(runs.subList(0, resolvedLimit)) : runs;
    }

    /**
     * 查询某个父会话最近一次子任务。
     *
     * @param parentSessionKey 父会话键
     * @return 最近一次子任务；不存在则返回 null
     */
    public AgentRun getLatestChildRun(String parentSessionKey) {
        List<AgentRun> runs = listChildRuns(parentSessionKey, 1);
        return runs.isEmpty() ? null : runs.get(0);
    }

    /**
     * 查询某个父运行下的所有子任务。
     *
     * @param parentRunId 父运行标识
     * @return 子任务列表
     */
    public List<AgentRun> listChildRunsByParentRun(String parentRunId) {
        return listChildRunsByParentRun(parentRunId, null);
    }

    /**
     * 查询某个父运行下的所有子任务。
     *
     * @param parentRunId 父运行标识
     * @param batchKey 批次键；为空时返回全部
     * @return 子任务列表
     */
    public List<AgentRun> listChildRunsByParentRun(String parentRunId, String batchKey) {
        List<AgentRun> runs = new ArrayList<>();
        if (StrUtil.isBlank(parentRunId)) {
            return runs;
        }

        List<File> files = FileUtil.loopFiles(runsDir, pathname -> pathname.isFile() && pathname.getName().endsWith(".json"));
        for (File file : files) {
            try {
                AgentRun run = JSONUtil.toBean(FileUtil.readUtf8String(file), AgentRun.class);
                if (run != null
                        && StrUtil.equals(parentRunId, run.getParentRunId())
                        && (StrUtil.isBlank(batchKey) || StrUtil.equals(batchKey, run.getBatchKey()))) {
                    runs.add(run);
                }
            } catch (Exception ignored) {
                // 允许跳过并发写入期间的临时不可读文件。
            }
        }
        runs.sort(Comparator.comparingLong(AgentRun::getCreatedAt));
        return runs;
    }

    /**
     * 返回某个会话最近一个派生过子任务的父运行。
     *
     * @param sessionKey 会话键
     * @return 父运行；不存在则返回 null
     */
    public AgentRun getLatestParentRunWithChildren(String sessionKey) {
        List<File> files = FileUtil.loopFiles(runsDir, pathname -> pathname.isFile() && pathname.getName().endsWith(".json"));
        AgentRun latest = null;
        for (File file : files) {
            try {
                AgentRun run = JSONUtil.toBean(FileUtil.readUtf8String(file), AgentRun.class);
                if (run == null || !StrUtil.equals(sessionKey, run.getSessionKey())) {
                    continue;
                }
                if (listChildRunsByParentRun(run.getRunId()).isEmpty()) {
                    continue;
                }
                if (latest == null || run.getCreatedAt() > latest.getCreatedAt()) {
                    latest = run;
                }
            } catch (Exception ignored) {
                // 允许跳过并发写入期间的临时不可读文件。
            }
        }
        return latest;
    }

    /**
     * 聚合某个父运行下的子任务状态。
     *
     * @param parentRunId 父运行标识
     * @return 聚合结果
     */
    public ParentRunChildrenSummary summarizeChildRuns(String parentRunId) {
        return summarizeChildRuns(parentRunId, null);
    }

    /**
     * 聚合某个父运行下的子任务状态。
     *
     * @param parentRunId 父运行标识
     * @param batchKey 批次键；为空时聚合全部子任务
     * @return 聚合结果
     */
    public ParentRunChildrenSummary summarizeChildRuns(String parentRunId, String batchKey) {
        List<AgentRun> children = listChildRunsByParentRun(parentRunId, batchKey);
        ParentRunChildrenSummary summary = new ParentRunChildrenSummary();
        summary.setParentRunId(parentRunId);
        summary.setBatchKey(batchKey);
        summary.setChildren(children);
        summary.setTotalChildren(children.size());

        int succeeded = 0;
        int failed = 0;
        int pending = 0;
        for (AgentRun child : children) {
            if (child.getStatus() == RunStatus.SUCCEEDED) {
                succeeded++;
            } else if (child.getStatus() == RunStatus.FAILED
                    || child.getStatus() == RunStatus.CANCELLED
                    || child.getStatus() == RunStatus.ABORTED) {
                failed++;
            } else {
                pending++;
            }
        }

        summary.setSucceededChildren(succeeded);
        summary.setFailedChildren(failed);
        summary.setPendingChildren(pending);
        summary.setAllCompleted(!children.isEmpty() && pending == 0);
        return summary;
    }

    /**
     * 读取最近一次外部可回复路由。
     *
     * @return 最近一次外部路由
     */
    public LatestReplyRoute getLatestExternalRoute() {
        File file = latestReplyTargetFile();
        if (!file.exists()) {
            return null;
        }
        return JSONUtil.toBean(FileUtil.readUtf8String(file), LatestReplyRoute.class);
    }

    /**
     * 读取最近一次外部回复目标。
     *
     * @return 最近一次外部回复目标
     */
    public ReplyTarget getLatestExternalReplyTarget() {
        LatestReplyRoute route = getLatestExternalRoute();
        return route == null ? null : route.getReplyTarget();
    }

    /**
     * 读取某个会话最近一次回复目标。
     *
     * @param sessionKey 会话键
     * @return 最近回复目标；不存在则返回 null
     */
    public ReplyTarget getReplyTarget(String sessionKey) {
        if (StrUtil.isBlank(sessionKey)) {
            return null;
        }
        File file = sessionReplyTargetFile(sessionKey);
        if (!file.exists()) {
            return null;
        }
        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            if (!file.exists()) {
                return null;
            }
            LatestReplyRoute route = JSONUtil.toBean(FileUtil.readUtf8String(file), LatestReplyRoute.class);
            return route == null ? null : route.getReplyTarget();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回某个会话最近一次真实用户消息的版本号。
     *
     * @param sessionKey 会话键
     * @return 最新用户消息版本号；不存在则返回 0
     */
    public long getLatestUserConversationVersion(String sessionKey) {
        List<ConversationEvent> events = readConversationEvents(sessionKey);
        for (int i = events.size() - 1; i >= 0; i--) {
            ConversationEvent event = events.get(i);
            if ("user_message".equals(event.getEventType())) {
                return event.getVersion();
            }
        }
        return 0L;
    }

    /**
     * 记录最近一次外部回复目标。
     *
     * @param sessionKey 会话键
     * @param replyTarget 回复目标
     */
    public void rememberReplyTarget(String sessionKey, ReplyTarget replyTarget) {
        if (replyTarget == null) {
            return;
        }

        File sessionFile = sessionReplyTargetFile(sessionKey);
        ReentrantLock sessionLock = lock(sessionFile);
        sessionLock.lock();
        try {
            LatestReplyRoute route = new LatestReplyRoute();
            route.setSessionKey(sessionKey);
            route.setReplyTarget(replyTarget);
            FileUtil.writeUtf8String(JSONUtil.toJsonStr(route), sessionFile);
        } finally {
            sessionLock.unlock();
        }

        File file = latestReplyTargetFile();
        ReentrantLock lock = lock(file);
        lock.lock();
        try {
            LatestReplyRoute route = new LatestReplyRoute();
            route.setSessionKey(sessionKey);
            route.setReplyTarget(replyTarget);
            FileUtil.writeUtf8String(JSONUtil.toJsonStr(route), file);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回运行时根目录。
     *
     * @return 运行时根目录
     */
    public File getRuntimeDir() {
        return runtimeDir;
    }

    /**
     * 按渠道返回媒体目录。
     *
     * @param channelType 渠道类型
     * @return 媒体目录
     */
    public File resolveMediaDir(ChannelType channelType) {
        return FileUtil.mkdir(new File(mediaDir, channelType.name().toLowerCase()));
    }

    /**
     * 读取某个会话的全部事件。
     *
     * @param sessionKey 会话键
     * @return 会话事件列表
     */
    public List<ConversationEvent> readConversationEvents(String sessionKey) {
        File file = conversationEventsFile(sessionKey);
        if (!file.exists()) {
            return Collections.emptyList();
        }

        List<ConversationEvent> events = new ArrayList<>();
        for (String line : FileUtil.readUtf8Lines(file)) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            events.add(JSONUtil.toBean(line, ConversationEvent.class));
        }
        return events;
    }

    /**
     * 更新会话元数据文件。
     *
     * @param sessionKey 会话键
     * @param latestVersion 最新版本号
     * @param lastUpdatedAt 最后更新时间
     * @param lastRunId 最后关联的运行任务标识
     */
    private void updateConversationMeta(String sessionKey, long latestVersion, long lastUpdatedAt, String lastRunId) {
        File file = conversationMetaFile(sessionKey);
        Map<String, Object> meta = new HashMap<>();
        meta.put("sessionKey", sessionKey);
        meta.put("latestVersion", latestVersion);
        meta.put("lastUpdatedAt", lastUpdatedAt);
        meta.put("lastRunId", lastRunId);
        FileUtil.writeUtf8String(JSONUtil.toJsonStr(meta), file);
    }

    /**
     * 统计文件中的行数。
     *
     * @param file 目标文件
     * @return 行数
     */
    private long countLines(File file) {
        if (!file.exists()) {
            return 0L;
        }
        return FileUtil.readUtf8Lines(file).size();
    }

    /**
     * 在应用启动时将未完成任务标记为已中止。
     */
    private void markIncompleteRunsAborted() {
        List<File> runFiles = FileUtil.loopFiles(runsDir, file -> file.isFile() && file.getName().endsWith(".json"));
        for (File runFile : runFiles) {
            AgentRun run = JSONUtil.toBean(FileUtil.readUtf8String(runFile), AgentRun.class);
            if (run.getStatus() == RunStatus.QUEUED || run.getStatus() == RunStatus.RUNNING) {
                run.setStatus(RunStatus.ABORTED);
                run.setFinishedAt(System.currentTimeMillis());
                run.setErrorMessage("Application restarted before the run finished.");
                saveRun(run);
                appendRunEvent(run.getRunId(), "status", "aborted");
            }
        }
    }

    /**
     * 返回运行任务详情文件。
     *
     * @param runId 运行任务标识
     * @return 运行任务文件
     */
    private File runFile(String runId) {
        return new File(runsDir, runId + ".json");
    }

    /**
     * 返回运行事件文件。
     *
     * @param runId 运行任务标识
     * @return 运行事件文件
     */
    private File runEventsFile(String runId) {
        return new File(runsDir, runId + ".events.jsonl");
    }

    /**
     * 返回会话事件文件。
     *
     * @param sessionKey 会话键
     * @return 会话事件文件
     */
    private File conversationEventsFile(String sessionKey) {
        File dir = FileUtil.mkdir(new File(conversationsDir, safeSessionKey(sessionKey)));
        return new File(dir, "events.jsonl");
    }

    /**
     * 返回会话元数据文件。
     *
     * @param sessionKey 会话键
     * @return 会话元数据文件
     */
    private File conversationMetaFile(String sessionKey) {
        File dir = FileUtil.mkdir(new File(conversationsDir, safeSessionKey(sessionKey)));
        return new File(dir, "meta.json");
    }

    /**
     * 返回最近回复目标文件。
     *
     * @return 最近回复目标文件
     */
    private File latestReplyTargetFile() {
        return new File(metaDir, "latest-reply-target.json");
    }

    /**
     * 返回某个会话的最近回复目标文件。
     *
     * @param sessionKey 会话键
     * @return 回复目标文件
     */
    private File sessionReplyTargetFile(String sessionKey) {
        File dir = FileUtil.mkdir(new File(metaDir, "reply-targets"));
        return new File(dir, safeSessionKey(sessionKey) + ".json");
    }

    private boolean isRenderableSystemEvent(ConversationEvent event) {
        return "system_event".equals(event.getEventType())
                || "child_run_spawned".equals(event.getEventType())
                || "child_run_completed".equals(event.getEventType());
    }

    /**
     * 将会话事件转换为历史消息。
     *
     * @param event 会话事件
     * @return 历史消息
     */
    private ChatMessage toHistoryMessage(ConversationEvent event) {
        if ("assistant_reply".equals(event.getEventType())) {
            return ChatMessage.ofAssistant(event.getContent());
        }
        return ChatMessage.ofSystem(renderConversationEvent(event));
    }

    /**
     * 根据入站触发类型返回对应的会话事件类型。
     *
     * @param inboundEnvelope 入站消息
     * @return 会话事件类型
     */
    private String resolveInboundEventType(InboundEnvelope inboundEnvelope) {
        RuntimeSourceKind sourceKind = inboundEnvelope == null ? null : inboundEnvelope.getSourceKind();
        if (sourceKind == null || sourceKind == RuntimeSourceKind.USER_MESSAGE) {
            return "user_message";
        }
        return "system_event";
    }

    /**
     * 根据入站触发类型返回对应的会话事件角色。
     *
     * @param inboundEnvelope 入站消息
     * @return 会话事件角色
     */
    private String resolveInboundEventRole(InboundEnvelope inboundEnvelope) {
        RuntimeSourceKind sourceKind = inboundEnvelope == null ? null : inboundEnvelope.getSourceKind();
        if (sourceKind == null || sourceKind == RuntimeSourceKind.USER_MESSAGE) {
            return "user";
        }
        return "system";
    }

    /**
     * 解析入站事件要写入的历史锚点版本。
     *
     * @param inboundEnvelope 入站消息
     * @return 历史锚点版本
     */
    private long resolveInboundSourceUserVersion(InboundEnvelope inboundEnvelope) {
        RuntimeSourceKind sourceKind = inboundEnvelope == null ? null : inboundEnvelope.getSourceKind();
        if (sourceKind == null || sourceKind == RuntimeSourceKind.USER_MESSAGE) {
            return 0L;
        }
        return inboundEnvelope.getHistoryAnchorVersion();
    }

    private String renderConversationEvent(ConversationEvent event) {
        if ("child_run_spawned".equals(event.getEventType())) {
            ChildRunSpawnedData data = JSONUtil.toBean(event.getEventDataJson(), ChildRunSpawnedData.class);
            return "[系统事件] 已创建子任务\n"
                    + "parentRunId=" + StrUtil.blankToDefault(data.getParentRunId(), "(未知)") + "\n"
                    + "childRunId=" + StrUtil.blankToDefault(data.getChildRunId(), "(未知)") + "\n"
                    + "childSessionKey=" + StrUtil.blankToDefault(data.getChildSessionKey(), "(未知)") + "\n"
                    + (StrUtil.isBlank(data.getBatchKey()) ? "" : "batchKey=" + data.getBatchKey() + "\n")
                    + "task=" + StrUtil.blankToDefault(data.getTaskDescription(), "(未记录任务描述)");
        }
        if ("child_run_completed".equals(event.getEventType())) {
            ChildRunCompletedData data = JSONUtil.toBean(event.getEventDataJson(), ChildRunCompletedData.class);
            StringBuilder builder = new StringBuilder("[系统事件] 子任务已完成\n");
            builder.append("parentRunId=").append(StrUtil.blankToDefault(data.getParentRunId(), "(未知)")).append('\n');
            builder.append("childRunId=").append(StrUtil.blankToDefault(data.getChildRunId(), "(未知)")).append('\n');
            builder.append("status=").append(StrUtil.blankToDefault(data.getStatus(), "(未知)")).append('\n');
            if (StrUtil.isNotBlank(data.getBatchKey())) {
                builder.append("batchKey=").append(data.getBatchKey()).append('\n');
            }
            builder.append("task=").append(StrUtil.blankToDefault(data.getTaskDescription(), "(未记录任务描述)")).append('\n');
            if (StrUtil.isNotBlank(data.getResult())) {
                builder.append("result=\n").append(data.getResult());
            } else if (StrUtil.isNotBlank(data.getErrorMessage())) {
                builder.append("error=\n").append(data.getErrorMessage());
            }
            return builder.toString().trim();
        }
        return StrUtil.blankToDefault(event.getContent(), "[系统事件]");
    }

    /**
     * 将会话键转换为安全目录名。
     *
     * @param sessionKey 会话键
     * @return 安全目录名
     */
    private String safeSessionKey(String sessionKey) {
        return DigestUtil.sha1Hex(StrUtil.blankToDefault(sessionKey, "default"));
    }

    /**
     * 为目标文件返回一个复用锁对象。
     *
     * @param file 目标文件
     * @return 文件锁
     */
    private ReentrantLock lock(File file) {
        return pathLocks.computeIfAbsent(file.getAbsolutePath(), key -> new ReentrantLock());
    }
}


