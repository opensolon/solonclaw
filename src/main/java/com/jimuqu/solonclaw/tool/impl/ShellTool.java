package com.jimuqu.solonclaw.tool.impl;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Shell 工具
 * <p>
 * 允许 AI 执行 Shell 命令，用于安装环境、访问接口、查询文件等
 *
 * @author SolonClaw
 */
@Component
public class ShellTool {

    private static final Logger log = LoggerFactory.getLogger(ShellTool.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024; // 1MB

    /**
     * 执行 Shell 命令
     *
     * @param command 要执行的 Shell 命令
     * @return 命令执行结果
     */
    @ToolMapping(description = "执行 Shell 命令，可用于安装环境、访问接口、查询文件")
    public String exec(
            @Param(description = "要执行的 Shell 命令") String command
    ) {
        log.info("执行 Shell 命令: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder();

            // 根据操作系统选择命令
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
            if (isWindows) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            // 设置工作目录
            pb.directory(new File("."));

            // 启动进程
            Process process = pb.start();

            // 读取输出
            String output = readOutput(process);
            String error = readError(process);

            // 等待进程完成
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "[超时] 命令执行超过 " + DEFAULT_TIMEOUT_SECONDS + " 秒";
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                log.warn("命令执行失败: exitCode={}, error={}", exitCode, error);
                return "[错误 " + exitCode + "] " + (error.isEmpty() ? "命令执行失败" : error);
            }

            // 限制输出大小
            if (output.length() > MAX_OUTPUT_BYTES) {
                output = output.substring(0, MAX_OUTPUT_BYTES) + "\n... (输出过长，已截断)";
            }

            log.info("命令执行成功: outputLength={}", output.length());
            return output.isEmpty() ? "(命令执行成功，无输出)" : output;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[中断] " + e.getMessage();
        } catch (Exception e) {
            log.error("命令执行异常", e);
            return "[异常] " + e.getMessage();
        }
    }

    /**
     * 读取标准输出
     */
    private String readOutput(Process process) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            log.error("读取输出异常", e);
        }
        return sb.toString();
    }

    /**
     * 读取错误输出
     */
    private String readError(Process process) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            log.error("读取错误输出异常", e);
        }
        return sb.toString();
    }
}
