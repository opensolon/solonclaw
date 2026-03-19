package com.jimuqu.claw.web.dto;

import com.jimuqu.claw.agent.model.event.RunEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述调试页轮询运行事件时返回的数据。
 */
public class DebugRunEventsResponse {
    /** 本次返回的事件列表。 */
    private List<RunEvent> events = new ArrayList<>();
    /** 当前已返回到的最后事件序号。 */
    private long lastSeq;

    /**
     * 返回事件列表。
     *
     * @return 事件列表
     */
    public List<RunEvent> getEvents() {
        return events;
    }

    /**
     * 设置事件列表。
     *
     * @param events 事件列表
     */
    public void setEvents(List<RunEvent> events) {
        this.events = events;
    }

    /**
     * 返回最后事件序号。
     *
     * @return 最后事件序号
     */
    public long getLastSeq() {
        return lastSeq;
    }

    /**
     * 设置最后事件序号。
     *
     * @param lastSeq 最后事件序号
     */
    public void setLastSeq(long lastSeq) {
        this.lastSeq = lastSeq;
    }
}


