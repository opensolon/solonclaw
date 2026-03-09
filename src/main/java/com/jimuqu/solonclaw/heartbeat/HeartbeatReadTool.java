package com.jimuqu.solonclaw.heartbeat;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.ai.annotation.ToolMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Heartbeat 读取工具
 * <p>
 * 用于 Agent 读取 HEARTBEAT.md 任务清单
 *
 * @author SolonClaw
 */
@Component
public class HeartbeatReadTool {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatReadTool.class);

    private static final String HEARTBEAT_FILENAME = "HEARTBEAT.md";

    @Inject
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    /**
     * 读取 HEARTBEAT.md 任务清单
     *
     * @return HEARTBEAT.md 的内容，如果文件不存在返回空字符串
     */
    @ToolMapping(description = "读取 HEARTBEAT.md 任务清单文件内容")
    public String readHeartbeat() {
        return readHeartbeatFile();
    }

    /**
     * 读取 HEARTBEAT.md 文件内容
     */
    public String readHeartbeatFile() {
        try {
            Path heartbeatFile = workspaceInfo.workspace().resolve(HEARTBEAT_FILENAME);
            if (!Files.exists(heartbeatFile)) {
                log.debug("HEARTBEAT.md 文件不存在: {}", heartbeatFile);
                return "";
            }

            String content = Files.readString(heartbeatFile);
            log.debug("读取 HEARTBEAT.md 成功，长度: {}", content.length());
            return content;
        } catch (IOException e) {
            log.error("读取 HEARTBEAT.md 失败", e);
            return "";
        }
    }

    /**
     * 检查 HEARTBEAT.md 是否为空
     *
     * @return true 如果文件为空或只包含空白字符
     */
    public boolean isHeartbeatFileEmpty() {
        String content = readHeartbeatFile();
        return isContentEmpty(content);
    }

    /**
     * 检查内容是否为空
     */
    public boolean isContentEmpty(String content) {
        return StrUtil.isBlank(content);
    }

    /**
     * 获取 HEARTBEAT.md 文件路径
     */
    public Path getHeartbeatFilePath() {
        return workspaceInfo.workspace().resolve(HEARTBEAT_FILENAME);
    }
}
