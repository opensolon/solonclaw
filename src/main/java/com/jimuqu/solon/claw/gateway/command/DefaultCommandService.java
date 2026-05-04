package com.jimuqu.solon.claw.gateway.command;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentProfileService;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.context.LocalSkillService;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.AgentRunStopResult;
import com.jimuqu.solon.claw.core.model.CheckpointRecord;
import com.jimuqu.solon.claw.core.model.CronJobRecord;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.core.service.CommandService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.ContextService;
import com.jimuqu.solon.claw.core.service.ConversationEventSink;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.core.service.SkillHubService;
import com.jimuqu.solon.claw.core.service.ToolRegistry;
import com.jimuqu.solon.claw.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.ScanResult;
import com.jimuqu.solon.claw.skillhub.model.SkillBrowseResult;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.model.TapRecord;
import com.jimuqu.solon.claw.storage.session.SqliteAgentSession;
import com.jimuqu.solon.claw.support.CronSupport;
import com.jimuqu.solon.claw.support.DisplaySettingsService;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.MessageSupport;
import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import com.jimuqu.solon.claw.support.SourceKeySupport;
import com.jimuqu.solon.claw.support.constants.AgentSettingConstants;
import com.jimuqu.solon.claw.support.constants.GatewayCommandConstants;
import com.jimuqu.solon.claw.support.update.AppUpdateService;
import com.jimuqu.solon.claw.tool.runtime.DangerousCommandApprovalService;
import com.jimuqu.solon.claw.tool.runtime.ProcessRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 默认 slash 命令实现，统一承接 Hermes 风格的会话控制命令。 */
public class DefaultCommandService implements CommandService {
    /** 会话仓储。 */
    private final SessionRepository sessionRepository;

    /** 工具注册表。 */
    private final ToolRegistry toolRegistry;

    /** 本地技能服务。 */
    private final LocalSkillService localSkillService;

    /** 定时任务仓储。 */
    private final CronJobRepository cronJobRepository;

    /** 对话编排器。 */
    private final ConversationOrchestrator conversationOrchestrator;

    /** 上下文服务。 */
    private final ContextService contextService;

    /** 上下文压缩服务。 */
    private final ContextCompressionService contextCompressionService;

    /** 渠道投递服务。 */
    private final DeliveryService deliveryService;

    /** 授权服务。 */
    private final GatewayAuthorizationService gatewayAuthorizationService;

    /** checkpoint 服务。 */
    private final CheckpointService checkpointService;

    private final SkillHubService skillHubService;

    /** 应用配置。 */
    private final AppConfig appConfig;

    /** 全局设置仓储。 */
    private final GlobalSettingRepository globalSettingRepository;

    /** 进程注册表。 */
    private final ProcessRegistry processRegistry;

    /** 运行时设置服务。 */
    private final RuntimeSettingsService runtimeSettingsService;

    private final DisplaySettingsService displaySettingsService;
    private final AppUpdateService appUpdateService;
    private final DangerousCommandApprovalService dangerousCommandApprovalService;
    private final AgentRunControlService agentRunControlService;
    private final AgentProfileService agentProfileService;
    private final AgentRunRepository agentRunRepository;

    public DefaultCommandService(
            SessionRepository sessionRepository,
            ToolRegistry toolRegistry,
            LocalSkillService localSkillService,
            CronJobRepository cronJobRepository,
            ConversationOrchestrator conversationOrchestrator,
            ContextService contextService,
            ContextCompressionService contextCompressionService,
            DeliveryService deliveryService,
            GatewayAuthorizationService gatewayAuthorizationService,
            CheckpointService checkpointService,
            SkillHubService skillHubService,
            AppConfig appConfig,
            GlobalSettingRepository globalSettingRepository,
            ProcessRegistry processRegistry,
            RuntimeSettingsService runtimeSettingsService,
            DisplaySettingsService displaySettingsService,
            AppUpdateService appUpdateService,
            DangerousCommandApprovalService dangerousCommandApprovalService,
            AgentRunControlService agentRunControlService,
            AgentProfileService agentProfileService) {
        this(
                sessionRepository,
                toolRegistry,
                localSkillService,
                cronJobRepository,
                conversationOrchestrator,
                contextService,
                contextCompressionService,
                deliveryService,
                gatewayAuthorizationService,
                checkpointService,
                skillHubService,
                appConfig,
                globalSettingRepository,
                processRegistry,
                runtimeSettingsService,
                displaySettingsService,
                appUpdateService,
                dangerousCommandApprovalService,
                agentRunControlService,
                agentProfileService,
                null);
    }

    public DefaultCommandService(
            SessionRepository sessionRepository,
            ToolRegistry toolRegistry,
            LocalSkillService localSkillService,
            CronJobRepository cronJobRepository,
            ConversationOrchestrator conversationOrchestrator,
            ContextService contextService,
            ContextCompressionService contextCompressionService,
            DeliveryService deliveryService,
            GatewayAuthorizationService gatewayAuthorizationService,
            CheckpointService checkpointService,
            SkillHubService skillHubService,
            AppConfig appConfig,
            GlobalSettingRepository globalSettingRepository,
            ProcessRegistry processRegistry,
            RuntimeSettingsService runtimeSettingsService,
            DisplaySettingsService displaySettingsService,
            AppUpdateService appUpdateService,
            DangerousCommandApprovalService dangerousCommandApprovalService,
            AgentRunControlService agentRunControlService,
            AgentProfileService agentProfileService,
            AgentRunRepository agentRunRepository) {
        this.sessionRepository = sessionRepository;
        this.toolRegistry = toolRegistry;
        this.localSkillService = localSkillService;
        this.cronJobRepository = cronJobRepository;
        this.conversationOrchestrator = conversationOrchestrator;
        this.contextService = contextService;
        this.contextCompressionService = contextCompressionService;
        this.deliveryService = deliveryService;
        this.gatewayAuthorizationService = gatewayAuthorizationService;
        this.checkpointService = checkpointService;
        this.skillHubService = skillHubService;
        this.appConfig = appConfig;
        this.globalSettingRepository = globalSettingRepository;
        this.processRegistry = processRegistry;
        this.runtimeSettingsService = runtimeSettingsService;
        this.displaySettingsService = displaySettingsService;
        this.appUpdateService = appUpdateService;
        this.dangerousCommandApprovalService = dangerousCommandApprovalService;
        this.agentRunControlService = agentRunControlService;
        this.agentProfileService = agentProfileService;
        this.agentRunRepository = agentRunRepository;
    }

