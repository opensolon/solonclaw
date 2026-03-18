package com.jimuqu.claw.agent.workspace;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;

/**
 * 负责管理 Agent 工作区目录，以及将运行期相对路径统一解析到工作区下。
 */
public class AgentWorkspaceService {
    /** Agent 工作区根目录。 */
    private final File workspaceDir;

    /**
     * 创建工作区服务。
     *
     * @param workspacePath 工作区配置路径
     */
    public AgentWorkspaceService(String workspacePath) {
        this.workspaceDir = FileUtil.mkdir(new File(StrUtil.blankToDefault(workspacePath, "./workspace")));
    }

    /**
     * 返回工作区根目录。
     *
     * @return 工作区根目录
     */
    public File getWorkspaceDir() {
        return workspaceDir;
    }

    /**
     * 将配置路径解析为工作区下的实际目录。
     * 如果传入的是绝对路径，则原样返回。
     *
     * @param configuredPath 配置中的路径
     * @param defaultRelativePath 默认相对路径
     * @return 解析后的目录
     */
    public File resolveWithinWorkspace(String configuredPath, String defaultRelativePath) {
        String candidate = StrUtil.blankToDefault(configuredPath, defaultRelativePath).trim();
        File file = new File(candidate);
        if (file.isAbsolute()) {
            return FileUtil.mkdir(file);
        }

        String normalized = candidate.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return FileUtil.mkdir(new File(workspaceDir, normalized));
    }

    /**
     * 返回工作区下的某个文件。
     *
     * @param relativePath 相对工作区的路径
     * @return 文件对象
     */
    public File fileInWorkspace(String relativePath) {
        String normalized = StrUtil.blankToDefault(relativePath, "").replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return new File(workspaceDir, normalized);
    }
}
