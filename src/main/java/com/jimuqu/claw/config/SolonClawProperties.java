package com.jimuqu.claw.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合 SolonClaw 项目的自定义配置。
 */
public class SolonClawProperties {
    /** 工作区目录。 */
    private String workspace = "./workspace";
    /** Agent 相关配置。 */
    private Agent agent = new Agent();
    /** 渠道相关配置。 */
    private Channels channels = new Channels();

    /**
     * 返回工作区目录。
     *
     * @return 工作区目录
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * 设置工作区目录。
     *
     * @param workspace 工作区目录
     */
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    /**
     * 返回 Agent 配置。
     *
     * @return Agent 配置
     */
    public Agent getAgent() {
        return agent;
    }

    /**
     * 设置 Agent 配置。
     *
     * @param agent Agent 配置
     */
    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    /**
     * 返回渠道配置。
     *
     * @return 渠道配置
     */
    public Channels getChannels() {
        return channels;
    }

    /**
     * 设置渠道配置。
     *
     * @param channels 渠道配置
     */
    public void setChannels(Channels channels) {
        this.channels = channels;
    }

    /**
     * 描述 Agent 行为配置。
     */
    public static class Agent {
        /** 基础系统提示词。 */
        private String systemPrompt;
        /** 调度器配置。 */
        private Scheduler scheduler = new Scheduler();
        /** 工具配置。 */
        private Tools tools = new Tools();
        /** 心跳配置。 */
        private Heartbeat heartbeat = new Heartbeat();

        /**
         * 返回系统提示词。
         *
         * @return 系统提示词
         */
        public String getSystemPrompt() {
            return systemPrompt;
        }

        /**
         * 设置系统提示词。
         *
         * @param systemPrompt 系统提示词
         */
        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        /**
         * 返回调度器配置。
         *
         * @return 调度器配置
         */
        public Scheduler getScheduler() {
            return scheduler;
        }

        /**
         * 设置调度器配置。
         *
         * @param scheduler 调度器配置
         */
        public void setScheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
        }

        /**
         * 返回工具配置。
         *
         * @return 工具配置
         */
        public Tools getTools() {
            return tools;
        }

        /**
         * 设置工具配置。
         *
         * @param tools 工具配置
         */
        public void setTools(Tools tools) {
            this.tools = tools;
        }

        /**
         * 返回心跳配置。
         *
         * @return 心跳配置
         */
        public Heartbeat getHeartbeat() {
            return heartbeat;
        }