    /** 判断当前命令是否由默认命令服务承接。 */
    @Override
    public boolean supports(String commandName) {
        return Arrays.asList(
                        GatewayCommandConstants.COMMAND_NEW,
                        GatewayCommandConstants.COMMAND_RESET,
                        GatewayCommandConstants.COMMAND_RETRY,
                        GatewayCommandConstants.COMMAND_UNDO,
                        GatewayCommandConstants.COMMAND_BRANCH,
                        GatewayCommandConstants.COMMAND_RESUME,
                        GatewayCommandConstants.COMMAND_STATUS,
                        GatewayCommandConstants.COMMAND_USAGE,
                        GatewayCommandConstants.COMMAND_REASONING,
                        GatewayCommandConstants.COMMAND_STOP,
                        GatewayCommandConstants.COMMAND_PERSONALITY,
                        GatewayCommandConstants.COMMAND_VERSION,
                        GatewayCommandConstants.COMMAND_MODEL,
                        GatewayCommandConstants.COMMAND_TOOLS,
                        GatewayCommandConstants.COMMAND_SKILLS,
                        GatewayCommandConstants.COMMAND_CRON,
                        GatewayCommandConstants.COMMAND_PLATFORMS,
                        GatewayCommandConstants.COMMAND_COMPRESS,
                        GatewayCommandConstants.COMMAND_ROLLBACK,
                        GatewayCommandConstants.COMMAND_SETHOME,
                        GatewayCommandConstants.COMMAND_PAIRING,
                        GatewayCommandConstants.COMMAND_APPROVE,
                        GatewayCommandConstants.COMMAND_DENY,
                        GatewayCommandConstants.COMMAND_AGENT,
                        GatewayCommandConstants.COMMAND_HELP)
                .contains(commandName);
    }

