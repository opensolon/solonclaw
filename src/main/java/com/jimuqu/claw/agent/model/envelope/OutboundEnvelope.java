package com.jimuqu.claw.agent.model.envelope;

import com.jimuqu.claw.agent.model.route.ReplyTarget;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一描述要发送到外部渠道的出站消息。
 */
@Data
@NoArgsConstructor
public class OutboundEnvelope implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 所属运行任务标识。 */
    private String runId;
    /** 实际回复目标。 */
    private ReplyTarget replyTarget;
    /** 文本内容。 */
    private String content;
    /** 附件或媒体路径列表。 */
    private List<String> media = new ArrayList<String>();
    /** 是否为进度消息。 */
    private boolean progress;
}
