package com.jimuqu.solonclaw.config;

import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工作目录配置
 * <p>
 * 统一管理 SolonClaw 的所有数据目录
 *
 * @author SolonClaw
 */
@Configuration
public class WorkspaceConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceConfig.class);

    @Inject("${nullclaw.workspace}")
    private String workspacePath;

    @Inject("${nullclaw.directories.mcpConfig}")
    private String mcpConfigFile;

    @Inject("${nullclaw.directories.skillsDir}")
    private String skillsDir;

    @Inject("${nullclaw.directories.jobsFile}")
    private String jobsFile;

    @Inject("${nullclaw.directories.jobHistoryFile}")
    private String jobHistoryFile;

    @Inject("${nullclaw.directories.database}")
    private String databaseFile;

    @Inject("${nullclaw.directories.shellWorkspace}")
    private String shellWorkspace;

    @Inject("${nullclaw.directories.logsDir}")
    private String logsDir;

    @Bean
    public WorkspaceInfo workspaceInfo() {
        Path workspace = Paths.get(workspacePath).toAbsolutePath().normalize();

        // 确保工作目录存在
        workspace.toFile().mkdirs();

        WorkspaceInfo info = new WorkspaceInfo(
                workspace,
                workspace.resolve(mcpConfigFile),
                workspace.resolve(skillsDir),
                workspace.resolve(jobsFile),
                workspace.resolve(jobHistoryFile),
                workspace.resolve(databaseFile),
                workspace.resolve(shellWorkspace),
                workspace.resolve(logsDir)
        );

        // 创建必要的子目录
        info.mkdirs();

        log.info("SolonClaw 工作目录: {}", workspace);
        log.info("  - MCP 配置: {}", info.mcpConfigFile());
        log.info("  - Skills 目录: {}", info.skillsDir());
        log.info("  - 数据库: {}", info.databaseFile());

        return info;
    }

    /**
     * 工作目录信息
     *
     * @param workspace       工作目录根路径
     * @param mcpConfigFile   MCP 配置文件
     * @param skillsDir       Skills 目录
     * @param jobsFile        任务配置文件
     * @param jobHistoryFile  任务历史文件
     * @param databaseFile    数据库文件
     * @param shellWorkspace  Shell 工作目录
     * @param logsDir         日志目录
     */
    public record WorkspaceInfo(
            Path workspace,
            Path mcpConfigFile,
            Path skillsDir,
            Path jobsFile,
            Path jobHistoryFile,
            Path databaseFile,
            Path shellWorkspace,
            Path logsDir
    ) {
        /**
         * 创建所有必要的目录
         */
        public void mkdirs() {
            workspace().toFile().mkdirs();
            skillsDir().toFile().mkdirs();
            shellWorkspace().toFile().mkdirs();
            logsDir().toFile().mkdirs();
        }
    }
}