    /** 处理单条 slash 命令。 */
    @Override
    public GatewayReply handle(GatewayMessage message, String commandLine) throws Exception {
        String withoutSlash = commandLine.substring(1).trim();
        String[] parts = withoutSlash.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";
        recordSlashCommand(message, command, args);

        if (GatewayCommandConstants.COMMAND_AGENT.equals(command)) {
            return GatewayReply.ok(
                    agentProfileService.handleCommand(
                            args, sessionRepository, message.sourceKey()));
        }

        if (GatewayCommandConstants.COMMAND_NEW.equals(command)
                || GatewayCommandConstants.COMMAND_RESET.equals(command)) {
            SessionRecord created = sessionRepository.bindNewSession(message.sourceKey());
            GatewayReply reply = GatewayReply.ok("已创建新会话：" + created.getSessionId());
            reply.setSessionId(created.getSessionId());
            reply.setBranchName(created.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_RETRY.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            String lastUser = MessageSupport.getLastUserMessage(session.getNdjson());
            if (StrUtil.isBlank(lastUser)) {
                return GatewayReply.error("没有可重试的上一条用户消息。");
            }
            session.setNdjson(MessageSupport.removeLastTurn(session.getNdjson()));
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);

            GatewayMessage retryMessage =
                    new GatewayMessage(
                            message.getPlatform(),
                            message.getChatId(),
                            message.getUserId(),
                            lastUser);
            retryMessage.setThreadId(message.getThreadId());
            retryMessage.setChatType(message.getChatType());
            retryMessage.setChatName(message.getChatName());
            retryMessage.setUserName(message.getUserName());
            return conversationOrchestrator.handleIncoming(retryMessage);
        }

        if (GatewayCommandConstants.COMMAND_UNDO.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            session.setNdjson(MessageSupport.removeLastTurn(session.getNdjson()));
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
            GatewayReply reply = GatewayReply.ok("已从会话中移除上一轮对话：" + session.getSessionId());
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_BRANCH.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            String branchName =
                    StrUtil.isBlank(args) ? "branch-" + System.currentTimeMillis() : args;
            SessionRecord clone =
                    sessionRepository.cloneSession(
                            message.sourceKey(), session.getSessionId(), branchName);
            GatewayReply reply =
                    GatewayReply.ok("已创建分支 " + branchName + " -> " + clone.getSessionId());
            reply.setSessionId(clone.getSessionId());
            reply.setBranchName(clone.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_RESUME.equals(command)) {
            if (StrUtil.isBlank(args)) {
                return GatewayReply.error(
                        "用法：" + GatewayCommandConstants.SLASH_RESUME + " <session-id-or-branch>");
            }
            SessionRecord session = sessionRepository.findById(args);
            if (session == null) {
                session = sessionRepository.findBySourceAndBranch(message.sourceKey(), args);
            }
            if (session == null) {
                return GatewayReply.error("未找到对应会话或分支：" + args);
            }
            sessionRepository.bindSource(message.sourceKey(), session.getSessionId());
            GatewayReply reply = GatewayReply.ok("已恢复会话：" + session.getSessionId());
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_STATUS.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            int count = MessageSupport.countMessages(session.getNdjson());
            GatewayReply reply =
                    GatewayReply.ok(
                            "session="
                                    + session.getSessionId()
                                    + ", branch="
                                    + session.getBranchName()
                                    + ", messages="
                                    + count
                                    + ", model="
                                    + StrUtil.nullToDefault(session.getModelOverride(), "default")
                                    + ", agent="
                                    + StrUtil.blankToDefault(
                                            session.getActiveAgentName(), "default")
                                    + ", personality="
                                    + currentPersonalityName());
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_USAGE.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            GatewayReply reply = GatewayReply.ok(formatUsage(session));
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_REASONING.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            GatewayReply reply = handleReasoning(message, args);
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_STOP.equals(command)) {
            return handleStop(message);
        }

        if (GatewayCommandConstants.COMMAND_PERSONALITY.equals(command)) {
            return handlePersonality(args);
        }

        if (GatewayCommandConstants.COMMAND_VERSION.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            GatewayReply reply;
            if (StrUtil.isBlank(args)) {
                reply = GatewayReply.ok(appUpdateService.formatVersionReport(false));
            } else if ("check".equalsIgnoreCase(args) || "status".equalsIgnoreCase(args)) {
                reply = GatewayReply.ok(appUpdateService.formatVersionReport(true));
            } else if ("update".equalsIgnoreCase(args)
                    || "upgrade".equalsIgnoreCase(args)
                    || "run".equalsIgnoreCase(args)) {
                AppUpdateService.UpdateResult result = appUpdateService.startUpdate();
                reply =
                        result.isError()
                                ? GatewayReply.error(result.getMessage())
                                : GatewayReply.ok(result.getMessage());
            } else {
                reply = GatewayReply.error("用法：/version [check|update]");
            }
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_MODEL.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            if (StrUtil.isBlank(args)) {
                GatewayReply reply = GatewayReply.ok(runtimeSettingsService.describeModel(session));
                reply.setSessionId(session.getSessionId());
                reply.setBranchName(session.getBranchName());
                return reply;
            }

            ModelCommandInput input = parseModelCommand(args);
            if (input.clear) {
                sessionRepository.setModelOverride(session.getSessionId(), null);
                GatewayReply reply = GatewayReply.ok("已清除当前会话模型覆盖，下一条消息将回退到全局默认模型。");
                reply.setSessionId(session.getSessionId());
                reply.setBranchName(session.getBranchName());
                return reply;
            }
            if (StrUtil.isBlank(input.model)) {
                return GatewayReply.error(
                        "用法：/model [--global] <model> 或 /model [--global] <provider>:<model>");
            }

            if (input.global) {
                runtimeSettingsService.setGlobalModel(input.provider, input.model);
                GatewayReply reply =
                        GatewayReply.ok(
                                "已更新全局默认模型为："
                                        + (StrUtil.isNotBlank(input.provider)
                                                ? input.provider + ":"
                                                : "")
                                        + input.model
                                        + "（下一条消息生效）");
                reply.setSessionId(session.getSessionId());
                reply.setBranchName(session.getBranchName());
                return reply;
            }

            String override =
                    StrUtil.isNotBlank(input.provider)
                            ? input.provider + ":" + input.model
                            : input.model;
            sessionRepository.setModelOverride(session.getSessionId(), override);
            GatewayReply reply = GatewayReply.ok("已切换当前会话模型为：" + override + "（下一条消息生效）");
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_TOOLS.equals(command)) {
            return handleTools(message, args);
        }

        if (GatewayCommandConstants.COMMAND_SKILLS.equals(command)) {
            return handleSkills(message, args);
        }

        if (GatewayCommandConstants.COMMAND_SETHOME.equals(command)) {
            return gatewayAuthorizationService.setHome(message);
        }

        if (GatewayCommandConstants.COMMAND_PAIRING.equals(command)) {
            return handlePairing(message, args);
        }

        if (GatewayCommandConstants.COMMAND_APPROVE.equals(command)) {
            return handleDangerousApprove(message, args);
        }

        if (GatewayCommandConstants.COMMAND_DENY.equals(command)) {
            return handleDangerousDeny(message);
        }

        if (GatewayCommandConstants.COMMAND_CRON.equals(command)) {
            return handleCron(message, args);
        }

        if (GatewayCommandConstants.COMMAND_COMPRESS.equals(command)) {
            SessionRecord session = requireSession(message.sourceKey());
            String systemPrompt = contextService.buildSystemPrompt(message.sourceKey());
            session.setSystemPromptSnapshot(systemPrompt);
            session = contextCompressionService.compressNow(session, systemPrompt, args);
            sessionRepository.save(session);
            GatewayReply reply =
                    GatewayReply.ok(
                            StrUtil.isBlank(args) ? "已完成当前会话的上下文压缩。" : "已按关注主题完成当前会话的上下文压缩。");
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
            return reply;
        }

        if (GatewayCommandConstants.COMMAND_ROLLBACK.equals(command)) {
            if (StrUtil.isBlank(args)) {
                return GatewayReply.ok(formatCheckpointList(message.sourceKey()));
            }
            if ("latest".equalsIgnoreCase(args)) {
                return GatewayReply.ok(
                        "已回滚到最近一次 checkpoint："
                                + checkpointService
                                        .rollbackLatest(message.sourceKey())
                                        .getCheckpointId());
            }
            try {
                int index = Integer.parseInt(args);
                List<CheckpointRecord> recent =
                        checkpointService.listRecent(message.sourceKey(), 10);
                if (index < 1 || index > recent.size()) {
                    return GatewayReply.error("checkpoint 序号无效，应在 1-" + recent.size() + " 之间。");
                }
                CheckpointRecord restored =
                        checkpointService.rollback(recent.get(index - 1).getCheckpointId());
                return GatewayReply.ok("已按列表序号回滚到 checkpoint：" + restored.getCheckpointId());
            } catch (NumberFormatException ignored) {
                // fall through
            }
            return GatewayReply.ok(
                    "已回滚到指定 checkpoint：" + checkpointService.rollback(args).getCheckpointId());
        }

        if (GatewayCommandConstants.COMMAND_PLATFORMS.equals(command)) {
            return GatewayReply.ok(
                    gatewayAuthorizationService.formatPlatformStatus(deliveryService.statuses()));
        }

        return GatewayReply.ok(helpText());
    }

    @Override
    public GatewayReply handle(
            GatewayMessage message, String commandLine, ConversationEventSink eventSink)
            throws Exception {
        if (eventSink == null) {
            eventSink = ConversationEventSink.noop();
        }

        String withoutSlash = commandLine.substring(1).trim();
        String[] parts = withoutSlash.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        if (GatewayCommandConstants.COMMAND_RETRY.equals(command)) {
            recordSlashCommand(message, command, args);
            SessionRecord session = requireSession(message.sourceKey());
            String lastUser = MessageSupport.getLastUserMessage(session.getNdjson());
            if (StrUtil.isBlank(lastUser)) {
                GatewayReply reply = GatewayReply.error("没有可重试的上一条用户消息。");
                emitDirectReply(reply, eventSink, session.getSessionId());
                return reply;
            }

            session.setNdjson(MessageSupport.removeLastTurn(session.getNdjson()));
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);

            GatewayMessage retryMessage =
                    new GatewayMessage(
                            message.getPlatform(),
                            message.getChatId(),
                            message.getUserId(),
                            lastUser);
            retryMessage.setThreadId(message.getThreadId());
            retryMessage.setChatType(message.getChatType());
            retryMessage.setChatName(message.getChatName());
            retryMessage.setUserName(message.getUserName());
            retryMessage.setSourceKeyOverride(message.sourceKey());
            return conversationOrchestrator.handleIncoming(retryMessage, eventSink);
        }

        GatewayReply reply = handle(message, commandLine);
        SessionRecord session = sessionRepository.getBoundSession(message.sourceKey());
        emitDirectReply(reply, eventSink, session == null ? null : session.getSessionId());
        return reply;
    }

    private GatewayReply handleStop(GatewayMessage message) throws Exception {
        AgentRunStopResult stopResult =
                agentRunControlService == null
                        ? AgentRunStopResult.none()
                        : agentRunControlService.stop(message.sourceKey());
        int stoppedProcesses = processRegistry.stopAll();

        StringBuilder buffer = new StringBuilder();
        if (stopResult.isActiveRun()) {
            buffer.append("已请求停止当前任务：").append(stopResult.getRunId());
        } else {
            buffer.append("当前聊天没有正在执行的任务。");
        }
        buffer.append("\n已停止后台进程：").append(stoppedProcesses).append(" 个。");

        SessionRecord session = sessionRepository.getBoundSession(message.sourceKey());
        GatewayReply reply = GatewayReply.ok(buffer.toString());
        if (session != null) {
            reply.setSessionId(session.getSessionId());
            reply.setBranchName(session.getBranchName());
        }
        return reply;
    }

