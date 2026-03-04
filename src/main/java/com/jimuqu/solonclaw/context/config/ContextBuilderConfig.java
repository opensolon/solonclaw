package com.jimuqu.solonclaw.context.config;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上下文构建器配置
 * <p>
 * 从配置文件读取上下文构建器的相关配置
 *
 * @author SolonClaw
 */
@Component
@Configuration
public class ContextBuilderConfig {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilderConfig.class);

    // 系统上下文配置
    private boolean systemEnabled = true;

    private boolean includeToolsInSystem = true;

    // 工具上下文配置
    private boolean toolsEnabled = true;

    private boolean includeParameters = true;

    // 知识上下文配置
    private boolean knowledgeEnabled = true;

    private int maxSearchResults = 5;

    private double minConfidenceThreshold = 0.6;

    // 会话上下文配置
    private boolean sessionEnabled = true;

    private int maxHistoryMessages = 10;

    private int maxSummaryLength = 500;

    // Solon 依赖注入（会覆盖默认值）
    @Inject("${solonclaw.context.builder.system.enabled:true}")
    public void setSystemEnabledInjected(boolean systemEnabled) {
        this.systemEnabled = systemEnabled;
    }

    @Inject("${solonclaw.context.builder.system.includeTools:true}")
    public void setIncludeToolsInSystemInjected(boolean includeToolsInSystem) {
        this.includeToolsInSystem = includeToolsInSystem;
    }

    @Inject("${solonclaw.context.builder.tools.enabled:false}")
    public void setToolsEnabledInjected(boolean toolsEnabled) {
        this.toolsEnabled = toolsEnabled;
    }

    @Inject("${solonclaw.context.builder.tools.includeParameters:true}")
    public void setIncludeParametersInjected(boolean includeParameters) {
        this.includeParameters = includeParameters;
    }

    @Inject("${solonclaw.context.builder.knowledge.enabled:true}")
    public void setKnowledgeEnabledInjected(boolean knowledgeEnabled) {
        this.knowledgeEnabled = knowledgeEnabled;
    }

    @Inject("${solonclaw.context.builder.knowledge.maxSearchResults:5}")
    public void setMaxSearchResultsInjected(int maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    @Inject("${solonclaw.context.builder.knowledge.minConfidenceThreshold:0.6}")
    public void setMinConfidenceThresholdInjected(double minConfidenceThreshold) {
        this.minConfidenceThreshold = minConfidenceThreshold;
    }

    @Inject("${solonclaw.context.builder.session.enabled:true}")
    public void setSessionEnabledInjected(boolean sessionEnabled) {
        this.sessionEnabled = sessionEnabled;
    }

    @Inject("${solonclaw.context.builder.session.maxHistoryMessages:10}")
    public void setMaxHistoryMessagesInjected(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    @Inject("${solonclaw.context.builder.session.maxSummaryLength:500}")
    public void setMaxSummaryLengthInjected(int maxSummaryLength) {
        this.maxSummaryLength = maxSummaryLength;
    }

    public ContextBuilderConfig() {
    }

    // Getters and Setters

    public boolean isSystemEnabled() {
        return systemEnabled;
    }

    public void setSystemEnabled(boolean systemEnabled) {
        this.systemEnabled = systemEnabled;
    }

    public boolean isIncludeToolsInSystem() {
        return includeToolsInSystem;
    }

    public void setIncludeToolsInSystem(boolean includeToolsInSystem) {
        this.includeToolsInSystem = includeToolsInSystem;
    }

    public boolean isToolsEnabled() {
        return toolsEnabled;
    }

    public void setToolsEnabled(boolean toolsEnabled) {
        this.toolsEnabled = toolsEnabled;
    }

    public boolean isIncludeParameters() {
        return includeParameters;
    }

    public void setIncludeParameters(boolean includeParameters) {
        this.includeParameters = includeParameters;
    }

    public boolean isKnowledgeEnabled() {
        return knowledgeEnabled;
    }

    public void setKnowledgeEnabled(boolean knowledgeEnabled) {
        this.knowledgeEnabled = knowledgeEnabled;
    }

    public int getMaxSearchResults() {
        return maxSearchResults;
    }

    public void setMaxSearchResults(int maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    public double getMinConfidenceThreshold() {
        return minConfidenceThreshold;
    }

    public void setMinConfidenceThreshold(double minConfidenceThreshold) {
        this.minConfidenceThreshold = minConfidenceThreshold;
    }

    public boolean isSessionEnabled() {
        return sessionEnabled;
    }

    public void setSessionEnabled(boolean sessionEnabled) {
        this.sessionEnabled = sessionEnabled;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public int getMaxSummaryLength() {
        return maxSummaryLength;
    }

    public void setMaxSummaryLength(int maxSummaryLength) {
        this.maxSummaryLength = maxSummaryLength;
    }

    @Override
    public String toString() {
        return "ContextBuilderConfig{" +
            "systemEnabled=" + systemEnabled +
            ", includeToolsInSystem=" + includeToolsInSystem +
            ", toolsEnabled=" + toolsEnabled +
            ", includeParameters=" + includeParameters +
            ", knowledgeEnabled=" + knowledgeEnabled +
            ", maxSearchResults=" + maxSearchResults +
            ", minConfidenceThreshold=" + minConfidenceThreshold +
            ", sessionEnabled=" + sessionEnabled +
            ", maxHistoryMessages=" + maxHistoryMessages +
            ", maxSummaryLength=" + maxSummaryLength +
            '}';
    }
}