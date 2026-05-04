package com.jimuqu.solon.claw.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.constants.ContextFileConstants;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** runtime/ 根目录下人格工作区文件的统一访问服务。 */
public class PersonaWorkspaceService {
    private static final Logger log = LoggerFactory.getLogger(PersonaWorkspaceService.class);
    private static final String TEMPLATE_ROOT = "persona-templates/";

    private final File workspaceDir;
    private final File memoryDir;

    public PersonaWorkspaceService(AppConfig appConfig) {
        this.workspaceDir = FileUtil.file(appConfig.getRuntime().getHome());
        this.memoryDir = FileUtil.file(this.workspaceDir, ContextFileConstants.MEMORY_DIR);
        mkdirIfPossible(this.workspaceDir, "persona workspace");
        mkdirIfPossible(this.memoryDir, "persona memory");
        ensureSeeded();
    }

    /** 返回受控文件 key 顺序。 */
    public List<String> orderedKeys() {
        return ContextFileConstants.orderedKeys();
    }

    /** 解析 key 对应文件名。 */
    public String fileName(String key) {
        return ContextFileConstants.fileName(key);
    }

    /** 获取 key 对应文件。 */
    public File file(String key) {
        return FileUtil.file(workspaceDir, fileName(key));
    }

    /** 获取 key 对应文件绝对路径。 */
    public String absolutePath(String key) {
        return file(key).getAbsolutePath();
    }

    /** 判断文件是否存在。 */
    public boolean exists(String key) {
        return file(key).exists();
    }

    /** 读取文件内容；不存在时返回模板默认内容。 */
    public String read(String key) {
        File target = file(key);
        if (!target.exists()) {
            return loadTemplate(key);
        }
        try {
            return FileUtil.readUtf8String(target);
        } catch (IORuntimeException e) {
            log.warn(
                    "Unable to read persona workspace file {}; falling back to bundled template: {}",
                    target.getAbsolutePath(),
                    failureMessage(e));
            return loadTemplate(key);
        } catch (SecurityException e) {
            log.warn(
                    "Unable to read persona workspace file {}; falling back to bundled template: {}",
                    target.getAbsolutePath(),
                    failureMessage(e));
            return loadTemplate(key);
        }
    }

    /** 读取供系统提示词使用的正文内容。 */
    public String readPromptBody(String key) {
        return read(key);
    }

    /** 写入文件内容，不存在时自动创建。 */
    public void write(String key, String content) {
        writeContent(file(key), content);
    }

    /** 读取 key 对应模板内容。 */
    public String readTemplate(String key) {
        return loadTemplate(key);
    }

    /** 将文件恢复为模板默认内容。 */
    public void restoreTemplate(String key) {
        write(key, readTemplate(key));
    }

    /** 返回所有日记文件，相对路径按日期倒序排列。 */
    public List<String> listDiaryRelativePaths() {
        if (!memoryDir.exists()) {
            return Collections.emptyList();
        }

        List<File> files =
                FileUtil.loopFiles(
                        memoryDir, file -> file.isFile() && file.getName().endsWith(".md"));
        files.sort((left, right) -> right.getName().compareTo(left.getName()));

        List<String> result = new ArrayList<String>();
        for (File file : files) {
            result.add(ContextFileConstants.MEMORY_DIR + "/" + file.getName());
        }
        return result;
    }

    public String readDiary(String relativePath) {
        File target = diaryFile(relativePath);
        if (!target.exists()) {
            return "";
        }
        return FileUtil.readUtf8String(target);
    }

    public String absoluteDiaryPath(String relativePath) {
        return diaryFile(relativePath).getAbsolutePath();
    }

    private File diaryFile(String relativePath) {
        if (StrUtil.isBlank(relativePath) || relativePath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid diary path");
        }
        if (!listDiaryRelativePaths().contains(relativePath)) {
            throw new IllegalArgumentException("Diary file is not available: " + relativePath);
        }
        try {
            File target = FileUtil.file(workspaceDir, relativePath).getCanonicalFile();
            File root = memoryDir.getCanonicalFile();
            String targetPath = target.getAbsolutePath();
            String rootPath = root.getAbsolutePath();
            if (!targetPath.startsWith(rootPath + File.separator)) {
                throw new IllegalArgumentException("Diary file is outside memory directory");
            }
            return target;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("Invalid diary path", e);
        }
    }

    /** 启动时补齐缺失的人格工作区文件。 */
    private void ensureSeeded() {
        for (String key : orderedKeys()) {
            File target = file(key);
            if (target.exists()) {
                continue;
            }
            try {
                writeContent(target, loadTemplate(key));
            } catch (IORuntimeException e) {
                log.warn(
                        "Unable to seed persona workspace file {}: {}. Startup continues; fix runtime directory permissions before editing workspace files.",
                        target.getAbsolutePath(),
                        failureMessage(e));
            } catch (SecurityException e) {
                log.warn(
                        "Unable to seed persona workspace file {}: {}. Startup continues; fix runtime directory permissions before editing workspace files.",
                        target.getAbsolutePath(),
                        failureMessage(e));
            }
        }
    }

    private void mkdirIfPossible(File dir, String label) {
        try {
            FileUtil.mkdir(dir);
        } catch (IORuntimeException e) {
            log.warn(
                    "Unable to create {} directory {}: {}. Startup continues; file edits under this directory may fail.",
                    label,
                    dir.getAbsolutePath(),
                    failureMessage(e));
        } catch (SecurityException e) {
            log.warn(
                    "Unable to create {} directory {}: {}. Startup continues; file edits under this directory may fail.",
                    label,
                    dir.getAbsolutePath(),
                    failureMessage(e));
        }
    }

    private void writeContent(File target, String content) {
        File parent = target.getParentFile();
        if (parent != null) {
            FileUtil.mkdir(parent);
        }
        FileUtil.writeUtf8String(StrUtil.nullToEmpty(content), target);
    }

    private static String failureMessage(Throwable e) {
        if (e.getMessage() == null) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** 从类路径加载原始模板。 */
    private String loadTemplate(String key) {
        String normalized = ContextFileConstants.normalizeKey(key);
        if (ContextFileConstants.KEY_MEMORY.equals(normalized)) {
            return "";
        }
        if (ContextFileConstants.KEY_MEMORY_TODAY.equals(ContextFileConstants.normalizeKey(key))) {
            return buildTodayMemoryTemplate(LocalDate.now());
        }
        String resource = TEMPLATE_ROOT + fileName(key);
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing persona template resource: " + resource);
        }
        try {
            return IoUtil.read(stream, StandardCharsets.UTF_8);
        } finally {
            IoUtil.close(stream);
        }
    }

    private String buildTodayMemoryTemplate(LocalDate date) {
        return "# " + date.toString() + "\n\n";
    }
}