    private void recordSlashCommand(GatewayMessage message, String command, String args) {
        if (agentRunRepository == null || message == null || !isTrackedSlash(command)) {
            return;
        }
        try {
            SessionRecord session = sessionRepository.getBoundSession(message.sourceKey());
            if (session == null) {
                return;
            }
            List<AgentRunRecord> runs = agentRunRepository.listBySession(session.getSessionId(), 1);
            if (runs.isEmpty()) {
                return;
            }
            AgentRunRecord run = runs.get(0);
            AgentRunEventRecord event = new AgentRunEventRecord();
            event.setEventId(IdSupport.newId());
            event.setRunId(run.getRunId());
            event.setSessionId(session.getSessionId());
            event.setSourceKey(message.sourceKey());
            event.setEventType("slash.command");
            event.setPhase(run.getPhase());
            event.setSeverity("info");
            event.setSummary("/" + command);
            event.setMetadataJson(
                    org.noear.snack4.ONode.serialize(
                            java.util.Collections.singletonMap("args", StrUtil.nullToEmpty(args))));
            event.setCreatedAt(System.currentTimeMillis());
            agentRunRepository.appendEvent(event);
        } catch (Exception ignored) {
        }
    }

    private boolean isTrackedSlash(String command) {
        return GatewayCommandConstants.COMMAND_NEW.equals(command)
                || GatewayCommandConstants.COMMAND_RETRY.equals(command)
                || GatewayCommandConstants.COMMAND_UNDO.equals(command)
                || GatewayCommandConstants.COMMAND_BRANCH.equals(command)
                || GatewayCommandConstants.COMMAND_RESUME.equals(command)
                || GatewayCommandConstants.COMMAND_STOP.equals(command)
                || GatewayCommandConstants.COMMAND_COMPRESS.equals(command)
                || GatewayCommandConstants.COMMAND_ROLLBACK.equals(command);
    }

