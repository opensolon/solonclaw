package com.jimuqu.solonclaw.agent.event;

import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内部事件存储
 * <p>
 * 管理 Agent 内部事件的存储和检索
 * 参考 OpenClaw 的内部事件系统设计
 *
 * @author SolonClaw
 */
@Component
public class EventStore {

    private static final Logger log = LoggerFactory.getLogger(EventStore.class);

    /**
     * 按会话键存储事件列表
     * Key: 父会话键
     * Value: 该会话的所有内部事件
     */
    private final Map<String, List<AgentInternalEvent>> eventsBySession = new ConcurrentHashMap<>();

    /**
     * 按运行 ID 存储事件
     * Key: runId
     * Value: 对应的内部事件
     */
    private final Map<String, AgentInternalEvent> eventsByRunId = new ConcurrentHashMap<>();

    /**
     * 添加内部事件
     *
     * @param event 内部事件
     */
    public void addEvent(AgentInternalEvent event) {
        String sessionKey = event.getChildSessionKey();
        String runId = event.getTaskLabel(); // 使用 taskLabel 作为 runId 的替代

        log.debug("添加内部事件: sessionKey={}, taskLabel={}, status={}",
                sessionKey, event.getTaskLabel(), event.getStatus());

        // 按会话键存储
        eventsBySession.computeIfAbsent(sessionKey, k -> new CopyOnWriteArrayList<>()).add(event);

        // 按 runId 存储
        eventsByRunId.put(runId, event);

        log.info("内部事件已添加: sessionKey={}, 事件总数={}",
                sessionKey, eventsBySession.get(sessionKey).size());
    }

    /**
     * 获取指定会话的所有事件
     *
     * @param sessionKey 会话键
     * @return 事件列表
     */
    public List<AgentInternalEvent> getEvents(String sessionKey) {
        return eventsBySession.getOrDefault(sessionKey, new ArrayList<>());
    }

    /**
     * 获取指定会话的未处理事件
     *
     * @param sessionKey 会话键
     * @return 未处理事件列表
     */
    public List<AgentInternalEvent> getPendingEvents(String sessionKey) {
        List<AgentInternalEvent> allEvents = eventsBySession.getOrDefault(sessionKey, new ArrayList<>());
        return new ArrayList<>(allEvents); // 简化实现：返回所有事件
    }

    /**
     * 获取指定会话的事件并清除
     *
     * @param sessionKey 会话键
     * @return 事件列表
     */
    public List<AgentInternalEvent> getAndClearEvents(String sessionKey) {
        List<AgentInternalEvent> events = eventsBySession.remove(sessionKey);
        if (events != null) {
            // 同时清除按 runId 的索引
            for (AgentInternalEvent event : events) {
                eventsByRunId.remove(event.getTaskLabel());
            }
        }
        return events != null ? events : new ArrayList<>();
    }

    /**
     * 清除指定会话的所有事件
     *
     * @param sessionKey 会话键
     */
    public void clearEvents(String sessionKey) {
        List<AgentInternalEvent> events = eventsBySession.remove(sessionKey);
        if (events != null) {
            for (AgentInternalEvent event : events) {
                eventsByRunId.remove(event.getTaskLabel());
            }
            log.info("已清除会话事件: sessionKey={}, 清除数量={}", sessionKey, events.size());
        }
    }

    /**
     * 根据任务标签获取事件
     *
     * @param taskLabel 任务标签
     * @return 内部事件
     */
    public AgentInternalEvent getEventByTaskLabel(String taskLabel) {
        return eventsByRunId.get(taskLabel);
    }

    /**
     * 获取所有会话的事件总数
     *
     * @return 总事件数
     */
    public int getTotalEventCount() {
        return eventsBySession.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 获取指定会话的事件数量
     *
     * @param sessionKey 会话键
     * @return 事件数量
     */
    public int getEventCount(String sessionKey) {
        return eventsBySession.getOrDefault(sessionKey, new ArrayList<>()).size();
    }
}