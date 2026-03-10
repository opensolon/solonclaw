package com.jimuqu.solonclaw.agent.subagent;

import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作空间上下文
 * <p>
 * 管理子 Agent 的工作空间继承
 * 参考 OpenClaw 的 resolveSpawnedWorkspaceInheritance 实现
 *
 * @author SolonClaw
 */
@Component
public class WorkspaceContext {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceContext.class);

    /**
     * 默认工作空间根目录
     */
    private static final String DEFAULT_WORKSPACE_ROOT = "./workspace";

    /**
     * 会话到工作空间的映射
     */
    private final Map<String, String> sessionToWorkspace = new ConcurrentHashMap<>();

    /**
     * 子 Agent 到父 Agent 的工作空间映射
     */
    private final Map<String, String> childToParentWorkspace = new ConcurrentHashMap<>();

    /**
     * 获取或创建会话的工作空间目录
     *
     * @param sessionKey 会话键
     * @return 工作空间目录路径
     */
    public String getWorkspaceForSession(String sessionKey) {
        return sessionToWorkspace.computeIfAbsent(sessionKey, key -> {
            // 创建基于会话键的唯一工作空间目录
            String safeSessionKey = sanitizeSessionKey(key);
            Path workspacePath = Paths.get(DEFAULT_WORKSPACE_ROOT, safeSessionKey);

            // 确保目录存在
            try {
                java.nio.file.Files.createDirectories(workspacePath);
                log.info("创建工作空间: sessionKey={}, path={}", key, workspacePath);
            } catch (Exception e) {
                log.error("创建工作空间失败: sessionKey={}, path={}", key, workspacePath, e);
            }

            return workspacePath.toString();
        });
    }

    /**
     * 继承父 Agent 的工作空间
     * <p>
     * 子 Agent 共享父 Agent 的工作目录，可以访问父 Agent 的文件
     *
     * @param childSessionKey 子会话键
     * @param parentSessionKey 父会话键
     * @return 工作空间目录路径
     */
    public String inheritWorkspace(String childSessionKey, String parentSessionKey) {
        String parentWorkspace = getWorkspaceForSession(parentSessionKey);

        // 子 Agent 使用父 Agent 的工作空间
        sessionToWorkspace.put(childSessionKey, parentWorkspace);
        childToParentWorkspace.put(childSessionKey, parentSessionKey);

        log.info("继承工作空间: childSessionKey={}, parentSessionKey={}, workspace={}",
                childSessionKey, parentSessionKey, parentWorkspace);

        return parentWorkspace;
    }

    /**
     * 获取子 Agent 的工作空间（可能是继承的）
     *
     * @param childSessionKey 子会话键
     * @return 工作空间目录路径
     */
    public String getWorkspaceForChild(String childSessionKey) {
        return sessionToWorkspace.get(childSessionKey);
    }

    /**
     * 获取父 Agent 的会话键
     *
     * @param childSessionKey 子会话键
     * @return 父会话键，如果没有继承则返回 null
     */
    public String getParentSessionKey(String childSessionKey) {
        return childToParentWorkspace.get(childSessionKey);
    }

    /**
     * 检查是否是继承的工作空间
     *
     * @param childSessionKey 子会话键
     * @return true 如果是继承的
     */
    public boolean isInheritedWorkspace(String childSessionKey) {
        return childToParentWorkspace.containsKey(childSessionKey);
    }

    /**
     * 清理会话的工作空间
     *
     * @param sessionKey 会话键
     * @param deleteFiles 是否删除文件
     */
    public void clearWorkspace(String sessionKey, boolean deleteFiles) {
        String workspacePath = sessionToWorkspace.get(sessionKey);
        if (workspacePath != null) {
            if (deleteFiles) {
                try {
                    java.nio.file.Files.deleteIfExists(Paths.get(workspacePath));
                    log.info("删除工作空间: sessionKey={}, path={}", sessionKey, workspacePath);
                } catch (Exception e) {
                    log.error("删除工作空间失败: sessionKey={}, path={}", sessionKey, workspacePath, e);
                }
            }

            sessionToWorkspace.remove(sessionKey);
            childToParentWorkspace.remove(sessionKey);
        }
    }

    /**
     * 清除子 Agent 的工作空间继承关系
     * <p>
     * 只清除继承关系，不删除文件（因为父 Agent 还在使用）
     *
     * @param childSessionKey 子会话键
     */
    public void clearInheritance(String childSessionKey) {
        childToParentWorkspace.remove(childSessionKey);
        log.debug("清除继承关系: childSessionKey={}", childSessionKey);
    }

    /**
     * 获取所有使用指定工作空间的会话
     *
     * @param workspacePath 工作空间路径
     * @return 会话键列表
     */
    public java.util.List<String> getSessionsUsingWorkspace(String workspacePath) {
        return sessionToWorkspace.entrySet().stream()
                .filter(entry -> workspacePath.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 统计工作空间使用情况
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        long totalSessions = sessionToWorkspace.size();
        long inheritedWorkspaces = childToParentWorkspace.size();
        long uniqueWorkspaces = sessionToWorkspace.values().stream().distinct().count();

        stats.put("totalSessions", totalSessions);
        stats.put("inheritedWorkspaces", inheritedWorkspaces);
        stats.put("uniqueWorkspaces", uniqueWorkspaces);

        return stats;
    }

    /**
     * 清理所有工作空间
     *
     * @param deleteFiles 是否删除文件
     */
    public void clearAll(boolean deleteFiles) {
        if (deleteFiles) {
            // 清理所有工作空间（只清理根目录下独特的，避免重复删除继承的）
            sessionToWorkspace.values().stream()
                    .distinct()
                    .forEach(workspacePath -> {
                        try {
                            java.nio.file.Files.deleteIfExists(Paths.get(workspacePath));
                        } catch (Exception e) {
                            log.error("删除工作空间失败: path={}", workspacePath, e);
                        }
                    });
        }

        sessionToWorkspace.clear();
        childToParentWorkspace.clear();

        log.info("清理所有工作空间: deleteFiles={}", deleteFiles);
    }

    /**
     * 清理会话键中的特殊字符
     *
     * @param sessionKey 原始会话键
     * @return 安全的会话键
     */
    private String sanitizeSessionKey(String sessionKey) {
        return sessionKey.replaceAll("[^a-zA-Z0-9-]", "_");
    }
}