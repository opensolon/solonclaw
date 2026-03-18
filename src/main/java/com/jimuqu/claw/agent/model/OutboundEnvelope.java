package com.jimuqu.claw.agent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一描述要发送到外部渠道的出站消息。
 */
public class OutboundEnvelope {
    /** 所属运行任务标识。 */
    private String runId;
    /** 实际回复目标。 */
    private ReplyTarget replyTarget;
    /** 文本内容。 */
    private String content;
    /** 附件或媒体路径列表。 */
    private List<String> media = new ArrayList<>();
    /** 是否为进度消息。 */
    private boolean progress;

    /**
     * 返回运行任务标识。
     *
     * @return 运行任务标识
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 设置运行任务标识。
     *
     * @param runId 运行任务标识
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * 返回回复目标。
     *
     * @return 回复目标
     */
    public ReplyTarget getReplyTarget() {
        return replyTarget;
    }

    /**
     * 设置回复目标。
     *
     * @param replyTarget 回复目标
     */
    public void setReplyTarget(ReplyTarget replyTarget) {
        this.replyTarget = replyTarget;
    }

    /**
     * 返回文本内容。
     *
     * @return 文本内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置文本内容。
     *
     * @param content 文本内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 返回媒体列表。
     *
     * @return 媒体列表
     */
    public List<String> getMedia() {
        return media;
    }

    /**
     * 设置媒体列表。
     *
     * @param media 媒体列表
     */
    public void setMedia(List<String> media) {
        this.media = media;
    }

    /**
     * 判断当前消息是否为进度消息。
     *
     * @return 若为进度消息则返回 true
     */
    public boolean isProgress() {
        return progress;
    }

    /**
     * 设置是否为进度消息。
     *
     * @param progress 进度标记
     */
    public void setProgress(boolean progress) {
        this.progress = progress;
    }
}
