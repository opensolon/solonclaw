package com.jimuqu.solonclaw.logging;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 日志查询条件
 */
public class LogQuery {
    private Set<LogLevel> levels;
    private Set<String> sources;
    private String sessionId;
    private String keyword;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer page;
    private Integer pageSize;
    private Integer maxFiles;

    public LogQuery() {
        this.levels = new HashSet<>();
        this.sources = new HashSet<>();
        this.page = 1;
        this.pageSize = 100;
        this.maxFiles = 30;
    }

    public Set<LogLevel> getLevels() {
        return levels;
    }

    public LogQuery setLevels(Set<LogLevel> levels) {
        this.levels = levels;
        return this;
    }

    public LogQuery addLevel(LogLevel level) {
        if (this.levels == null) {
            this.levels = new HashSet<>();
        }
        this.levels.add(level);
        return this;
    }

    public Set<String> getSources() {
        return sources;
    }

    public LogQuery setSources(Set<String> sources) {
        this.sources = sources;
        return this;
    }

    public LogQuery addSource(String source) {
        if (this.sources == null) {
            this.sources = new HashSet<>();
        }
        this.sources.add(source);
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public LogQuery setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getKeyword() {
        return keyword;
    }

    public LogQuery setKeyword(String keyword) {
        this.keyword = keyword;
        return this;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LogQuery setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        return this;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public LogQuery setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    public Integer getPage() {
        return page;
    }

    public LogQuery setPage(Integer page) {
        this.page = page;
        return this;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public LogQuery setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public Integer getMaxFiles() {
        return maxFiles;
    }

    public LogQuery setMaxFiles(Integer maxFiles) {
        this.maxFiles = maxFiles;
        return this;
    }
}