        /**
         * 设置心跳配置。
         *
         * @param heartbeat 心跳配置
         */
        public void setHeartbeat(Heartbeat heartbeat) {
            this.heartbeat = heartbeat;
        }
    }

    /**
     * 描述工具能力配置。
     */
    public static class Tools {
        /** CLI TerminalSkill 是否启用沙盒模式。 */
        private boolean sandboxMode = true;

        /**
         * 返回 TerminalSkill 是否启用沙盒模式。
         *
         * @return 若启用则返回 true
         */
        public boolean isSandboxMode() {
            return sandboxMode;
        }

        /**
         * 设置 TerminalSkill 是否启用沙盒模式。
         *
         * @param sandboxMode 启用标记
         */
        public void setSandboxMode(boolean sandboxMode) {
            this.sandboxMode = sandboxMode;
        }
    }

    /**
     * 描述并发调度配置。
     */
    public static class Scheduler {
        /** 单会话最大并发数。 */
        private int maxConcurrentPerConversation = 4;
        /** 忙时是否立即回执确认消息。 */
        private boolean ackWhenBusy = true;

        /**
         * 返回单会话最大并发数。
         *
         * @return 单会话最大并发数
         */
        public int getMaxConcurrentPerConversation() {
            return maxConcurrentPerConversation;
        }

        /**
         * 设置单会话最大并发数。
         *
         * @param maxConcurrentPerConversation 单会话最大并发数
         */
        public void setMaxConcurrentPerConversation(int maxConcurrentPerConversation) {
            this.maxConcurrentPerConversation = maxConcurrentPerConversation;
        }

        /**
         * 返回是否忙时回执。
         *
         * @return 若启用则返回 true
         */
        public boolean isAckWhenBusy() {
            return ackWhenBusy;
        }

        /**
         * 设置是否忙时回执。
         *
         * @param ackWhenBusy 忙时回执标记
         */
        public void setAckWhenBusy(boolean ackWhenBusy) {
            this.ackWhenBusy = ackWhenBusy;
        }
    }

    /**
     * 描述心跳任务配置。
     */
    public static class Heartbeat {
        /** 是否启用心跳。 */
        private boolean enabled = true;
        /** 心跳触发间隔，单位秒。 */
        private int intervalSeconds = 1800;

        /**
         * 返回是否启用心跳。
         *
         * @return 若启用则返回 true
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用心跳。
         *
         * @param enabled 启用标记
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回心跳间隔。
         *
         * @return 心跳间隔秒数
         */
        public int getIntervalSeconds() {
            return intervalSeconds;
        }

        /**
         * 设置心跳间隔。
         *
         * @param intervalSeconds 心跳间隔秒数
         */
        public void setIntervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
    }

    /**
     * 描述所有渠道配置的聚合对象。
     */
    public static class Channels {
        /** 飞书渠道配置。 */
        private Feishu feishu = new Feishu();
        /** 钉钉渠道配置。 */
        private DingTalk dingtalk = new DingTalk();

        /**
         * 返回飞书配置。
         *
         * @return 飞书配置
         */
        public Feishu getFeishu() {
            return feishu;
        }

        /**
         * 设置飞书配置。
         *
         * @param feishu 飞书配置
         */
        public void setFeishu(Feishu feishu) {
            this.feishu = feishu;
        }

        /**
         * 返回钉钉配置。
         *
         * @return 钉钉配置
         */
        public DingTalk getDingtalk() {
            return dingtalk;
        }

        /**
         * 设置钉钉配置。
         *
         * @param dingtalk 钉钉配置
         */
        public void setDingtalk(DingTalk dingtalk) {
            this.dingtalk = dingtalk;
        }
    }

    /**
     * 描述飞书机器人配置。
     */
    public static class Feishu {
        /** 是否启用飞书渠道。 */
        private boolean enabled;
        /** 飞书 appId。 */
        private String appId = "";
        /** 飞书 appSecret。 */
        private String appSecret = "";
        /** 飞书开放平台域名。 */
        private String baseDomain = "https://open.feishu.cn";
        /** 是否启用基于卡片 patch 的流式更新。 */
        private boolean streamingReply = true;
        /** 私聊允许列表。 */
        private List<String> allowFrom = new ArrayList<>();
        /** 群聊允许列表。 */
        private List<String> groupAllowFrom = new ArrayList<>();

        /**
         * 返回飞书渠道启用状态。
         *
         * @return 若启用则返回 true
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置飞书渠道启用状态。
         *
         * @param enabled 启用标记
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回 appId。
         *
         * @return appId
         */
        public String getAppId() {
            return appId;
        }

        /**
         * 设置 appId。
         *
         * @param appId appId
         */
        public void setAppId(String appId) {
            this.appId = appId;
        }

        /**
         * 返回 appSecret。
         *
         * @return appSecret
         */
        public String getAppSecret() {
            return appSecret;
        }

        /**
         * 设置 appSecret。
         *
         * @param appSecret appSecret
         */
        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        /**
         * 返回开放平台域名。
         *
         * @return 开放平台域名
         */
        public String getBaseDomain() {
            return baseDomain;
        }

        /**
         * 设置开放平台域名。
         *
         * @param baseDomain 开放平台域名
         */
        public void setBaseDomain(String baseDomain) {
            this.baseDomain = baseDomain;
        }

        /**
         * 返回是否启用流式更新。
         *
         * @return 若启用则返回 true
         */
        public boolean isStreamingReply() {
            return streamingReply;
        }

        /**
         * 设置是否启用流式更新。
         *
         * @param streamingReply 流式更新标记
         */
        public void setStreamingReply(boolean streamingReply) {
            this.streamingReply = streamingReply;
        }

        /**
         * 返回私聊白名单。
         *
         * @return 私聊白名单
         */
        public List<String> getAllowFrom() {
            return allowFrom;
        }

        /**
         * 设置私聊白名单。
         *
         * @param allowFrom 私聊白名单
         */
        public void setAllowFrom(List<String> allowFrom) {
            this.allowFrom = allowFrom;
        }

        /**
         * 返回群聊白名单。
         *
         * @return 群聊白名单
         */
        public List<String> getGroupAllowFrom() {
            return groupAllowFrom;
        }

        /**
         * 设置群聊白名单。
         *
         * @param groupAllowFrom 群聊白名单
         */
        public void setGroupAllowFrom(List<String> groupAllowFrom) {
            this.groupAllowFrom = groupAllowFrom;
        }
    }

    /**
     * 描述钉钉机器人配置。
     */
    public static class DingTalk {
        /** 是否启用钉钉渠道。 */
        private boolean enabled;
        /** 钉钉 clientId。 */
        private String clientId = "";
        /** 钉钉 clientSecret。 */
        private String clientSecret = "";
        /** 钉钉 robotCode。 */
        private String robotCode = "";
        /** 私聊允许列表。 */
        private List<String> allowFrom = new ArrayList<>();
        /** 群聊允许列表。 */
        private List<String> groupAllowFrom = new ArrayList<>();

        /**
         * 返回钉钉渠道启用状态。
         *
         * @return 若启用则返回 true
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置钉钉渠道启用状态。
         *
         * @param enabled 启用标记
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回 clientId。
         *
         * @return clientId
         */
        public String getClientId() {
            return clientId;
        }

        /**
         * 设置 clientId。
         *
         * @param clientId clientId
         */
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        /**
         * 返回 clientSecret。
         *
         * @return clientSecret
         */
        public String getClientSecret() {
            return clientSecret;
        }

        /**
         * 设置 clientSecret。
         *
         * @param clientSecret clientSecret
         */
        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        /**
         * 返回 robotCode。
         *
         * @return robotCode
         */
        public String getRobotCode() {
            return robotCode;
        }

        /**
         * 设置 robotCode。
         *
         * @param robotCode robotCode
         */
        public void setRobotCode(String robotCode) {
            this.robotCode = robotCode;
        }

        /**
         * 返回私聊白名单。
         *
         * @return 私聊白名单
         */
        public List<String> getAllowFrom() {
            return allowFrom;
        }

        /**
         * 设置私聊白名单。
         *
         * @param allowFrom 私聊白名单
         */
        public void setAllowFrom(List<String> allowFrom) {
            this.allowFrom = allowFrom;
        }

        /**
         * 返回群聊白名单。
         *
         * @return 群聊白名单
         */
        public List<String> getGroupAllowFrom() {
            return groupAllowFrom;
        }

        /**
         * 设置群聊白名单。
         *
         * @param groupAllowFrom 群聊白名单
         */
        public void setGroupAllowFrom(List<String> groupAllowFrom) {
            this.groupAllowFrom = groupAllowFrom;
        }
    }
}
