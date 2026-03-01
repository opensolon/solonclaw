package com.jimuqu.solonclaw.tool.impl;

import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 技能安装工具
 * <p>
 * 允许 Agent 主动安装各种技能包和依赖
 *
 * @author SolonClaw
 */
@Component
public class SkillInstallTool {

    private static final Logger log = LoggerFactory.getLogger(SkillInstallTool.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5分钟超时

    @Inject
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    /**
     * 安装 Python 包
     *
     * @param packageName Python 包名称
     * @return 安装结果
     */
    @ToolMapping(description = "使用 pip 安装 Python 包。例如：requests, pandas, numpy 等")
    public String installPythonPackage(
            @Param(description = "Python 包名称（支持版本号，如 package==1.0.0）") String packageName
    ) {
        log.info("安装 Python 包: {}", packageName);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("pip3", "install", packageName);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "❌ 安装超时（超过 " + DEFAULT_TIMEOUT_SECONDS + " 秒）";
            }

            int exitCode = process.waitFor();
            String output = readOutput(process);

            if (exitCode == 0) {
                log.info("Python 包安装成功: {}", packageName);
                return "✅ Python 包安装成功: " + packageName + "\n\n输出:\n" + output;
            } else {
                log.warn("Python 包安装失败: {}, exitCode={}", packageName, exitCode);
                return "❌ Python 包安装失败: " + packageName + "\n\n错误:\n" + output;
            }

        } catch (Exception e) {
            log.error("安装 Python 包异常", e);
            return "❌ 安装 Python 包时出错: " + e.getMessage();
        }
    }

    /**
     * 安装 Node.js 包
     *
     * @param packageName NPM 包名称
     * @return 安装结果
     */
    @ToolMapping(description = "使用 npm 全局安装 Node.js 包。例如：@anthropic-ai/sdk, typescript 等")
    public String installNpmPackage(
            @Param(description = "NPM 包名称") String packageName
    ) {
        log.info("安装 NPM 包: {}", packageName);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("npm", "install", "-g", packageName);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "❌ 安装超时（超过 " + DEFAULT_TIMEOUT_SECONDS + " 秒）";
            }

            int exitCode = process.waitFor();
            String output = readOutput(process);

            if (exitCode == 0) {
                log.info("NPM 包安装成功: {}", packageName);
                return "✅ NPM 包安装成功: " + packageName + "\n\n输出:\n" + output;
            } else {
                log.warn("NPM 包安装失败: {}, exitCode={}", packageName, exitCode);
                return "❌ NPM 包安装失败: " + packageName + "\n\n错误:\n" + output;
            }

        } catch (Exception e) {
            log.error("安装 NPM 包异常", e);
            return "❌ 安装 NPM 包时出错: " + e.getMessage();
        }
    }

    /**
     * 从 GitHub 克隆项目
     *
     * @param repoUrl GitHub 仓库 URL
     * @param targetDir 目标目录（可选，默认为当前目录下的项目名）
     * @return 克隆结果
     */
    @ToolMapping(description = "从 GitHub 克隆代码仓库。可以用于下载开源项目、技能包等")
    public String cloneFromGitHub(
            @Param(description = "GitHub 仓库 URL（如：https://github.com/user/repo.git）") String repoUrl,
            @Param(description = "目标目录（可选，不填则自动使用仓库名）") String targetDir
    ) {
        log.info("从 GitHub 克隆: {}", repoUrl);

        try {
            // 如果没有指定目标目录，从 URL 中提取仓库名
            if (targetDir == null || targetDir.isEmpty()) {
                String[] parts = repoUrl.split("/");
                targetDir = parts[parts.length - 1].replace(".git", "");
            }

            ProcessBuilder pb = new ProcessBuilder();
            pb.command("git", "clone", repoUrl, targetDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "❌ 克隆超时（超过 " + DEFAULT_TIMEOUT_SECONDS + " 秒）";
            }

            int exitCode = process.waitFor();
            String output = readOutput(process);

            if (exitCode == 0) {
                log.info("GitHub 克隆成功: {}", repoUrl);
                return "✅ GitHub 克隆成功\n\n仓库: " + repoUrl + "\n目标目录: " + targetDir + "\n\n输出:\n" + output;
            } else {
                log.warn("GitHub 克隆失败: {}, exitCode={}", repoUrl, exitCode);
                return "❌ GitHub 克隆失败\n\n仓库: " + repoUrl + "\n错误:\n" + output;
            }

        } catch (Exception e) {
            log.error("GitHub 克隆异常", e);
            return "❌ GitHub 克隆时出错: " + e.getMessage();
        }
    }

    /**
     * 创建基于 JSON 配置的技能
     *
     * @param skillName 技能名称
     * @param description 技能描述
     * @param instruction 技能指令
     * @return 创建结果
     */
    @ToolMapping(description = "创建基于 JSON 配置的自定义技能，保存到 skills 目录")
    public String createJsonSkill(
            @Param(description = "技能名称（英文，如: order_expert）") String skillName,
            @Param(description = "技能描述") String description,
            @Param(description = "技能指令（告诉 AI 如何使用这个技能）") String instruction
    ) {
        log.info("创建 JSON 技能: {}", skillName);

        try {
            // 获取技能目录
            Path skillsDir = workspaceInfo.skillsDir();
            if (!Files.exists(skillsDir)) {
                Files.createDirectories(skillsDir);
            }

            // 创建技能配置
            String jsonContent = String.format("""
                    {
                      "name": "%s",
                      "description": "%s",
                      "instruction": "%s",
                      "enabled": true
                    }
                    """, skillName, description, instruction);

            // 保存到文件
            String fileName = skillName + ".json";
            Path filePath = skillsDir.resolve(fileName);
            Files.writeString(filePath, jsonContent, StandardCharsets.UTF_8);

            log.info("JSON 技能创建成功: {}", fileName);
            return String.format("✅ JSON 技能创建成功\n\n" +
                    "文件: %s\n" +
                    "名称: %s\n" +
                    "描述: %s\n" +
                    "指令: %s\n\n" +
                    "提示: 技能已保存到文件，需要重新加载才能生效。",
                    filePath, skillName, description, instruction);

        } catch (Exception e) {
            log.error("创建 JSON 技能异常", e);
            return "❌ 创建 JSON 技能时出错: " + e.getMessage();
        }
    }

    /**
     * 读取进程输出
     */
    private String readOutput(Process process) {
        try {
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            String line;
            int maxLength = 5000; // 限制输出长度
            int currentLength = 0;

            while ((line = reader.readLine()) != null) {
                if (currentLength + line.length() > maxLength) {
                    output.append("... (输出过长，已截断)");
                    break;
                }
                output.append(line).append("\n");
                currentLength += line.length() + 1;
            }

            return output.toString();

        } catch (Exception e) {
            log.error("读取进程输出失败", e);
            return "";
        }
    }
}
