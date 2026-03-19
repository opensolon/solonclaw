package com.jimuqu.claw.agent.tool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 提供受工作区边界保护的基础文件与命令工具。
 */
public class WorkspaceAgentTools {
    private static final int MAX_RESULT_CHARS = 8000;

    private final AgentWorkspaceService workspaceService;

    public WorkspaceAgentTools(AgentWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @ToolMapping(name = "read_file", description = "读取工作区内指定文件的文本内容")
    public String readFile(@Param(description = "工作区内的相对路径，或工作区内的绝对路径") String filePath) throws IOException {
        Path target = resolvePath(filePath, false);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return "文件不存在: " + target;
        }

        String content = FileUtil.readUtf8String(target.toFile());
        return truncate("文件内容如下:\n" + content);
    }

    @ToolMapping(name = "write_file", description = "写入工作区内文件；如目录不存在则自动创建；会覆盖原文件")
    public String writeFile(
            @Param(description = "工作区内的相对路径，或工作区内的绝对路径") String filePath,
            @Param(description = "要写入的完整文本内容") String content
    ) throws IOException {
        Path target = resolvePath(filePath, true);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        FileUtil.writeUtf8String(content == null ? "" : content, target.toFile());
        return "已写入文件: " + target;
    }

    @ToolMapping(name = "edit_file", description = "修改工作区内文件中的指定文本片段；仅当旧文本存在时才会替换")
    public String editFile(
            @Param(description = "工作区内的相对路径，或工作区内的绝对路径") String filePath,
            @Param(description = "需要被替换的原始文本") String oldText,
            @Param(description = "替换后的新文本") String newText
    ) throws IOException {
        Path target = resolvePath(filePath, false);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return "文件不存在: " + target;
        }

        String source = FileUtil.readUtf8String(target.toFile());
        if (oldText == null || oldText.isEmpty()) {
            return "修改失败: oldText 不能为空";
        }
        if (!source.contains(oldText)) {
            return "修改失败: 未找到需要替换的文本";
        }

        String result = source.replace(oldText, newText == null ? "" : newText);
        FileUtil.writeUtf8String(result, target.toFile());
        return "已修改文件: " + target;
    }

    @ToolMapping(name = "exec_command", description = "在工作区目录执行命令，返回标准输出与标准错误")
    public String execCommand(@Param(description = "要执行的命令文本") String command) throws Exception {
        if (StrUtil.isBlank(command)) {
            return "执行失败: command 不能为空";
        }

        ProcessBuilder builder = new ProcessBuilder(buildShellCommand(command));
        builder.directory(workspaceService.getWorkspaceDir());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessOutput(process));

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return "执行超时(30s): " + command;
        }

        String output = outputFuture.get(5, TimeUnit.SECONDS);
        String body = StrUtil.isBlank(output) ? "(无输出)" : output.trim();
        return truncate("exitCode=" + process.exitValue() + "\n" + body);
    }

    private String[] buildShellCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new String[]{"powershell", "-NoProfile", "-Command", command};
        }

        return new String[]{"/bin/sh", "-lc", command};
    }

    private String readProcessOutput(Process process) {
        try (InputStream inputStream = process.getInputStream()) {
            return IoUtil.read(inputStream, "UTF-8");
        } catch (IOException e) {
            return "读取命令输出失败: " + e.getMessage();
        }
    }

    private Path resolvePath(String pathText, boolean allowMissingLeaf) throws IOException {
        if (StrUtil.isBlank(pathText)) {
            throw new IllegalArgumentException("filePath 不能为空");
        }

        Path workspaceRoot = workspaceService.getWorkspaceDir().toPath().toRealPath();
        Path candidate = Paths.get(pathText.trim());
        if (!candidate.isAbsolute()) {
            candidate = workspaceRoot.resolve(pathText.trim());
        }

        candidate = candidate.normalize();
        Path absolute = candidate.toAbsolutePath().normalize();
        if (!absolute.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("路径超出工作区范围: " + pathText);
        }

        if (allowMissingLeaf) {
            Path parent = absolute.getParent();
            if (parent != null && !parent.toAbsolutePath().normalize().startsWith(workspaceRoot)) {
                throw new IllegalArgumentException("路径超出工作区范围: " + pathText);
            }
            return absolute;
        }

        return absolute.toRealPath();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_RESULT_CHARS) {
            return text;
        }

        return text.substring(0, MAX_RESULT_CHARS) + "\n...(输出已截断)";
    }
}
