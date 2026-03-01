package com.jimuqu.solonclaw.logging;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志统计信息
 */
public class LogStats {
    private int totalFiles;
    private long totalSize;
    private Map<String, Long> levelCounts;

    public LogStats() {
        this.levelCounts = new HashMap<>();
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public Map<String, Long> getLevelCounts() {
        return levelCounts;
    }

    public void setLevelCounts(Map<String, Long> levelCounts) {
        this.levelCounts = levelCounts;
    }

    public void addLevelCount(String level, long count) {
        this.levelCounts.put(level, count);
    }
}