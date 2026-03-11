package com.jimuqu.solonclaw.memory.file;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件记忆配置
 *
 * @author SolonClaw
 */
@Component
public class MemoryFileConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryFileConfig.class);

    /**
     * 是否启用文件记忆
     */
    private boolean enabled = true;

    /**
     * 笔记保留天数
     */
    private int retainDays = 30;

    /**
     * 是否自动追加事件
     */
    private boolean autoAppendEvents = true;

    /**
     * 最大笔记大小（字节）
     */
    private int maxNoteSize = 102400;

    @Inject("${solonclaw.memory.file.enabled:true}")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.debug("文件记忆 enabled: {}", enabled);
    }

    @Inject("${solonclaw.memory.file.retainDays:30}")
    public void setRetainDays(int retainDays) {
        this.retainDays = retainDays;
        log.debug("文件记忆 retainDays: {}", retainDays);
    }

    @Inject("${solonclaw.memory.file.autoAppendEvents:true}")
    public void setAutoAppendEvents(boolean autoAppendEvents) {
        this.autoAppendEvents = autoAppendEvents;
        log.debug("文件记忆 autoAppendEvents: {}", autoAppendEvents);
    }

    @Inject("${solonclaw.memory.file.maxNoteSize:102400}")
    public void setMaxNoteSize(int maxNoteSize) {
        this.maxNoteSize = maxNoteSize;
        log.debug("文件记忆 maxNoteSize: {}", maxNoteSize);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRetainDays() {
        return retainDays;
    }

    public boolean isAutoAppendEvents() {
        return autoAppendEvents;
    }

    public int getMaxNoteSize() {
        return maxNoteSize;
    }
}
