package com.jimuqu.solonclaw.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 引导服务
 * <p>
 * 负责加载和管理 Bootstrap 引导文件
 * 支持从工作区加载引导文件和从 classpath 加载模板
 *
 * @author SolonClaw
 */
@Component
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    /**
     * 引导文件名列表
     */
    public static final List<String> BOOTSTRAP_FILES = List.of(
            "AGENTS.md",
            "SOUL.md",
            "USER.md",
            "IDENTITY.md",
            "BOOTSTRAP.md"
    );

    @Inject(required = false)
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    @Inject
    private BootstrapConfig config;

    /**
     * 加载工作区根目录下的引导文件
     *
     * @param workspaceDir 工作区根目录
     * @return 引导文件列表
     */
    public List<BootstrapFile> loadBootstrapFiles(String workspaceDir) {
        List<BootstrapFile> files = new ArrayList<>();

        if (StrUtil.isBlank(workspaceDir)) {
            log.warn("工作区目录为空，无法加载引导文件");
            return files;
        }

        Path workspacePath = Path.of(workspaceDir);

        for (String fileName : BOOTSTRAP_FILES) {
            BootstrapFile bootstrapFile = loadBootstrapFile(workspacePath, fileName);
            files.add(bootstrapFile);

            if (!bootstrapFile.missing()) {
                log.debug("加载引导文件: {} - {} 字符", fileName, bootstrapFile.content().length());
            }
        }

        log.info("引导文件加载完成: {} / {} 个文件存在",
                files.stream().filter(f -> !f.missing()).count(),
                files.size());

        return files;
    }

    /**
     * 加载单个引导文件
     */
    private BootstrapFile loadBootstrapFile(Path workspacePath, String fileName) {
        Path filePath = workspacePath.resolve(fileName);

        if (!Files.exists(filePath)) {
            return BootstrapFile.missing(fileName, filePath.toString());
        }

        try {
            String content = Files.readString(filePath);
            return BootstrapFile.of(fileName, filePath.toString(), content);
        } catch (IOException e) {
            log.warn("读取引导文件失败: {}", fileName, e);
            return BootstrapFile.missing(fileName, filePath.toString());
        }
    }

    /**
     * 从 classpath 加载模板
     *
     * @param name 模板名称（不含扩展名）
     * @return 模板内容，如果不存在返回空
     */
    public Optional<String> loadTemplate(String name) {
        // 添加 .md 扩展名
        String templateName = name.endsWith(".md") ? name : name + ".md";

        // 尝试从 templates 目录加载
        String[] paths = {
                "templates/" + templateName,
                "templates/bootstrap/" + templateName,
                templateName
        };

        for (String path : paths) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    String content = new String(is.readAllBytes());
                    log.debug("加载模板: {} -> {}", path, content.length() + " 字符");
                    return Optional.of(content);
                }
            } catch (IOException e) {
                log.warn("加载模板失败: {}", path, e);
            }
        }

        log.debug("模板不存在: {}", name);
        return Optional.empty();
    }

    /**
     * 首次运行时复制模板文件到工作区
     *
     * @param workspaceDir 工作区目录
     */
    public void seedBootstrapFiles(String workspaceDir) {
        if (!config.isEnabled() || !config.isAutoSeed()) {
            log.info("Bootstrap 自动初始化已禁用");
            return;
        }

        if (StrUtil.isBlank(workspaceDir)) {
            log.warn("工作区目录为空，无法初始化引导文件");
            return;
        }

        Path workspacePath = Path.of(workspaceDir);

        // 检查是否需要初始化（只要 BOOTSTRAP.md 不存在就初始化）
        Path bootstrapFile = workspacePath.resolve("BOOTSTRAP.md");
        if (Files.exists(bootstrapFile)) {
            log.info("引导文件已存在，跳过初始化");
            return;
        }

        log.info("开始初始化引导文件到: {}", workspaceDir);

        // 从模板复制引导文件
        for (String fileName : BOOTSTRAP_FILES) {
            seedTemplate(workspacePath, fileName);
        }
    }

    /**
     * 复制单个模板到工作区
     */
    private void seedTemplate(Path workspacePath, String fileName) {
        // 移除 .md 扩展名获取模板名
        String templateName = fileName.replace(".md", "").toLowerCase();

        // 尝试加载模板
        Optional<String> templateContent = loadTemplate(templateName);

        if (templateContent.isEmpty()) {
            log.warn("模板不存在，跳过: {}", templateName);
            return;
        }

        Path targetPath = workspacePath.resolve(fileName);

        // 如果文件已存在，跳过
        if (Files.exists(targetPath)) {
            log.debug("文件已存在，跳过: {}", fileName);
            return;
        }

        try {
            Files.writeString(targetPath, templateContent.get());
            log.info("已创建引导文件: {}", fileName);
        } catch (IOException e) {
            log.error("创建引导文件失败: {}", fileName, e);
        }
    }

    /**
     * 检查引导是否完成
     * <p>
     * 如果 IDENTITY.md 和 USER.md 都存在，则认为引导已完成
     *
     * @param workspaceDir 工作区目录
     * @return true 如果引导已完成
     */
    public boolean checkOnboardingComplete(String workspaceDir) {
        if (StrUtil.isBlank(workspaceDir)) {
            return false;
        }

        Path workspacePath = Path.of(workspaceDir);
        Path identityFile = workspacePath.resolve("IDENTITY.md");
        Path userFile = workspacePath.resolve("USER.md");

        return Files.exists(identityFile) && Files.exists(userFile);
    }

    /**
     * 检查是否存在 BOOTSTRAP.md
     *
     * @param workspaceDir 工作区目录
     * @return true 如果 BOOTSTRAP.md 存在
     */
    public boolean hasBootstrap(String workspaceDir) {
        if (StrUtil.isBlank(workspaceDir)) {
            return false;
        }

        Path workspacePath = Path.of(workspaceDir);
        Path bootstrapFile = workspacePath.resolve("BOOTSTRAP.md");

        return Files.exists(bootstrapFile);
    }

    /**
     * 获取工作区根目录
     */
    public String getWorkspaceDir() {
        if (workspaceInfo == null) {
            return null;
        }
        return workspaceInfo.workspace().toString();
    }

    /**
     * 加载引导文件并构建系统提示词片段
     * <p>
     * 按照特定顺序加载：SOUL.md -> AGENTS.md -> IDENTITY.md -> USER.md
     *
     * @param workspaceDir 工作区目录
     * @return 构建好的提示词片段
     */
    public String buildBootstrapPrompt(String workspaceDir) {
        List<BootstrapFile> files = loadBootstrapFiles(workspaceDir);

        if (files.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();

        // 按特定顺序处理文件
        Map<String, BootstrapFile> fileMap = new LinkedHashMap<>();
        for (BootstrapFile file : files) {
            fileMap.put(file.name(), file);
        }

        // SOUL.md - 最重要，定义人格
        if (fileMap.containsKey("SOUL.md") && !fileMap.get("SOUL.md").missing()) {
            String soul = fileMap.get("SOUL.md").content();
            prompt.append("\n## 灵魂与人格\n");
            prompt.append(soul);
            prompt.append("\n");
        }

        // AGENTS.md - Agent 定义
        if (fileMap.containsKey("AGENTS.md") && !fileMap.get("AGENTS.md").missing()) {
            String agents = fileMap.get("AGENTS.md").content();
            prompt.append("\n## Agent 定义\n");
            prompt.append(agents);
            prompt.append("\n");
        }

        // IDENTITY.md - Agent 身份
        if (fileMap.containsKey("IDENTITY.md") && !fileMap.get("IDENTITY.md").missing()) {
            String identity = fileMap.get("IDENTITY.md").content();
            prompt.append("\n## 身份信息\n");
            prompt.append(identity);
            prompt.append("\n");
        }

        // USER.md - 用户信息
        if (fileMap.containsKey("USER.md") && !fileMap.get("USER.md").missing()) {
            String user = fileMap.get("USER.md").content();
            prompt.append("\n## 用户信息\n");
            prompt.append(user);
            prompt.append("\n");
        }

        return prompt.toString();
    }
}
