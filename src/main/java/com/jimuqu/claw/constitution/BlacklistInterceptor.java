package com.jimuqu.claw.constitution;

import com.jimuqu.claw.config.props.BlacklistProperties;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.agent.react.intercept.HITLInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 黑名单拦截器。
 *
 * <p>实现 {@link HITLInterceptor.InterventionStrategy}，
 * 在 bash 工具调用前检测命令是否命中黑名单。
 * 命中则直接返回拒绝消息，工具调用不会执行。
 *
 * <p>所有黑名单条目均来自 app.yml 配置，Java 代码不包含任何默认值。
 */
public class BlacklistInterceptor implements HITLInterceptor.InterventionStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(BlacklistInterceptor.class);

    /** 命令关键词黑名单（首个 token 匹配）。 */
    private final Set<String> commandBlacklist = new HashSet<>();
    /** 路径黑名单（命令中包含这些路径片段）。 */
    private final List<String> pathBlacklist = new ArrayList<>();
    /** 正则模式黑名单（整条命令匹配）。 */
    private final List<Pattern> patternBlacklist = new ArrayList<>();

    public BlacklistInterceptor(BlacklistProperties config) {
        if (config != null) {
            for (String cmd : config.getExtraCommands()) {
                if (cmd != null && !cmd.trim().isEmpty()) {
                    commandBlacklist.add(cmd.trim().toLowerCase());
                }
            }
            for (String path : config.getExtraPaths()) {
                if (path != null && !path.trim().isEmpty()) {
                    pathBlacklist.add(path.trim());
                }
            }
            for (String pattern : config.getExtraPatterns()) {
                if (pattern != null && !pattern.trim().isEmpty()) {
                    patternBlacklist.add(Pattern.compile(pattern));
                }
            }
        }

        LOG.info("Blacklist loaded: {} commands, {} paths, {} patterns",
                commandBlacklist.size(), pathBlacklist.size(), patternBlacklist.size());
    }

    @Override
    public String evaluate(ReActTrace trace, Map<String, Object> args) {
        String cmd = (String) args.get("command");
        if (cmd == null || cmd.trim().isEmpty()) return null;

        cmd = cmd.trim();
        String reason;

        // ① 命令关键词
        reason = checkCommand(cmd);
        if (reason != null) {
            LOG.warn("[黑名单] 命令被拦截: {} ({})", cmd, reason);
            return formatRejection(cmd, reason);
        }

        // ② 路径
        reason = checkPath(cmd);
        if (reason != null) {
            LOG.warn("[黑名单] 路径被拦截: {} ({})", cmd, reason);
            return formatRejection(cmd, reason);
        }

        // ③ 正则模式
        reason = checkPattern(cmd);
        if (reason != null) {
            LOG.warn("[黑名单] 模式被拦截: {} ({})", cmd, reason);
            return formatRejection(cmd, reason);
        }

        return null; // 放行
    }

    // ===== 检查方法 =====

    private String checkCommand(String cmd) {
        // 提取第一个 token
        String firstToken = cmd.split("\\s+")[0].toLowerCase();
        // 处理路径前缀，如 /usr/bin/sudo → sudo
        int lastSlash = firstToken.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < firstToken.length() - 1) {
            firstToken = firstToken.substring(lastSlash + 1);
        }
        int lastBackSlash = firstToken.lastIndexOf('\\');
        if (lastBackSlash >= 0 && lastBackSlash < firstToken.length() - 1) {
            firstToken = firstToken.substring(lastBackSlash + 1);
        }

        if (commandBlacklist.contains(firstToken)) {
            return "命令 \"" + firstToken + "\" 被禁止";
        }
        return null;
    }

    private String checkPath(String cmd) {
        String normalized = cmd.replace('\\', '/');
        for (String forbidden : pathBlacklist) {
            String normalizedForbidden = forbidden.replace('\\', '/');
            if (normalized.contains(normalizedForbidden)) {
                return "路径 \"" + forbidden + "\" 被禁止访问";
            }
        }
        return null;
    }

    private String checkPattern(String cmd) {
        for (Pattern p : patternBlacklist) {
            if (p.matcher(cmd).matches()) {
                return "命令匹配到危险模式";
            }
        }
        return null;
    }

    private String formatRejection(String cmd, String reason) {
        String summary = cmd.length() > 80 ? cmd.substring(0, 77) + "..." : cmd;
        return "[系统安全] 此命令被禁止执行: " + summary + "\n原因: " + reason;
    }
}
