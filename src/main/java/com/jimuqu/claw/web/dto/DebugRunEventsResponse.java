package com.jimuqu.claw.web.dto;

import com.jimuqu.claw.agent.model.event.RunEvent;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述调试页轮询运行事件时返回的数据。
 */
@Data
@NoArgsConstructor
public class DebugRunEventsResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本次返回的事件列表。 */
    private List<RunEvent> events = new ArrayList<RunEvent>();
    /** 当前已返回到的最后事件序号。 */
    private long lastSeq;
}