    /** 处理工具开关命令。 */
    private GatewayReply handleTools(GatewayMessage message, String args) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0
                || StrUtil.isBlank(parts[0])
                || GatewayCommandConstants.ACTION_LIST.equalsIgnoreCase(parts[0])) {
            return GatewayReply.ok("工具列表：" + toolRegistry.listToolNames());
        }

        List<String> names = new ArrayList<String>();
        for (int i = 1; i < parts.length; i++) {
            if (StrUtil.isNotBlank(parts[i])) {
                names.add(parts[i]);
            }
        }

        if (GatewayCommandConstants.ACTION_ENABLE.equalsIgnoreCase(parts[0])) {
            toolRegistry.enableTools(message.sourceKey(), names);
            return GatewayReply.ok("已启用工具：" + names);
        }

        if (GatewayCommandConstants.ACTION_DISABLE.equalsIgnoreCase(parts[0])) {
            toolRegistry.disableTools(message.sourceKey(), names);
            return GatewayReply.ok("已禁用工具：" + names);
        }

        return GatewayReply.error(
                "用法：" + GatewayCommandConstants.SLASH_TOOLS + " [list|enable|disable] [name...]");
    }

    /** 处理技能命令。 */
    private GatewayReply handleSkills(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        String action =
                parts.length == 0 || StrUtil.isBlank(parts[0])
                        ? GatewayCommandConstants.ACTION_LIST
                        : parts[0];
        String target = parts.length > 1 ? parts[1].trim() : "";

        if (GatewayCommandConstants.ACTION_LIST.equalsIgnoreCase(action)) {
            return GatewayReply.ok("技能列表：" + localSkillService.listSkillNames());
        }
        if (GatewayCommandConstants.ACTION_BROWSE.equalsIgnoreCase(action)) {
            return GatewayReply.ok(
                    formatBrowse(
                            skillHubService.browse(
                                    parseOption(target, "--source", "all"),
                                    parseIntOption(target, "--page", 1),
                                    parseIntOption(target, "--size", 20))));
        }
        if (GatewayCommandConstants.ACTION_SEARCH.equalsIgnoreCase(action)) {
            String query = stripOptions(target, "--source", "--limit");
            return GatewayReply.ok(
                    formatSearch(
                            skillHubService.search(
                                    query,
                                    parseOption(target, "--source", "all"),
                                    parseIntOption(target, "--limit", 10))));
        }
        if (GatewayCommandConstants.ACTION_INSTALL.equalsIgnoreCase(action)) {
            if (StrUtil.isBlank(target)) {
                return GatewayReply.error(
                        "用法："
                                + GatewayCommandConstants.SLASH_SKILLS
                                + " install <identifier> [--category <name>] [--force]");
            }
            String identifier = firstToken(target);
            String category = parseOption(target, "--category", null);
            boolean force = hasFlag(target, "--force");
            HubInstallRecord record = skillHubService.install(identifier, category, force);
            return GatewayReply.ok(
                    "已安装技能：" + record.getInstallPath() + " (" + record.getSource() + ")");
        }
        if (GatewayCommandConstants.ACTION_CHECK.equalsIgnoreCase(action)) {
            return GatewayReply.ok(
                    formatHubInstallRecords(
                            skillHubService.check(StrUtil.blankToDefault(target, null))));
        }
        if (GatewayCommandConstants.ACTION_UPDATE.equalsIgnoreCase(action)) {
            return GatewayReply.ok(
                    formatHubInstallRecords(
                            skillHubService.update(
                                    stripOptions(target, "--force"), hasFlag(target, "--force"))));
        }
        if (GatewayCommandConstants.ACTION_AUDIT.equalsIgnoreCase(action)) {
            return GatewayReply.ok(
                    formatAudit(skillHubService.audit(StrUtil.blankToDefault(target, null))));
        }
        if (GatewayCommandConstants.ACTION_UNINSTALL.equalsIgnoreCase(action)) {
            if (StrUtil.isBlank(target)) {
                return GatewayReply.error(
                        "用法：" + GatewayCommandConstants.SLASH_SKILLS + " uninstall <name>");
            }
            return GatewayReply.ok(skillHubService.uninstall(firstToken(target)));
        }
        if (GatewayCommandConstants.ACTION_TAP.equalsIgnoreCase(action)) {
            return GatewayReply.ok(handleTap(target));
        }
        if (GatewayCommandConstants.ACTION_ENABLE.equalsIgnoreCase(action)) {
            localSkillService.enable(message.sourceKey(), target);
            return GatewayReply.ok("已启用技能：" + target);
        }
        if (GatewayCommandConstants.ACTION_DISABLE.equalsIgnoreCase(action)) {
            localSkillService.disable(message.sourceKey(), target);
            return GatewayReply.ok("已禁用技能：" + target);
        }
        if (GatewayCommandConstants.ACTION_INSPECT.equalsIgnoreCase(action)) {
            return GatewayReply.ok(localSkillService.inspect(target));
        }
        if (GatewayCommandConstants.ACTION_RELOAD.equalsIgnoreCase(action)) {
            return GatewayReply.ok("已从 runtime 目录重新加载本地技能。");
        }

        return GatewayReply.error(
                "用法："
                        + GatewayCommandConstants.SLASH_SKILLS
                        + " [list|browse|search|install|inspect|check|update|audit|uninstall|tap|enable|disable|reload] ...");
    }

    /** 处理人格命令。 */
    private GatewayReply handlePersonality(String args) throws Exception {
        Map<String, AppConfig.PersonalityConfig> personalities =
                appConfig.getAgent().getPersonalities();
        if (personalities == null || personalities.isEmpty()) {
            return GatewayReply.error("当前没有可用的人格配置。");
        }
        if (StrUtil.isBlank(args)) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("可用人格：\n");
            buffer.append("- none: 清除人格覆盖\n");
            for (Map.Entry<String, AppConfig.PersonalityConfig> entry : personalities.entrySet()) {
                String description =
                        entry.getValue() == null
                                ? ""
                                : StrUtil.blankToDefault(entry.getValue().getDescription(), "无描述");
                buffer.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(description)
                        .append('\n');
            }
            buffer.append("当前激活：").append(currentPersonalityName());
            return GatewayReply.ok(buffer.toString().trim());
        }

        if ("none".equalsIgnoreCase(args)
                || "default".equalsIgnoreCase(args)
                || "neutral".equalsIgnoreCase(args)) {
            globalSettingRepository.remove(AgentSettingConstants.ACTIVE_PERSONALITY);
            return GatewayReply.ok("已清除人格覆盖，下一条消息恢复默认行为。");
        }

        String matchedName = null;
        for (String name : personalities.keySet()) {
            if (name.equalsIgnoreCase(args)) {
                matchedName = name;
                break;
            }
        }
        if (matchedName == null) {
            return GatewayReply.error("未知人格：" + args);
        }
        globalSettingRepository.set(AgentSettingConstants.ACTIVE_PERSONALITY, matchedName);
        return GatewayReply.ok("已切换人格为：" + matchedName + "，将从下一条消息开始生效。");
    }

    /** 处理定时任务命令。 */
    private GatewayReply handleCron(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+", 2);
        String action =
                parts.length == 0 || StrUtil.isBlank(parts[0])
                        ? GatewayCommandConstants.ACTION_LIST
                        : parts[0];
        String tail = parts.length > 1 ? parts[1] : "";

        if (GatewayCommandConstants.ACTION_LIST.equalsIgnoreCase(action)) {
            List<CronJobRecord> jobs = cronJobRepository.listBySource(message.sourceKey());
            StringBuilder buffer = new StringBuilder();
            for (CronJobRecord job : jobs) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(job.getJobId())
                        .append(" ")
                        .append(job.getName())
                        .append(" ")
                        .append(job.getStatus());
            }
            return GatewayReply.ok(buffer.length() == 0 ? "当前没有定时任务。" : buffer.toString());
        }

        if (GatewayCommandConstants.ACTION_CREATE.equalsIgnoreCase(action)) {
            String[] fields = tail.split("\\|", 3);
            if (fields.length < 3) {
                return GatewayReply.error(
                        "用法："
                                + GatewayCommandConstants.SLASH_CRON
                                + " create <name>|<cron>|<prompt>");
            }

            long now = System.currentTimeMillis();
            String[] sourceParts = SourceKeySupport.split(message.sourceKey());
            CronJobRecord job = new CronJobRecord();
            job.setJobId(IdSupport.newId());
            job.setName(fields[0].trim());
            job.setCronExpr(fields[1].trim());
            job.setPrompt(fields[2].trim());
            job.setSourceKey(message.sourceKey());
            job.setDeliverPlatform(sourceParts[0]);
            job.setDeliverChatId(sourceParts[1]);
            job.setStatus("ACTIVE");
            job.setNextRunAt(CronSupport.nextRunAt(job.getCronExpr(), now));
            job.setCreatedAt(now);
            job.setUpdatedAt(now);
            cronJobRepository.save(job);
            return GatewayReply.ok("已创建定时任务：" + job.getJobId());
        }

        if (GatewayCommandConstants.ACTION_PAUSE.equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(tail, "PAUSED");
            return GatewayReply.ok("已暂停定时任务：" + tail);
        }

        if (GatewayCommandConstants.ACTION_RESUME.equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(tail, "ACTIVE");
            return GatewayReply.ok("已恢复定时任务：" + tail);
        }

        if (GatewayCommandConstants.ACTION_DELETE.equalsIgnoreCase(action)) {
            cronJobRepository.delete(tail);
            return GatewayReply.ok("已删除定时任务：" + tail);
        }

        if (GatewayCommandConstants.ACTION_RUN.equalsIgnoreCase(action)) {
            CronJobRecord job = cronJobRepository.findById(tail);
            if (job == null) {
                return GatewayReply.error("未找到定时任务：" + tail);
            }

            GatewayMessage synthetic =
                    new GatewayMessage(
                            message.getPlatform(),
                            message.getChatId(),
                            message.getUserId(),
                            job.getPrompt());
            synthetic.setChatType(message.getChatType());
            synthetic.setChatName(message.getChatName());
            synthetic.setUserName(message.getUserName());
            GatewayReply reply = conversationOrchestrator.runScheduled(synthetic);
            deliveryService.deliver(
                    SourceKeySupport.toDeliveryRequest(job.getSourceKey(), reply.getContent()));
            return GatewayReply.ok("已执行定时任务：" + tail);
        }

        return GatewayReply.error(
                "用法："
                        + GatewayCommandConstants.SLASH_CRON
                        + " [list|create|pause|resume|delete|run]");
    }

    /** 处理 pairing 相关命令。 */
    private GatewayReply handlePairing(GatewayMessage message, String args) throws Exception {
        String[] parts = args.split("\\s+");
        if (parts.length == 0 || StrUtil.isBlank(parts[0])) {
            return GatewayReply.error(
                    "用法："
                            + GatewayCommandConstants.SLASH_PAIRING
                            + " [claim-admin|pending|approve|revoke|approved] ...");
        }
        String action = parts[0].trim().toLowerCase();

        if (GatewayCommandConstants.ACTION_CLAIM_ADMIN.equals(action)) {
            return gatewayAuthorizationService.claimAdmin(message);
        }

        PlatformType targetPlatform = message.getPlatform();
        if (parts.length >= 2) {
            targetPlatform = PlatformType.fromName(parts[1]);
        }

        if (GatewayCommandConstants.ACTION_PENDING.equals(action)) {
            return gatewayAuthorizationService.pairingPending(message, targetPlatform);
        }
        if (GatewayCommandConstants.ACTION_APPROVED.equals(action)) {
            return gatewayAuthorizationService.pairingApproved(message, targetPlatform);
        }
        if (GatewayCommandConstants.ACTION_APPROVE.equals(action)) {
            if (parts.length < 3) {
                return GatewayReply.error(
                        "用法："
                                + GatewayCommandConstants.SLASH_PAIRING
                                + " approve <platform> <code>");
            }
            return gatewayAuthorizationService.pairingApprove(message, targetPlatform, parts[2]);
        }
        if (GatewayCommandConstants.ACTION_REVOKE.equals(action)) {
            if (parts.length < 3) {
                return GatewayReply.error(
                        "用法："
                                + GatewayCommandConstants.SLASH_PAIRING
                                + " revoke <platform> <userId>");
            }
            return gatewayAuthorizationService.pairingRevoke(message, targetPlatform, parts[2]);
        }

        return GatewayReply.error(
                "用法："
                        + GatewayCommandConstants.SLASH_PAIRING
                        + " [claim-admin|pending|approve|revoke|approved] ...");
    }

    private GatewayReply handleDangerousApprove(GatewayMessage message, String args)
            throws Exception {
        SessionRecord session = sessionRepository.getBoundSession(message.sourceKey());
        if (session == null) {
            return GatewayReply.error("当前没有绑定会话，也没有待审批的危险命令。请先触发需要审批的工具调用。");
        }

        SqliteAgentSession agentSession = new SqliteAgentSession(session, sessionRepository);
        String normalizedArgs = StrUtil.nullToEmpty(args).trim().toLowerCase();
        if ("list".equals(normalizedArgs)) {
            return GatewayReply.ok(formatApprovalList(agentSession));
        }
        if (normalizedArgs.startsWith("clear")) {
            return clearApprovals(agentSession, normalizedArgs);
        }

        DangerousCommandApprovalService.PendingApproval pending =
                dangerousCommandApprovalService.getPendingApproval(agentSession);
        if (pending == null) {
            return GatewayReply.error("当前没有待审批的危险命令。若刚刚收到审批提示，请重试原始请求；也可以使用 /approve list 查看审批状态。");
        }

        DangerousCommandApprovalService.ApprovalScope scope = parseApprovalScope(args);
        if (!dangerousCommandApprovalService.approve(agentSession, scope, message.getUserName())) {
            return GatewayReply.error("危险命令审批状态已失效，请重试原始请求。");
        }
        return conversationOrchestrator.resumePending(message.sourceKey());
    }

    private String formatApprovalList(SqliteAgentSession agentSession) {
        DangerousCommandApprovalService.PendingApproval pending =
                dangerousCommandApprovalService.getPendingApproval(agentSession);
        StringBuilder buffer = new StringBuilder();
        buffer.append("pending=")
                .append(pending == null ? "none" : pending.approvalKey())
                .append('\n');
        buffer.append("session_approvals=")
                .append(dangerousCommandApprovalService.listSessionApprovals(agentSession))
                .append('\n');
        buffer.append("always_approvals=")
                .append(dangerousCommandApprovalService.listAlwaysApprovals());
        return buffer.toString();
    }

    private GatewayReply clearApprovals(SqliteAgentSession agentSession, String normalizedArgs)
            throws Exception {
        String[] parts = normalizedArgs.split("\\s+", 3);
        String scope = parts.length >= 2 ? parts[1] : "session";
        if ("session".equals(scope)) {
            dangerousCommandApprovalService.clearSessionApprovals(agentSession);
            return GatewayReply.ok("cleared session approvals");
        }
        if ("always".equals(scope)) {
            dangerousCommandApprovalService.clearAlwaysApprovals();
            return GatewayReply.ok("cleared always approvals");
        }
        if ("all".equals(scope)) {
            dangerousCommandApprovalService.clearSessionApprovals(agentSession);
            dangerousCommandApprovalService.clearAlwaysApprovals();
            return GatewayReply.ok("cleared all approvals");
        }
        return GatewayReply.error("用法：/approve clear session|always|all");
    }

    private GatewayReply handleDangerousDeny(GatewayMessage message) throws Exception {
        SessionRecord session = sessionRepository.getBoundSession(message.sourceKey());
        if (session == null) {
            return GatewayReply.error("当前没有待审批的危险命令。");
        }

        SqliteAgentSession agentSession = new SqliteAgentSession(session, sessionRepository);
        DangerousCommandApprovalService.PendingApproval pending =
                dangerousCommandApprovalService.getPendingApproval(agentSession);
        if (pending == null) {
            return GatewayReply.error("当前没有待审批的危险命令。");
        }

        if (!dangerousCommandApprovalService.reject(agentSession, message.getUserName())) {
            return GatewayReply.error("危险命令审批状态已失效，请重试。");
        }
        return conversationOrchestrator.resumePending(message.sourceKey());
    }

    private GatewayReply handleReasoning(GatewayMessage message, String args) throws Exception {
        String normalized = StrUtil.nullToEmpty(args).trim().toLowerCase();
        if (normalized.length() == 0) {
            return GatewayReply.ok(
                    "reasoning_display="
                            + displaySettingsService.describeReasoning(
                                    message.sourceKey(), message.getPlatform())
                            + "\nreasoning_effort="
                            + StrUtil.blankToDefault(
                                    appConfig.getLlm().getReasoningEffort(), "default")
                            + "\nusage="
                            + GatewayCommandConstants.SLASH_REASONING
                            + " [show|hide]");
        }
        if ("show".equals(normalized) || "on".equals(normalized)) {
            displaySettingsService.setReasoningVisible(message.sourceKey(), true);
            return GatewayReply.ok("已开启当前来源键的 reasoning 展示。");
        }
        if ("hide".equals(normalized) || "off".equals(normalized)) {
            displaySettingsService.setReasoningVisible(message.sourceKey(), false);
            return GatewayReply.ok("已关闭当前来源键的 reasoning 展示。");
        }
        return GatewayReply.error("用法：" + GatewayCommandConstants.SLASH_REASONING + " [show|hide]");
    }

    private DangerousCommandApprovalService.ApprovalScope parseApprovalScope(String args) {
        String normalized = StrUtil.nullToEmpty(args).trim().toLowerCase();
        if ("always".equals(normalized)
                || "permanent".equals(normalized)
                || "permanently".equals(normalized)) {
            return DangerousCommandApprovalService.ApprovalScope.ALWAYS;
        }
        if ("session".equals(normalized) || "ses".equals(normalized)) {
            return DangerousCommandApprovalService.ApprovalScope.SESSION;
        }
        return DangerousCommandApprovalService.ApprovalScope.ONCE;
    }

    /** 获取当前来源键的会话；若不存在则立即创建。 */
    private SessionRecord requireSession(String sourceKey) throws Exception {
        SessionRecord session = sessionRepository.getBoundSession(sourceKey);
        if (session == null) {
            session = sessionRepository.bindNewSession(sourceKey);
        }
        return session;
    }

    private void emitDirectReply(
            GatewayReply reply, ConversationEventSink eventSink, String fallbackSessionId) {
        if (eventSink == null || eventSink == ConversationEventSink.noop() || reply == null) {
            return;
        }

        String sessionId = StrUtil.blankToDefault(reply.getSessionId(), fallbackSessionId);
        if (reply.isError()) {
            eventSink.onRunFailed(sessionId, new IllegalStateException(reply.getContent()));
            return;
        }

        if (StrUtil.isNotBlank(reply.getContent())) {
            eventSink.onAssistantDelta(reply.getContent());
        }
        eventSink.onRunCompleted(sessionId, "", null);
    }

    private String formatCheckpointList(String sourceKey) throws Exception {
        List<CheckpointRecord> checkpoints = checkpointService.listRecent(sourceKey, 10);
        if (checkpoints.isEmpty()) {
            return "当前来源键没有可回滚的 checkpoint。";
        }
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < checkpoints.size(); i++) {
            CheckpointRecord record = checkpoints.get(i);
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(i + 1)
                    .append(". ")
                    .append(record.getCheckpointId())
                    .append(" created=")
                    .append(DateUtil.formatDateTime(new java.util.Date(record.getCreatedAt())))
                    .append(", restored=")
                    .append(
                            record.getRestoredAt() > 0
                                    ? DateUtil.formatDateTime(
                                            new java.util.Date(record.getRestoredAt()))
                                    : "never");
            if (StrUtil.isNotBlank(record.getSessionId())) {
                buffer.append(", session=").append(record.getSessionId());
            }
        }
        return buffer.toString();
    }

    private String currentPersonalityName() {
        try {
            String value = globalSettingRepository.get(AgentSettingConstants.ACTIVE_PERSONALITY);
            return StrUtil.blankToDefault(value, "default");
        } catch (Exception e) {
            return "default";
        }
    }

    private ModelCommandInput parseModelCommand(String args) {
        String[] tokens = args.trim().split("\\s+");
        ModelCommandInput result = new ModelCommandInput();
        StringBuilder remainder = new StringBuilder();
        for (String token : tokens) {
            if ("--global".equalsIgnoreCase(token)) {
                result.global = true;
                continue;
            }
            if (remainder.length() > 0) {
                remainder.append(' ');
            }
            remainder.append(token);
        }
        String spec = remainder.toString().trim();
        if ("clear".equalsIgnoreCase(spec)
                || "default".equalsIgnoreCase(spec)
                || "none".equalsIgnoreCase(spec)) {
            result.clear = true;
            return result;
        }
        if (spec.contains(":")) {
            String[] parts = spec.split(":", 2);
            result.provider = parts[0].trim();
            result.model = parts[1].trim();
        } else {
            result.model = spec;
        }
        return result;
    }

    private static class ModelCommandInput {
        private boolean global;
        private boolean clear;
        private String provider;
        private String model;
    }

    private String handleTap(String target) throws Exception {
        String action = firstToken(target);
        if (StrUtil.isBlank(action)
                || GatewayCommandConstants.ACTION_LIST.equalsIgnoreCase(action)) {
            List<TapRecord> taps = skillHubService.listTaps();
            if (taps.isEmpty()) {
                return "当前没有自定义 taps。";
            }
            StringBuilder buffer = new StringBuilder();
            for (TapRecord tap : taps) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(tap.getRepo())
                        .append(" path=")
                        .append(StrUtil.blankToDefault(tap.getPath(), ""));
            }
            return buffer.toString();
        }
        if (GatewayCommandConstants.ACTION_ADD.equalsIgnoreCase(action)) {
            String[] parts = target.split("\\s+");
            if (parts.length < 2) {
                throw new IllegalStateException("用法：/skills tap add <owner/repo> [path]");
            }
            return skillHubService.addTap(parts[1], parts.length > 2 ? parts[2] : null);
        }
        if (GatewayCommandConstants.ACTION_REMOVE.equalsIgnoreCase(action)
                || GatewayCommandConstants.ACTION_DELETE.equalsIgnoreCase(action)) {
            String[] parts = target.split("\\s+");
            if (parts.length < 2) {
                throw new IllegalStateException("用法：/skills tap remove <owner/repo>");
            }
            return skillHubService.removeTap(parts[1]);
        }
        throw new IllegalStateException("Unsupported tap action: " + action);
    }

    private String formatBrowse(SkillBrowseResult result) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("skills hub browse page ")
                .append(result.getPage())
                .append("/")
                .append(
                        Math.max(
                                1,
                                (result.getTotal() + result.getPageSize() - 1)
                                        / result.getPageSize()))
                .append('\n');
        for (SkillMeta item : result.getItems()) {
            buffer.append("- ")
                    .append(item.getName())
                    .append(" [")
                    .append(item.getSource())
                    .append("/")
                    .append(item.getTrustLevel())
                    .append("]: ")
                    .append(item.getDescription())
                    .append('\n');
        }
        return buffer.toString().trim();
    }

    private String formatSearch(SkillBrowseResult result) {
        StringBuilder buffer = new StringBuilder();
        for (SkillMeta item : result.getItems()) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append("- ")
                    .append(item.getName())
                    .append(" [")
                    .append(item.getSource())
                    .append("/")
                    .append(item.getTrustLevel())
                    .append("]")
                    .append(" -> ")
                    .append(item.getIdentifier());
        }
        return buffer.length() == 0 ? "未找到匹配技能。" : buffer.toString();
    }

    private String formatHubInstallRecords(List<HubInstallRecord> records) {
        if (records == null || records.isEmpty()) {
            return "没有技能变更。";
        }
        StringBuilder buffer = new StringBuilder();
        for (HubInstallRecord record : records) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append("- ")
                    .append(record.getName())
                    .append(" [")
                    .append(record.getSource())
                    .append("/")
                    .append(record.getTrustLevel())
                    .append("]")
                    .append(" path=")
                    .append(record.getInstallPath());
            Object status = record.getMetadata().get("status");
            if (status != null) {
                buffer.append(" status=").append(status);
            }
        }
        return buffer.toString();
    }

    private String formatAudit(List<ScanResult> results) {
        if (results == null || results.isEmpty()) {
            return "没有可审计的 hub 技能。";
        }
        StringBuilder buffer = new StringBuilder();
        for (ScanResult result : results) {
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(result.getSkillName())
                    .append(" -> ")
                    .append(result.getVerdict())
                    .append('\n');
            buffer.append(result.getSummary());
        }
        return buffer.toString();
    }

    private boolean hasFlag(String raw, String flag) {
        return (" " + StrUtil.nullToEmpty(raw) + " ").contains(" " + flag + " ");
    }

    private String parseOption(String raw, String option, String defaultValue) {
        String[] parts = StrUtil.nullToEmpty(raw).split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if (option.equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return defaultValue;
    }

    private int parseIntOption(String raw, String option, int defaultValue) {
        try {
            return Integer.parseInt(parseOption(raw, option, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String stripOptions(String raw, String... optionNames) {
        String[] parts = StrUtil.nullToEmpty(raw).split("\\s+");
        List<String> kept = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            boolean skip = false;
            for (String optionName : optionNames) {
                if (optionName.equals(parts[i])) {
                    skip = true;
                    if (i + 1 < parts.length) {
                        i++;
                    }
                    break;
                }
            }
            if (!skip && i < parts.length && StrUtil.isNotBlank(parts[i])) {
                kept.add(parts[i]);
            }
        }
        return String.join(" ", kept).trim();
    }

    private String firstToken(String raw) {
        String[] parts = StrUtil.nullToEmpty(raw).trim().split("\\s+", 2);
        return parts.length == 0 ? "" : parts[0];
    }

    /** 生成帮助文本。 */
    private String helpText() {
        return String.join(
                "\n",
                Arrays.asList(
                        helpLine(GatewayCommandConstants.SLASH_NEW, "创建并切换到新会话"),
                        helpLine(GatewayCommandConstants.SLASH_RESET, "重置当前会话并重新开始"),
                        helpLine(GatewayCommandConstants.SLASH_RETRY, "重新执行上一条用户消息"),
                        helpLine(GatewayCommandConstants.SLASH_UNDO, "撤销上一轮对话"),
                        helpLine(GatewayCommandConstants.SLASH_BRANCH + " [name]", "从当前会话创建分支"),
                        helpLine(
                                GatewayCommandConstants.SLASH_RESUME + " <session-or-branch>",
                                "恢复指定会话或分支"),
                        helpLine(GatewayCommandConstants.SLASH_STATUS, "查看当前会话状态"),
                        helpLine(GatewayCommandConstants.SLASH_USAGE, "查看当前会话运行信息"),
                        helpLine(GatewayCommandConstants.SLASH_STOP, "停止当前任务和后台进程"),
                        helpLine(
                                GatewayCommandConstants.SLASH_PERSONALITY + " [name|none]",
                                "查看或切换人格"),
                        helpLine(
                                GatewayCommandConstants.SLASH_VERSION + " [check|update]",
                                "查看版本或执行更新"),
                        helpLine(
                                GatewayCommandConstants.SLASH_MODEL
                                        + " [--global] [provider:]<model>|clear",
                                "查看或切换模型"),
                        helpLine(
                                GatewayCommandConstants.SLASH_REASONING + " [show|hide]",
                                "查看或切换 reasoning 展示"),
                        helpLine(
                                GatewayCommandConstants.SLASH_TOOLS
                                        + " [list|enable|disable] [name...]",
                                "查看或管理工具开关"),
                        helpLine(
                                GatewayCommandConstants.SLASH_SKILLS
                                        + " [list|browse|search|install|inspect|check|update|audit|uninstall|tap|enable|disable|reload]",
                                "管理本地技能与 Skills Hub"),
                        helpLine(
                                GatewayCommandConstants.SLASH_AGENT
                                        + " [name|list|create|show|model|tools|skills|memory]",
                                "切换或管理当前会话 Agent"),
                        helpLine(
                                GatewayCommandConstants.SLASH_CRON
                                        + " [list|create|pause|resume|delete|run]",
                                "管理定时任务"),
                        helpLine(GatewayCommandConstants.SLASH_COMPRESS + " [focus]", "压缩当前会话上下文"),
                        helpLine(
                                GatewayCommandConstants.SLASH_ROLLBACK
                                        + " [latest|checkpoint-id|number]",
                                "回滚到指定 checkpoint"),
                        helpLine(GatewayCommandConstants.SLASH_SETHOME, "将当前聊天设为 home channel"),
                        helpLine(
                                GatewayCommandConstants.SLASH_PAIRING
                                        + " [claim-admin|pending|approve|revoke|approved]",
                                "管理渠道配对与管理员授权"),
                        helpLine(
                                GatewayCommandConstants.SLASH_APPROVE + " [session|always]",
                                "批准当前危险命令"),
                        helpLine(GatewayCommandConstants.SLASH_DENY, "拒绝当前危险命令"),
                        helpLine(GatewayCommandConstants.SLASH_PLATFORMS, "查看平台连接与授权状态"),
                        helpLine(GatewayCommandConstants.SLASH_HELP, "显示帮助信息")));
    }

    private String helpLine(String usage, String description) {
        return usage + " - " + description;
    }

    private String formatUsage(SessionRecord session) {
        RuntimeSettingsService.ResolvedModel resolved =
                runtimeSettingsService.resolveEffectiveModel(session);
        StringBuilder buffer = new StringBuilder();
        buffer.append("session=").append(session.getSessionId()).append('\n');
        buffer.append("branch=").append(session.getBranchName()).append('\n');
        buffer.append("agent=")
                .append(StrUtil.blankToDefault(session.getActiveAgentName(), "default"))
                .append('\n');
        buffer.append("effective_provider=")
                .append(StrUtil.blankToDefault(resolved.getProvider(), "default"))
                .append('\n');
        buffer.append("effective_model=")
                .append(StrUtil.blankToDefault(resolved.getModel(), "default"))
                .append('\n');
        buffer.append("last_provider=")
                .append(StrUtil.blankToDefault(session.getLastResolvedProvider(), ""))
                .append('\n');
        buffer.append("last_model=")
                .append(StrUtil.blankToDefault(session.getLastResolvedModel(), ""))
                .append('\n');
        buffer.append("last_input_tokens=").append(session.getLastInputTokens()).append('\n');
        buffer.append("last_output_tokens=").append(session.getLastOutputTokens()).append('\n');
        buffer.append("last_reasoning_tokens=")
                .append(session.getLastReasoningTokens())
                .append('\n');
        buffer.append("last_cache_read_tokens=")
                .append(session.getLastCacheReadTokens())
                .append('\n');
        buffer.append("last_cache_write_tokens=")
                .append(session.getLastCacheWriteTokens())
                .append('\n');
        buffer.append("last_total_tokens=").append(session.getLastTotalTokens()).append('\n');
        buffer.append("cumulative_input_tokens=")
                .append(session.getCumulativeInputTokens())
                .append('\n');
        buffer.append("cumulative_output_tokens=")
                .append(session.getCumulativeOutputTokens())
                .append('\n');
        buffer.append("cumulative_reasoning_tokens=")
                .append(session.getCumulativeReasoningTokens())
                .append('\n');
        buffer.append("cumulative_cache_read_tokens=")
                .append(session.getCumulativeCacheReadTokens())
                .append('\n');
        buffer.append("cumulative_cache_write_tokens=")
                .append(session.getCumulativeCacheWriteTokens())
                .append('\n');
        buffer.append("cumulative_total_tokens=")
                .append(session.getCumulativeTotalTokens())
                .append('\n');
        buffer.append("last_usage_at=")
                .append(
                        session.getLastUsageAt() > 0
                                ? DateUtil.formatDateTime(
                                        new java.util.Date(session.getLastUsageAt()))
                                : "");
        return buffer.toString();
    }
}
