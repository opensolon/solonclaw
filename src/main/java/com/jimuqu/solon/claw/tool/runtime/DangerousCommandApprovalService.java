package com.jimuqu.solon.claw.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.support.constants.AgentSettingConstants;
import com.jimuqu.solon.claw.support.constants.ToolNameConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.noear.snack4.ONode;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.agent.react.intercept.HITL;
import org.noear.solon.ai.agent.react.intercept.HITLInterceptor;
import org.noear.solon.flow.FlowContext;

/** 危险命令审批服务。 */
public class DangerousCommandApprovalService {
    public static final String DELIVERY_MODE_APPROVAL_CARD = "dangerous_command_approval_card";
    public static final String CARD_ACTION_KEY = "solonclaw_action";
    public static final String CARD_SCOPE_KEY = "scope";
    public static final String CARD_ACTION_APPROVE = "dangerous_approve";
    public static final String CARD_ACTION_DENY = "dangerous_deny";

    private static final String CONTEXT_PENDING_APPROVAL = "_dangerous_command_pending_";
    private static final String CONTEXT_SESSION_APPROVALS = "_dangerous_command_session_approvals_";

    private static final List<DangerRule> RULES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            new DangerRule(
                                    "delete_root",
                                    "delete in root path",
                                    pattern("\\brm\\s+(-[^\\s]*\\s+)*\\/"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "recursive_delete",
                                    "recursive delete",
                                    pattern("\\brm\\s+-[^\\s]*r"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "find_delete",
                                    "find -delete",
                                    pattern("\\bfind\\b.*-delete\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "xargs_rm",
                                    "xargs with rm",
                                    pattern("\\bxargs\\s+.*\\brm\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "world_writable",
                                    "world/other-writable permissions",
                                    pattern(
                                            "\\bchmod\\s+(-[^\\s]*\\s+)*(777|666|o\\+[rwx]*w|a\\+[rwx]*w)\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "chown_root",
                                    "recursive chown to root",
                                    pattern("\\bchown\\s+(-[^\\s]*)?R\\s+root"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "mkfs",
                                    "format filesystem",
                                    pattern("\\bmkfs\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "dd_disk",
                                    "disk copy",
                                    pattern("\\bdd\\s+.*if="),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "overwrite_etc",
                                    "overwrite system config",
                                    pattern("(>|tee\\b).*?/etc/"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "stop_service",
                                    "stop/restart system service",
                                    pattern(
                                            "\\bsystemctl\\s+(-[^\\s]+\\s+)*(stop|restart|disable|mask)\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "kill_all",
                                    "kill all processes",
                                    pattern("\\bkill\\s+-9\\s+-1\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "pkill_force",
                                    "force kill processes",
                                    pattern("\\bpkill\\s+-9\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "fork_bomb",
                                    "fork bomb",
                                    pattern(
                                            ":\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*\\}\\s*;\\s*:"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "curl_pipe_shell",
                                    "pipe remote content to shell",
                                    pattern("\\b(curl|wget)\\b.*\\|\\s*(ba)?sh\\b"),
                                    ToolNameConstants.EXECUTE_SHELL,
                                    ToolNameConstants.EXECUTE_PYTHON,
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "git_reset_hard",
                                    "git reset --hard (destroys uncommitted changes)",
                                    pattern("\\bgit\\s+reset\\s+--hard\\b"),
                                    ToolNameConstants.EXECUTE_SHELL,
                                    ToolNameConstants.EXECUTE_PYTHON,
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "git_force_push",
                                    "git force push (rewrites remote history)",
                                    pattern("\\bgit\\s+push\\b.*(--force|-f)\\b"),
                                    ToolNameConstants.EXECUTE_SHELL,
                                    ToolNameConstants.EXECUTE_PYTHON,
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "git_clean_force",
                                    "git clean with force (deletes untracked files)",
                                    pattern("\\bgit\\s+clean\\s+-[^\\s]*f"),
                                    ToolNameConstants.EXECUTE_SHELL,
                                    ToolNameConstants.EXECUTE_PYTHON,
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "sql_drop",
                                    "SQL DROP",
                                    pattern("\\bDROP\\s+(TABLE|DATABASE)\\b"),
                                    ToolNameConstants.EXECUTE_SHELL,
                                    ToolNameConstants.EXECUTE_PYTHON,
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "sql_delete_no_where",
                                    "SQL DELETE without WHERE",
                                    pattern("\\bDELETE\\s+FROM\\b(?!.*\\bWHERE\\b)"),
                                    ToolNameConstants.EXECUTE_SHELL,
                                    ToolNameConstants.EXECUTE_PYTHON,
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "sql_truncate",
                                    "SQL TRUNCATE",
                                    pattern("\\bTRUNCATE\\s+(TABLE)?\\s*\\w"),
                                    ToolNameConstants.EXECUTE_SHELL,
                                    ToolNameConstants.EXECUTE_PYTHON,
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "windows_remove_item",
                                    "PowerShell recursive delete",
                                    pattern("\\bRemove-Item\\b.*-Recurse\\b.*-Force\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "windows_del_force",
                                    "Windows force delete",
                                    pattern("\\bdel\\b.*\\s/[fq].*\\s/[fq]"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "windows_rmdir_force",
                                    "Windows recursive directory delete",
                                    pattern("\\b(rmdir|rd)\\b.*\\s/s\\b.*\\s/q\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "windows_format",
                                    "Windows format volume",
                                    pattern("\\bformat\\s+[a-z]:"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "windows_taskkill",
                                    "Windows force kill",
                                    pattern("\\btaskkill\\b.*\\s/f\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "windows_stop_process",
                                    "PowerShell force stop process",
                                    pattern("\\bStop-Process\\b.*-Force\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "windows_reg_delete",
                                    "Windows registry delete",
                                    pattern("\\breg\\s+delete\\b"),
                                    ToolNameConstants.EXECUTE_SHELL),
                            new DangerRule(
                                    "python_rmtree",
                                    "Python recursive delete",
                                    pattern("\\bshutil\\.rmtree\\s*\\("),
                                    ToolNameConstants.EXECUTE_PYTHON),
                            new DangerRule(
                                    "python_os_remove",
                                    "Python file delete",
                                    pattern("\\bos\\.(remove|unlink)\\s*\\("),
                                    ToolNameConstants.EXECUTE_PYTHON),
                            new DangerRule(
                                    "python_os_system",
                                    "Python shell execution",
                                    pattern("\\bos\\.system\\s*\\("),
                                    ToolNameConstants.EXECUTE_PYTHON),
                            new DangerRule(
                                    "python_subprocess",
                                    "Python subprocess execution",
                                    pattern(
                                            "\\bsubprocess\\.(run|Popen|call|check_call|check_output)\\s*\\("),
                                    ToolNameConstants.EXECUTE_PYTHON),
                            new DangerRule(
                                    "js_child_process",
                                    "Node child_process execution",
                                    pattern(
                                            "\\bchild_process\\.(exec|execSync|spawn|spawnSync|execFile|execFileSync)\\s*\\("),
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "js_require_child_process",
                                    "Node child_process import",
                                    pattern("require\\s*\\(\\s*['\"]child_process['\"]\\s*\\)"),
                                    ToolNameConstants.EXECUTE_JS),
                            new DangerRule(
                                    "js_fs_remove",
                                    "Node file delete",
                                    pattern("\\bfs\\.(rm|rmSync|unlink|unlinkSync)\\s*\\("),
                                    ToolNameConstants.EXECUTE_JS)));

    private final GlobalSettingRepository globalSettingRepository;

    public DangerousCommandApprovalService(GlobalSettingRepository globalSettingRepository) {
        this.globalSettingRepository = globalSettingRepository;
    }

    public HITLInterceptor buildInterceptor() {
        return new HITLInterceptor()
                .onTool(
                        ToolNameConstants.EXECUTE_SHELL,
                        (trace, args) -> evaluate(trace, ToolNameConstants.EXECUTE_SHELL, args))
                .onTool(
                        ToolNameConstants.EXECUTE_PYTHON,
                        (trace, args) -> evaluate(trace, ToolNameConstants.EXECUTE_PYTHON, args))
                .onTool(
                        ToolNameConstants.EXECUTE_JS,
                        (trace, args) -> evaluate(trace, ToolNameConstants.EXECUTE_JS, args));
    }

    public PendingApproval getPendingApproval(AgentSession session) {
        if (session == null) {
            return null;
        }
        return toPendingApproval(session.getContext().get(CONTEXT_PENDING_APPROVAL));
    }

    public PendingApproval getPendingApproval(
            com.jimuqu.solon.claw.core.model.SessionRecord sessionRecord) {
        if (sessionRecord == null || StrUtil.isBlank(sessionRecord.getAgentSnapshotJson())) {
            return null;
        }

        try {
            Object parsed = ONode.deserialize(sessionRecord.getAgentSnapshotJson(), Object.class);
            if (!(parsed instanceof Map)) {
                return null;
            }
            Map<?, ?> snapshot = (Map<?, ?>) parsed;
            Object pending = snapshot.get(CONTEXT_PENDING_APPROVAL);
            if (pending == null && snapshot.get("vars") instanceof Map) {
                pending = ((Map<?, ?>) snapshot.get("vars")).get(CONTEXT_PENDING_APPROVAL);
            }
            return toPendingApproval(pending);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean approve(AgentSession session, ApprovalScope scope, String approver)
            throws Exception {
        PendingApproval pending = getPendingApproval(session);
        if (pending == null) {
            return false;
        }

        if (scope == ApprovalScope.SESSION) {
            addSessionApproval(
                    session.getContext(), approvalPattern(pending.getToolName(), pending.getPatternKey()));
        } else if (scope == ApprovalScope.ALWAYS) {
            addAlwaysApproval(approvalPattern(pending.getToolName(), pending.getPatternKey()));
        }

        String comment = scope.comment();
        if (StrUtil.isNotBlank(approver)) {
            comment = comment + " 审批人：" + approver.trim();
        }

        HITL.approve(session, pending.getToolName(), comment);
        session.getContext().remove(CONTEXT_PENDING_APPROVAL);
        session.updateSnapshot();
        return true;
    }

    public void storePendingApproval(
            AgentSession session,
            String toolName,
            String patternKey,
            String description,
            String command) {
        if (session == null) {
            return;
        }

        DetectionResult detection = new DetectionResult();
        detection.setPatternKey(patternKey);
        detection.setDescription(description);
        detection.setNormalizedCode(normalize(command));
        session.getContext()
                .put(CONTEXT_PENDING_APPROVAL, createPendingMap(toolName, detection, command));
        session.updateSnapshot();
    }

    public boolean reject(AgentSession session, String approver) {
        PendingApproval pending = getPendingApproval(session);
        if (pending == null) {
            return false;
        }

        String comment = "危险命令未获批准，已取消执行。";
        if (StrUtil.isNotBlank(approver)) {
            comment = comment + " 审批人：" + approver.trim();
        }

        HITL.reject(session, pending.getToolName(), comment);
        session.getContext().remove(CONTEXT_PENDING_APPROVAL);
        session.updateSnapshot();
        return true;
    }

    public DetectionResult detect(String toolName, String code) {
        String normalized = normalize(code);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }

        for (DangerRule rule : RULES) {
            if (!rule.matches(toolName, normalized)) {
                continue;
            }

            DetectionResult result = new DetectionResult();
            result.setPatternKey(rule.getPatternKey());
            result.setDescription(rule.getDescription());
            result.setNormalizedCode(normalized);
            return result;
        }

        return null;
    }

    public Map<String, Object> buildDeliveryExtras(PlatformType platform, PendingApproval pending) {
        if (platform != PlatformType.FEISHU || pending == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> extras = new LinkedHashMap<String, Object>();
        extras.put("mode", DELIVERY_MODE_APPROVAL_CARD);
        extras.put("approvalCommand", pending.getCommand());
        extras.put("approvalDescription", pending.getDescription());
        extras.put("approvalToolName", pending.getToolName());
        return extras;
    }

    public boolean isSessionApproved(AgentSession session, String patternKey) {
        if (session == null || StrUtil.isBlank(patternKey)) {
            return false;
        }
        return containsPattern(loadSessionApprovals(session.getContext()), patternKey);
    }

    public boolean isSessionApproved(
            AgentSession session, String toolName, String patternKey, String command) {
        if (session == null || StrUtil.hasBlank(toolName, patternKey)) {
            return false;
        }
        Set<String> approvals = loadSessionApprovals(session.getContext());
        return approvals.contains(approvalPattern(toolName, patternKey))
                || approvals.contains(approvalKey(toolName, patternKey, normalize(command)));
    }

    public boolean isAlwaysApproved(String patternKey) {
        if (StrUtil.isBlank(patternKey)) {
            return false;
        }
        return containsPattern(loadAlwaysApprovedPatterns(), patternKey);
    }

    public boolean isAlwaysApproved(String toolName, String patternKey, String command) {
        if (StrUtil.hasBlank(toolName, patternKey)) {
            return false;
        }
        Set<String> approvals = loadAlwaysApprovedPatterns();
        return approvals.contains(approvalPattern(toolName, patternKey))
                || approvals.contains(approvalKey(toolName, patternKey, normalize(command)));
    }

    public List<String> listSessionApprovals(AgentSession session) {
        if (session == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(loadSessionApprovals(session.getContext()));
    }

    public List<String> listAlwaysApprovals() {
        return new ArrayList<String>(loadAlwaysApprovedPatterns());
    }

    public void clearSessionApprovals(AgentSession session) throws Exception {
        if (session == null) {
            return;
        }
        session.getContext().remove(CONTEXT_SESSION_APPROVALS);
        session.getContext().remove(CONTEXT_PENDING_APPROVAL);
        session.updateSnapshot();
    }

    public void clearAlwaysApprovals() throws Exception {
        if (globalSettingRepository != null) {
            globalSettingRepository.set(
                    AgentSettingConstants.DANGEROUS_COMMAND_ALWAYS_PATTERNS,
                    ONode.serialize(new ArrayList<String>()));
        }
    }

    public static String commandFromCardActionPayload(Object raw) {
        Map<?, ?> map = raw instanceof Map ? (Map<?, ?>) raw : parseStaticMap(raw);
        if (map == null) {
            return null;
        }

        String action = stringValueStatic(map.get(CARD_ACTION_KEY)).toLowerCase(Locale.ROOT);
        if (CARD_ACTION_DENY.equals(action)) {
            return "/deny";
        }
        if (!CARD_ACTION_APPROVE.equals(action)) {
            return null;
        }

        String scope = stringValueStatic(map.get(CARD_SCOPE_KEY)).toLowerCase(Locale.ROOT);
        if ("always".equals(scope)) {
            return "/approve always";
        }
        if ("session".equals(scope)) {
            return "/approve session";
        }
        return "/approve";
    }

    private String evaluate(ReActTrace trace, String toolName, Map<String, Object> args) {
        String code =
                args == null || args.get("code") == null ? null : String.valueOf(args.get("code"));
        DetectionResult detection = detect(toolName, code);
        if (detection == null) {
            persistTraceSnapshot(trace);
            return null;
        }

        String approvalKey =
                approvalKey(toolName, detection.getPatternKey(), detection.getNormalizedCode());
        PendingApproval pending = getPendingApproval(trace.getSession());
        if (trace.getContext().getAs(HITL.DECISION_PREFIX + toolName) != null) {
            if (pending != null && approvalKey.equals(pending.approvalKey())) {
                trace.getContext().remove(CONTEXT_PENDING_APPROVAL);
                persistTraceSnapshot(trace);
                return null;
            }
            trace.getContext().remove(HITL.DECISION_PREFIX + toolName);
            persistTraceSnapshot(trace);
        }

        if (isApproved(trace.getContext(), approvalKey)) {
            if (pending != null && approvalKey.equals(pending.approvalKey())) {
                trace.getContext().remove(CONTEXT_PENDING_APPROVAL);
            }
            persistTraceSnapshot(trace);
            return null;
        }

        trace.getContext()
                .put(CONTEXT_PENDING_APPROVAL, createPendingMap(toolName, detection, code));
        persistTraceSnapshot(trace);
        return buildPendingMessage(toolName, detection, code);
    }

    private void persistTraceSnapshot(ReActTrace trace) {
        if (trace != null && trace.getSession() != null) {
            trace.getSession().updateSnapshot();
        }
    }

    private Map<String, Object> createPendingMap(
            String toolName, DetectionResult detection, String code) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("toolName", toolName);
        payload.put("patternKey", detection.getPatternKey());
        payload.put("description", detection.getDescription());
        payload.put("command", StrUtil.nullToEmpty(code));
        payload.put("commandHash", commandHash(detection.getNormalizedCode()));
        payload.put(
                "approvalKey",
                approvalKey(toolName, detection.getPatternKey(), detection.getNormalizedCode()));
        payload.put("createdAt", System.currentTimeMillis());
        return payload;
    }

    private String buildPendingMessage(String toolName, DetectionResult detection, String code) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("⚠️ 危险命令需要审批：\n");
        buffer.append("工具：").append(toolLabel(toolName)).append('\n');
        buffer.append("原因：").append(detection.getDescription()).append("\n\n");
        buffer.append("```").append(codeFence(toolName)).append('\n');
        buffer.append(trimPreview(code));
        buffer.append("\n```\n\n");
        buffer.append(
                "回复 `/approve` 执行一次，`/approve session` 记住当前会话，`/approve always` 永久记住，或 `/deny` 取消。");
        return buffer.toString();
    }

    private boolean isApproved(FlowContext context, String approvalKey) {
        ApprovalKeyParts parts = parseApprovalKey(approvalKey);
        if (parts == null) {
            return loadSessionApprovals(context).contains(approvalKey)
                    || loadAlwaysApprovedPatterns().contains(approvalKey);
        }

        String approvalPattern = approvalPattern(parts.toolName, parts.patternKey);
        return loadSessionApprovals(context).contains(approvalPattern)
                || loadSessionApprovals(context).contains(approvalKey)
                || loadAlwaysApprovedPatterns().contains(approvalPattern)
                || loadAlwaysApprovedPatterns().contains(approvalKey);
    }

    private void addSessionApproval(FlowContext context, String patternKey) {
        Set<String> approvals = loadSessionApprovals(context);
        approvals.add(patternKey);
        context.put(CONTEXT_SESSION_APPROVALS, new ArrayList<String>(approvals));
    }

    private Set<String> loadSessionApprovals(FlowContext context) {
        return stringSetFrom(context == null ? null : context.get(CONTEXT_SESSION_APPROVALS));
    }

    private void addAlwaysApproval(String patternKey) throws Exception {
        Set<String> approvals = loadAlwaysApprovedPatterns();
        approvals.add(patternKey);
        globalSettingRepository.set(
                AgentSettingConstants.DANGEROUS_COMMAND_ALWAYS_PATTERNS,
                ONode.serialize(new ArrayList<String>(approvals)));
    }

    private Set<String> loadAlwaysApprovedPatterns() {
        if (globalSettingRepository == null) {
            return new LinkedHashSet<String>();
        }

        try {
            return stringSetFrom(
                    globalSettingRepository.get(
                            AgentSettingConstants.DANGEROUS_COMMAND_ALWAYS_PATTERNS));
        } catch (Exception ignored) {
            return new LinkedHashSet<String>();
        }
    }

    private Set<String> stringSetFrom(Object raw) {
        Set<String> values = new LinkedHashSet<String>();
        if (raw == null) {
            return values;
        }

        if (raw instanceof Collection) {
            for (Object item : (Collection<?>) raw) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    values.add(String.valueOf(item).trim());
                }
            }
            return values;
        }

        String text = String.valueOf(raw).trim();
        if (text.length() == 0) {
            return values;
        }
        if (text.startsWith("[") || text.startsWith("{")) {
            try {
                Object parsed = ONode.deserialize(text, Object.class);
                if (parsed instanceof Collection) {
                    for (Object item : (Collection<?>) parsed) {
                        if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                            values.add(String.valueOf(item).trim());
                        }
                    }
                    return values;
                }
            } catch (Exception ignored) {
                // fallback to plain string below
            }
        }

        values.add(text);
        return values;
    }

    private boolean containsPattern(Set<String> approvals, String patternKey) {
        if (approvals == null || StrUtil.isBlank(patternKey)) {
            return false;
        }
        String normalizedPattern = patternKey.trim();
        if (approvals.contains(normalizedPattern)) {
            return true;
        }
        for (String approval : approvals) {
            ApprovalKeyParts parts = parseApprovalKey(approval);
            if (parts != null && normalizedPattern.equals(parts.patternKey)) {
                return true;
            }
        }
        return false;
    }

    private PendingApproval toPendingApproval(Object raw) {
        if (raw == null) {
            return null;
        }

        Map<?, ?> map = raw instanceof Map ? (Map<?, ?>) raw : parseMap(String.valueOf(raw));
        if (map == null) {
            return null;
        }

        String toolName = stringValue(map.get("toolName"));
        String patternKey = stringValue(map.get("patternKey"));
        String description = stringValue(map.get("description"));
        String command = stringValue(map.get("command"));
        String commandHash = stringValue(map.get("commandHash"));
        String approvalKey = stringValue(map.get("approvalKey"));
        if (StrUtil.hasBlank(toolName, patternKey)) {
            return null;
        }

        PendingApproval pending = new PendingApproval();
        pending.setToolName(toolName);
        pending.setPatternKey(patternKey);
        pending.setDescription(description);
        pending.setCommand(command);
        pending.setCommandHash(commandHash);
        pending.setApprovalKey(approvalKey);
        return pending;
    }

    private Map<?, ?> parseMap(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            Object parsed = ONode.deserialize(text, Object.class);
            return parsed instanceof Map ? (Map<?, ?>) parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<?, ?> parseStaticMap(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map) {
            return (Map<?, ?>) raw;
        }
        String text = String.valueOf(raw).trim();
        if (text.length() == 0) {
            return null;
        }
        try {
            Object parsed = ONode.deserialize(text, Object.class);
            return parsed instanceof Map ? (Map<?, ?>) parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringValueStatic(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toolLabel(String toolName) {
        if (ToolNameConstants.EXECUTE_PYTHON.equals(toolName)) {
            return "execute_python";
        }
        if (ToolNameConstants.EXECUTE_JS.equals(toolName)) {
            return "execute_js";
        }
        return "execute_shell";
    }

    private String codeFence(String toolName) {
        if (ToolNameConstants.EXECUTE_PYTHON.equals(toolName)) {
            return "python";
        }
        if (ToolNameConstants.EXECUTE_JS.equals(toolName)) {
            return "javascript";
        }
        return "shell";
    }

    private String trimPreview(String code) {
        String normalized = StrUtil.nullToEmpty(code).trim();
        if (normalized.length() <= 400) {
            return normalized;
        }
        return normalized.substring(0, 400) + "\n...";
    }

    private String normalize(String code) {
        String normalized = StrUtil.nullToEmpty(code).replace("\u0000", "");
        normalized = normalized.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
        return normalized.trim();
    }

    private String approvalKey(String toolName, String patternKey, String normalizedCode) {
        return approvalPattern(toolName, patternKey)
                + ":"
                + commandHash(normalizedCode);
    }

    private String approvalPattern(String toolName, String patternKey) {
        return StrUtil.nullToEmpty(toolName).trim()
                + ":"
                + StrUtil.nullToEmpty(patternKey).trim();
    }

    private ApprovalKeyParts parseApprovalKey(String approvalKey) {
        if (StrUtil.isBlank(approvalKey)) {
            return null;
        }
        String[] parts = approvalKey.split(":", 3);
        if (parts.length < 2 || StrUtil.hasBlank(parts[0], parts[1])) {
            return null;
        }
        return new ApprovalKeyParts(parts[0], parts[1]);
    }

    private String commandHash(String normalizedCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash =
                    digest.digest(
                            StrUtil.nullToEmpty(normalizedCode).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                String hex = Integer.toHexString(value & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash dangerous command", e);
        }
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    public enum ApprovalScope {
        ONCE,
        SESSION,
        ALWAYS;

        public String comment() {
            if (this == SESSION) {
                return "批准执行，并记住当前会话中的危险命令模式。";
            }
            if (this == ALWAYS) {
                return "批准执行，并永久记住危险命令模式。";
            }
            return "批准执行本次危险命令。";
        }
    }

    public static class PendingApproval {
        private String toolName;
        private String patternKey;
        private String description;
        private String command;
        private String commandHash;
        private String approvalKey;

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getPatternKey() {
            return patternKey;
        }

        public void setPatternKey(String patternKey) {
            this.patternKey = patternKey;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getCommandHash() {
            return commandHash;
        }

        public void setCommandHash(String commandHash) {
            this.commandHash = commandHash;
        }

        public String getApprovalKey() {
            return approvalKey;
        }

        public void setApprovalKey(String approvalKey) {
            this.approvalKey = approvalKey;
        }

        public String approvalKey() {
            return StrUtil.blankToDefault(
                    approvalKey,
                    StrUtil.nullToEmpty(toolName)
                            + ":"
                            + StrUtil.nullToEmpty(patternKey)
                            + ":"
                            + StrUtil.nullToEmpty(commandHash));
        }
    }

    public static class DetectionResult {
        private String patternKey;
        private String description;
        private String normalizedCode;

        public String getPatternKey() {
            return patternKey;
        }

        public void setPatternKey(String patternKey) {
            this.patternKey = patternKey;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getNormalizedCode() {
            return normalizedCode;
        }

        public void setNormalizedCode(String normalizedCode) {
            this.normalizedCode = normalizedCode;
        }
    }

    private static class ApprovalKeyParts {
        private final String toolName;
        private final String patternKey;

        private ApprovalKeyParts(String toolName, String patternKey) {
            this.toolName = toolName;
            this.patternKey = patternKey;
        }
    }

    private static class DangerRule {
        private final String patternKey;
        private final String description;
        private final Pattern pattern;
        private final Set<String> tools;

        private DangerRule(
                String patternKey, String description, Pattern pattern, String... tools) {
            this.patternKey = patternKey;
            this.description = description;
            this.pattern = pattern;
            this.tools = new LinkedHashSet<String>(Arrays.asList(tools));
        }

        private boolean matches(String toolName, String code) {
            return tools.contains(toolName) && pattern.matcher(code).find();
        }

        private String getPatternKey() {
            return patternKey;
        }

        private String getDescription() {
            return description;
        }
    }
}
