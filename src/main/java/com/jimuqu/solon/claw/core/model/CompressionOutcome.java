package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 单次上下文压缩结果。 */
@Getter
@Setter
@NoArgsConstructor
public class CompressionOutcome {
    private SessionRecord session;
    private boolean compressed;
    private boolean skipped;
    private boolean failed;
    private String warning;
    private String errorMessage;
    private int estimatedTokens;
    private int thresholdTokens;

    public static CompressionOutcome skipped(SessionRecord session) {
        CompressionOutcome outcome = new CompressionOutcome();
        outcome.setSession(session);
        outcome.setSkipped(true);
        return outcome;
    }

    public static CompressionOutcome success(SessionRecord session, boolean compressed) {
        CompressionOutcome outcome = new CompressionOutcome();
        outcome.setSession(session);
        outcome.setCompressed(compressed);
        outcome.setSkipped(!compressed);
        return outcome;
    }

    public static CompressionOutcome failed(SessionRecord session, Throwable error) {
        CompressionOutcome outcome = new CompressionOutcome();
        outcome.setSession(session);
        outcome.setFailed(true);
        outcome.setWarning("上下文压缩摘要生成失败，本轮已继续执行；较早对话可能没有被完整压缩。");
        outcome.setErrorMessage(error == null ? "" : error.getMessage());
        return outcome;
    }
}
