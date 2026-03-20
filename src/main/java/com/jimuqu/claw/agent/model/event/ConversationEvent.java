package com.jimuqu.claw.agent.model.event;

import com.jimuqu.claw.agent.model.enums.RuntimeSourceKind;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 表示会话历史中的单条事件记录。
 */
@Data
@NoArgsConstructor
public class ConversationEvent implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 事件版本号。 */
    private long version;
    /** 所属会话键。 */
    private String sessionKey;
    /** 事件类型。 */
    private String eventType;
    /** 关联运行任务标识。 */
    private String runId;
    /** 来源类型。 */
    private RuntimeSourceKind sourceKind;
    /** 来源消息标识。 */
    private String sourceMessageId;
    /** 来源用户消息对应的版本号。 */
    private long sourceUserVersion;
    /** 事件角色，例如 user、assistant、system。 */
    private String role;
    /** 事件文本内容。 */
    private String content;
    /** 事件结构化数据的 JSON 表示。 */
    private String eventDataJson;
    /** 事件创建时间戳。 */
    private long createdAt;
}
