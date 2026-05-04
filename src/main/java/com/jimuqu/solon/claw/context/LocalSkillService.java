package com.jimuqu.solon.claw.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentRuntimePolicy;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SkillDescriptor;
import com.jimuqu.solon.claw.core.model.SkillView;
import com.jimuqu.solon.claw.core.service.SkillCatalogService;
import com.jimuqu.solon.claw.core.service.SkillImportService;
import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.SkillSetupState;
import com.jimuqu.solon.claw.skillhub.support.SkillFrontmatterSupport;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import com.jimuqu.solon.claw.storage.repository.SqlitePreferenceStore;
import com.jimuqu.solon.claw.support.constants.SkillConstants;
import com.jimuqu.solon.claw.support.constants.ToolNameConstants;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本地技能目录服务，支持 Hermes 风格分类目录与渐进披露读取。 */
public class LocalSkillService implements SkillCatalogService {
    /** 技能名允许字符。 */
    private static final String VALID_NAME_PATTERN = "^[a-z0-9][a-z0-9._-]*$";

    /** 应用配置。 */
    private final AppConfig appConfig;

    /** 技能可见性偏好存储。 */
    private final SqlitePreferenceStore preferenceStore;

    /** 自动导入服务。 */
    private final SkillImportService skillImportService;

    /** Hub 状态存储。 */
    private final SkillHubStateStore hubStateStore;

    /** 构造本地技能服务。 */
    public LocalSkillService(AppConfig appConfig, SqlitePreferenceStore preferenceStore) {
        this(appConfig, preferenceStore, null, null);
    }

    public LocalSkillService(
            AppConfig appConfig,
            SqlitePreferenceStore preferenceStore,
            SkillImportService skillImportService,
            SkillHubStateStore hubStateStore) {
        this.appConfig = appConfig;
        this.preferenceStore = preferenceStore;
        this.skillImportService = skillImportService;
        this.hubStateStore =
                hubStateStore == null
                        ? new SkillHubStateStore(
                                FileUtil.file(appConfig.getRuntime().getSkillsDir()))
                        : hubStateStore;
        FileUtil.mkdir(appConfig.getRuntime().getSkillsDir());
    }

    public List<String> listSkillNames() {
        try {
            processPendingImportsQuietly();
            List<SkillDescriptor> skills = listSkills(null);
            List<String> names = new ArrayList<String>();
            for (SkillDescriptor descriptor : skills) {
                names.add(descriptor.canonicalName());
            }
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public String inspect(String skillName) {
        try {
            processPendingImportsQuietly();
            SkillView skillView = viewSkill(skillName, null);
            return skillView.getContent();
        } catch (Exception e) {
            return "Skill not found: " + skillName;
        }
    }

    /** 将技能显式设为可见。 */
    public void enable(String sourceKey, String skillName) throws Exception {
        setVisible(sourceKey, skillName, true);
    }

    /** 将技能显式设为隐藏。 */
    public void disable(String sourceKey, String skillName) throws Exception {
        setVisible(sourceKey, skillName, false);
    }

    /** 当前实现中技能默认可见，只有显式 disable 才隐藏。 */
    public boolean isEnabled(String sourceKey, String skillName) throws Exception {
        return isVisible(sourceKey, skillName);
    }

    @Override
    public List<SkillDescriptor> listSkills(String category) throws Exception {
        processPendingImportsQuietly();
        return listSkillsFromRoot(FileUtil.file(appConfig.getRuntime().getSkillsDir()), category);
    }

    public List<SkillDescriptor> listSkills(String category, AgentRuntimeScope agentScope)
            throws Exception {
        processPendingImportsQuietly();
        List<SkillDescriptor> skills = new ArrayList<SkillDescriptor>();
        skills.addAll(
                listSkillsFromRoot(FileUtil.file(appConfig.getRuntime().getSkillsDir()), null));
        if (agentScope != null
                && !agentScope.isDefaultAgentName()
                && StrUtil.isNotBlank(agentScope.getSkillsDir())) {
            skills.addAll(listSkillsFromRoot(FileUtil.file(agentScope.getSkillsDir()), null));
        }
        skills.sort(skillComparator());
        skills = filterAgentSkills(skills, agentScope);
        if (StrUtil.isBlank(category)) {
            return skills;
        }
        List<SkillDescriptor> filtered = new ArrayList<SkillDescriptor>();
        for (SkillDescriptor descriptor : skills) {
            if (category.equals(descriptor.getCategory())) {
                filtered.add(descriptor);
            }
        }
        return filtered;
    }

    private List<SkillDescriptor> listSkillsFromRoot(File root, String category) throws Exception {
        if (!root.exists()) {
            return Collections.emptyList();
        }

        List<SkillDescriptor> skills = new ArrayList<SkillDescriptor>();
        collectSkills(root, skills);
        skills.sort(skillComparator());

        if (StrUtil.isBlank(category)) {
            return skills;
        }

        List<SkillDescriptor> filtered = new ArrayList<SkillDescriptor>();
        for (SkillDescriptor descriptor : skills) {
            if (category.equals(descriptor.getCategory())) {
                filtered.add(descriptor);
            }
        }
        return filtered;
    }

    private Comparator<SkillDescriptor> skillComparator() {
        return new Comparator<SkillDescriptor>() {
            @Override
            public int compare(SkillDescriptor left, SkillDescriptor right) {
                String leftCategory = StrUtil.nullToDefault(left.getCategory(), "");
                String rightCategory = StrUtil.nullToDefault(right.getCategory(), "");
                int result = leftCategory.compareTo(rightCategory);
                if (result != 0) {
                    return result;
                }
                return left.getName().compareTo(right.getName());
            }
        };
    }

    @Override
    public SkillView viewSkill(String nameOrPath, String filePath) throws Exception {
        return viewSkill(nameOrPath, filePath, null);
    }

    public SkillView viewSkill(String nameOrPath, String filePath, AgentRuntimeScope agentScope)
            throws Exception {
        processPendingImportsQuietly();
        SkillDescriptor descriptor = findDescriptor(nameOrPath, agentScope);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }

        File target = resolveSkillFile(descriptor, filePath);
        if (!target.exists()) {
            throw new IllegalStateException("Skill file not found: " + target.getAbsolutePath());
        }

        SkillView view = new SkillView();
        view.setDescriptor(descriptor);
        view.setFilePath(filePath);
        view.setContent(FileUtil.readUtf8String(target));
        view.setLinkedFiles(new ArrayList<String>(descriptor.getLinkedFiles()));
        return view;
    }

    @Override
    public String renderSkillIndexPrompt(String sourceKey) throws Exception {
        return renderSkillIndexPrompt(sourceKey, null);
    }

    public String renderSkillIndexPrompt(String sourceKey, AgentRuntimeScope agentScope)
            throws Exception {
        processPendingImportsQuietly();
        List<SkillDescriptor> skills = listSkills(null, agentScope);
        Map<String, List<SkillDescriptor>> grouped =
                new LinkedHashMap<String, List<SkillDescriptor>>();
        for (SkillDescriptor descriptor : skills) {
            if (!isVisible(sourceKey, descriptor.canonicalName())) {
                continue;
            }
            if (!isRuntimeVisible(sourceKey, descriptor, agentScope)) {
                continue;
            }
            String category =
                    StrUtil.blankToDefault(
                            descriptor.getCategory(), SkillConstants.DEFAULT_CATEGORY);
            List<SkillDescriptor> items = grouped.get(category);
            if (items == null) {
                items = new ArrayList<SkillDescriptor>();
                grouped.put(category, items);
            }
            items.add(descriptor);
        }

        if (grouped.isEmpty()) {
            return "";
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append("## Skills (渐进披露)\n");
        buffer.append("在回复前先浏览下列技能索引；如果某个技能明显匹配当前任务，请先用 skill_view(name) 加载全文再执行。\n");
        buffer.append("<available_skills>\n");
        for (Map.Entry<String, List<SkillDescriptor>> entry : grouped.entrySet()) {
            buffer.append("  ").append(entry.getKey()).append(":\n");
            for (SkillDescriptor descriptor : entry.getValue()) {
                buffer.append("    - ")
                        .append(descriptor.canonicalName())
                        .append(": ")
                        .append(descriptorLine(descriptor))
                        .append('\n');
            }
        }
        buffer.append("</available_skills>");
        return buffer.toString();
    }

    @Override
    public boolean isVisible(String sourceKey, String canonicalName) throws Exception {
        return preferenceStore.isSkillEnabled(sourceKey, canonicalName);
    }

    @Override
    public void setVisible(String sourceKey, String canonicalName, boolean visible)
            throws Exception {
        preferenceStore.setSkillEnabled(sourceKey, canonicalName, visible);
    }

    /** 创建新技能。 */
    public SkillDescriptor createSkill(String name, String category, String content) {
        validateSkillName(name);
        validateCategory(category);
        validateSkillContent(content);
        File skillDir = resolveSkillDir(name, category);
        if (skillDir.exists()) {
            throw new IllegalStateException(
                    "Skill already exists: " + canonicalName(category, name));
        }
        writeSkillMainFile(skillDir, content);
        return buildDescriptor(skillDir, normalizeCategory(category));
    }

    /** 全量改写技能主文件。 */
    public SkillDescriptor editSkill(String nameOrPath, String content) throws Exception {
        validateSkillContent(content);
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        ensureWritable(descriptor);
        writeSkillMainFile(FileUtil.file(descriptor.getSkillDir()), content);
        return buildDescriptor(FileUtil.file(descriptor.getSkillDir()), descriptor.getCategory());
    }

    /** 在技能主文件或支持文件中做定点替换。 */
    public String patchSkill(String nameOrPath, String oldText, String newText, String filePath)
            throws Exception {
        SkillView view = viewSkill(nameOrPath, filePath);
        if (StrUtil.isBlank(oldText) || !view.getContent().contains(oldText)) {
            throw new IllegalStateException("Patch target not found.");
        }
        ensureWritable(view.getDescriptor());
        File target = resolveSkillFile(view.getDescriptor(), filePath);
        writeTextAtomically(
                target, view.getContent().replace(oldText, StrUtil.nullToEmpty(newText)));
        return "Patched skill file: " + target.getAbsolutePath();
    }

    /** 删除技能目录。 */
    public String deleteSkill(String nameOrPath) throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        ensureWritable(descriptor);
        FileUtil.del(FileUtil.file(descriptor.getSkillDir()));
        return "Deleted skill: " + descriptor.canonicalName();
    }

    /** 写入技能支持文件。 */
    public String writeSkillFile(String nameOrPath, String filePath, String fileContent)
            throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        ensureWritable(descriptor);
        if ("SKILL.md".equalsIgnoreCase(StrUtil.nullToEmpty(filePath).trim().replace('\\', '/'))) {
            return editSkill(nameOrPath, fileContent).canonicalName();
        }
        validateSupportFilePath(filePath);
        File target = resolveSkillFile(descriptor, filePath);
        writeTextAtomically(target, StrUtil.nullToEmpty(fileContent));
        return "Wrote skill file: " + target.getAbsolutePath();
    }

    /** 删除技能支持文件。 */
    public String removeSkillFile(String nameOrPath, String filePath) throws Exception {
        SkillDescriptor descriptor = findDescriptor(nameOrPath);
        if (descriptor == null) {
            throw new IllegalStateException("Skill not found: " + nameOrPath);
        }
        ensureWritable(descriptor);
        validateSupportFilePath(filePath);
        File target = resolveSkillFile(descriptor, filePath);
        if (!target.exists()) {
            throw new IllegalStateException("Skill file not found: " + target.getAbsolutePath());
        }
        FileUtil.del(target);
        return "Removed skill file: " + target.getAbsolutePath();
    }

    /** 预测新技能主文件路径。 */
    public File resolveSkillMainFile(String name, String category) {
        validateSkillName(name);
        validateCategory(category);
        return FileUtil.file(resolveSkillDir(name, category), SkillConstants.SKILL_FILE_NAME);
    }

    /** 递归扫描根目录与单层分类目录中的技能。 */
    private void collectSkills(File root, List<SkillDescriptor> output) {
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }

            File directSkill = FileUtil.file(child, SkillConstants.SKILL_FILE_NAME);
            if (directSkill.exists()) {
                output.add(buildDescriptor(child, null));
                continue;
            }

            File[] nestedChildren = child.listFiles();
            if (nestedChildren == null) {
                continue;
            }
            for (File nested : nestedChildren) {
                if (nested.isDirectory()
                        && FileUtil.file(nested, SkillConstants.SKILL_FILE_NAME).exists()) {
                    output.add(buildDescriptor(nested, child.getName()));
                }
            }
        }
    }

    /** 构建技能元数据。 */
    private SkillDescriptor buildDescriptor(File skillDir, String category) {
        File skillFile = FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME);
        String content = FileUtil.readUtf8String(skillFile);
        Map<String, Object> frontmatter = SkillFrontmatterSupport.parseFrontmatter(content);
        SkillDescriptor descriptor = new SkillDescriptor();
        descriptor.setName(SkillFrontmatterSupport.resolveName(frontmatter, skillDir.getName()));
        descriptor.setCategory(category);
        descriptor.setSkillDir(skillDir.getAbsolutePath());
        descriptor.setDescription(
                SkillFrontmatterSupport.resolveDescription(
                        frontmatter, extractDescription(skillDir.getName(), content)));
        descriptor.setLinkedFiles(scanLinkedFiles(skillDir));
        descriptor.setTags(new ArrayList<String>(SkillFrontmatterSupport.resolveTags(frontmatter)));
        descriptor.setPlatforms(
                new ArrayList<String>(
                        SkillFrontmatterSupport.parseStringList(frontmatter.get("platforms"))));
        descriptor.setMetadata(new LinkedHashMap<String, Object>(frontmatter));
        descriptor.setSetupState(SkillFrontmatterSupport.resolveSetupState(frontmatter).name());

        HubInstallRecord hubRecord = findHubRecord(category, skillDir.getName());
        if (hubRecord != null) {
            descriptor.setSource(hubRecord.getSource());
            descriptor.setIdentifier(hubRecord.getIdentifier());
            descriptor.setTrustLevel(hubRecord.getTrustLevel());
            descriptor.getMetadata().put("hub", hubRecord.getMetadata());
        } else {
            descriptor.setSource("local");
            descriptor.setIdentifier(descriptor.canonicalName());
            descriptor.setTrustLevel("agent-created");
        }
        return descriptor;
    }

    /** 通过规范名或路径定位技能。 */
    private SkillDescriptor findDescriptor(String nameOrPath) throws Exception {
        return findDescriptor(nameOrPath, null);
    }

    private SkillDescriptor findDescriptor(String nameOrPath, AgentRuntimeScope agentScope)
            throws Exception {
        for (SkillDescriptor descriptor : listSkills(null, agentScope)) {
            if (descriptor.canonicalName().equals(nameOrPath)
                    || descriptor.getName().equals(nameOrPath)) {
                return descriptor;
            }
        }
        return null;
    }

    private List<SkillDescriptor> filterAgentSkills(
            List<SkillDescriptor> skills, AgentRuntimeScope agentScope) {
        if (AgentRuntimePolicy.resolveAllowedSkills(agentScope).isEmpty()) {
            return skills;
        }
        List<SkillDescriptor> filtered = new ArrayList<SkillDescriptor>();
        for (SkillDescriptor descriptor : skills) {
            if (AgentRuntimePolicy.isSkillAllowed(agentScope, descriptor)) {
                filtered.add(descriptor);
            }
        }
        return filtered;
    }

    /** 从 SKILL.md 中提取描述；若无 frontmatter，则回退到首行正文。 */
    private String extractDescription(String fallbackName, String content) {
        if (StrUtil.isBlank(content)) {
            return fallbackName;
        }

        if (content.startsWith("---")) {
            String[] lines = content.split("\\R");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if ("---".equals(line)) {
                    break;
                }
                if (line.startsWith("description:")) {
                    return line.substring("description:".length())
                            .trim()
                            .replace("\"", "")
                            .replace("'", "");
                }
            }
        }

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.length() == 0 || trimmed.startsWith("#")) {
                continue;
            }
            return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
        }
        return fallbackName;
    }

    /** 扫描技能支持文件列表。 */
    private List<String> scanLinkedFiles(File skillDir) {
        List<String> linkedFiles = new ArrayList<String>();
        addRelativeFiles(skillDir, SkillConstants.REFERENCES_DIR, linkedFiles);
        addRelativeFiles(skillDir, SkillConstants.TEMPLATES_DIR, linkedFiles);
        addRelativeFiles(skillDir, SkillConstants.SCRIPTS_DIR, linkedFiles);
        addRelativeFiles(skillDir, SkillConstants.ASSETS_DIR, linkedFiles);
        linkedFiles.sort(String::compareTo);
        return linkedFiles;
    }

    /** 扫描指定支持目录内文件。 */
    private void addRelativeFiles(File skillDir, String childDirName, List<String> output) {
        File childDir = FileUtil.file(skillDir, childDirName);
        if (!childDir.exists() || !childDir.isDirectory()) {
            return;
        }

        List<File> files = FileUtil.loopFiles(childDir);
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            String absolute = file.getAbsolutePath();
            String root = skillDir.getAbsolutePath() + File.separator;
            if (absolute.startsWith(root)) {
                String relative =
                        absolute.substring(root.length()).replace(File.separatorChar, '/');
                if (!relative.startsWith(".hub")) {
                    output.add(relative);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureWritable(SkillDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        Map<String, Object> metadata = descriptor.getMetadata();
        boolean pinned = false;
        boolean readOnly = false;
        if (metadata != null) {
            pinned = asBoolean(metadata.get("pinned"));
            readOnly = asBoolean(metadata.get("readonly")) || asBoolean(metadata.get("readOnly"));
            Object curator = metadata.get("curator");
            if (curator instanceof Map) {
                pinned = pinned || asBoolean(((Map<String, Object>) curator).get("pinned"));
                readOnly = readOnly || asBoolean(((Map<String, Object>) curator).get("readonly"));
            }
            Object hermes = metadata.get("hermes");
            if (hermes instanceof Map) {
                pinned = pinned || asBoolean(((Map<String, Object>) hermes).get("pinned"));
                readOnly = readOnly || asBoolean(((Map<String, Object>) hermes).get("readonly"));
            }
        }
        if (pinned || readOnly || !"agent-created".equals(descriptor.getTrustLevel())) {
            throw new IllegalStateException(
                    "Skill is pinned/read-only and cannot be modified: "
                            + descriptor.canonicalName());
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    /** 解析技能目录。 */
    private File resolveSkillDir(String name, String category) {
        String normalizedCategory = normalizeCategory(category);
        if (StrUtil.isBlank(normalizedCategory)) {
            return FileUtil.file(appConfig.getRuntime().getSkillsDir(), name);
        }
        return FileUtil.file(appConfig.getRuntime().getSkillsDir(), normalizedCategory, name);
    }

    /** 规范化分类值。 */
    private String normalizeCategory(String category) {
        if (StrUtil.isBlank(category)
                || SkillConstants.DEFAULT_CATEGORY.equalsIgnoreCase(category)) {
            return null;
        }
        return category.trim();
    }

    /** 写技能主文件并创建默认目录结构。 */
    private void writeSkillMainFile(File skillDir, String content) {
        FileUtil.mkdir(skillDir);
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.REFERENCES_DIR));
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.TEMPLATES_DIR));
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.SCRIPTS_DIR));
        FileUtil.mkdir(FileUtil.file(skillDir, SkillConstants.ASSETS_DIR));
        writeTextAtomically(
                FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME),
                StrUtil.nullToEmpty(content));
    }

    /** 解析技能支持文件路径。 */
    private File resolveSkillFile(SkillDescriptor descriptor, String filePath) throws Exception {
        if (StrUtil.isNotBlank(filePath)) {
            validateSupportFilePath(filePath);
        }

        File skillDir = FileUtil.file(descriptor.getSkillDir()).getCanonicalFile();
        File candidate =
                StrUtil.isBlank(filePath)
                        ? FileUtil.file(skillDir, SkillConstants.SKILL_FILE_NAME)
                        : FileUtil.file(skillDir, filePath);
        File target = candidate.getCanonicalFile();
        String skillRoot = skillDir.getAbsolutePath() + File.separator;
        if (!target.getAbsolutePath().equals(skillDir.getAbsolutePath())
                && !target.getAbsolutePath().startsWith(skillRoot)) {
            throw new IllegalStateException(
                    "Skill file path is outside skill directory: " + filePath);
        }
        return target;
    }

    /** 生成规范名。 */
    private String canonicalName(String category, String name) {
        return StrUtil.isBlank(category) ? name : category + "/" + name;
    }

    /** 校验技能名。 */
    private void validateSkillName(String name) {
        if (StrUtil.isBlank(name) || !name.matches(VALID_NAME_PATTERN)) {
            throw new IllegalStateException("Invalid skill name: " + name);
        }
    }

    /** 校验分类名。 */
    private void validateCategory(String category) {
        if (StrUtil.isBlank(category)) {
            return;
        }
        if (category.contains("/") || category.contains("\\")) {
            throw new IllegalStateException("Category must be a single directory name.");
        }
        if (!category.matches(VALID_NAME_PATTERN)) {
            throw new IllegalStateException("Invalid category: " + category);
        }
    }

    /** 校验技能主文件内容。 */
    private void validateSkillContent(String content) {
        if (StrUtil.isBlank(content)) {
            throw new IllegalStateException("Skill content cannot be empty.");
        }
        if (!content.startsWith("---")) {
            throw new IllegalStateException("Skill content must start with YAML frontmatter.");
        }
    }

    /** 校验支持文件相对路径。 */
    private void validateSupportFilePath(String filePath) {
        if (StrUtil.isBlank(filePath) || filePath.contains("..")) {
            throw new IllegalStateException("Invalid skill file path: " + filePath);
        }
    }

    private HubInstallRecord findHubRecord(String category, String name) {
        String installPath = StrUtil.isBlank(category) ? name : category + "/" + name;
        for (HubInstallRecord record : hubStateStore.listInstalled()) {
            if (installPath.equals(record.getInstallPath())) {
                return record;
            }
        }
        return null;
    }

    private void processPendingImportsQuietly() {
        if (skillImportService == null) {
            return;
        }
        try {
            skillImportService.processPendingImports(false);
        } catch (Exception ignored) {
            // auto import failure should not break normal skills usage
        }
    }

    private boolean isRuntimeVisible(
            String sourceKey, SkillDescriptor descriptor, AgentRuntimeScope agentScope) {
        if (SkillSetupState.UNSUPPORTED.name().equals(descriptor.getSetupState())) {
            return false;
        }
        Map<String, Object> hermes =
                SkillFrontmatterSupport.getHermesMetadata(descriptor.getMetadata());
        if (!checkRequiresTools(
                sourceKey,
                SkillFrontmatterSupport.parseStringList(hermes.get("requires_tools")),
                agentScope)) {
            return false;
        }
        if (!checkRequiresToolsets(
                sourceKey,
                SkillFrontmatterSupport.parseStringList(hermes.get("requires_toolsets")),
                agentScope)) {
            return false;
        }
        if (!checkFallbackTools(
                sourceKey,
                SkillFrontmatterSupport.parseStringList(hermes.get("fallback_for_tools")),
                agentScope)) {
            return false;
        }
        return checkFallbackToolsets(
                sourceKey,
                SkillFrontmatterSupport.parseStringList(hermes.get("fallback_for_toolsets")),
                agentScope);
    }

    private boolean checkRequiresTools(
            String sourceKey, List<String> tools, AgentRuntimeScope agentScope) {
        if (tools.isEmpty()) {
            return true;
        }
        for (String tool : tools) {
            if (!isToolEnabled(sourceKey, tool, agentScope)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkRequiresToolsets(
            String sourceKey, List<String> toolsets, AgentRuntimeScope agentScope) {
        if (toolsets.isEmpty()) {
            return true;
        }
        for (String toolset : toolsets) {
            if (!isAnyToolEnabled(sourceKey, toolNamesForToolset(toolset), agentScope)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkFallbackTools(
            String sourceKey, List<String> tools, AgentRuntimeScope agentScope) {
        if (tools.isEmpty()) {
            return true;
        }
        for (String tool : tools) {
            if (isToolEnabled(sourceKey, tool, agentScope)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkFallbackToolsets(
            String sourceKey, List<String> toolsets, AgentRuntimeScope agentScope) {
        if (toolsets.isEmpty()) {
            return true;
        }
        for (String toolset : toolsets) {
            if (isAnyToolEnabled(sourceKey, toolNamesForToolset(toolset), agentScope)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAnyToolEnabled(
            String sourceKey, List<String> tools, AgentRuntimeScope agentScope) {
        if (tools.isEmpty()) {
            return false;
        }
        for (String tool : tools) {
            if (isToolEnabled(sourceKey, tool, agentScope)) {
                return true;
            }
        }
        return false;
    }

    private boolean isToolEnabled(String sourceKey, String toolName) {
        return isToolEnabled(sourceKey, toolName, null);
    }

    private boolean isToolEnabled(String sourceKey, String toolName, AgentRuntimeScope agentScope) {
        if (!AgentRuntimePolicy.isToolAllowed(agentScope, toolName)) {
            return false;
        }
        try {
            return preferenceStore.isToolEnabled(sourceKey, toolName);
        } catch (Exception e) {
            return true;
        }
    }

    private List<String> toolNamesForToolset(String toolset) {
        if ("web".equalsIgnoreCase(toolset)) {
            return java.util.Arrays.asList(
                    ToolNameConstants.WEBSEARCH,
                    ToolNameConstants.WEBFETCH,
                    ToolNameConstants.CODESEARCH);
        }
        if ("terminal".equalsIgnoreCase(toolset)) {
            return java.util.Arrays.asList(
                    ToolNameConstants.EXECUTE_SHELL,
                    ToolNameConstants.EXECUTE_PYTHON,
                    ToolNameConstants.EXECUTE_JS,
                    ToolNameConstants.GET_CURRENT_TIME,
                    ToolNameConstants.FILE_READ,
                    ToolNameConstants.FILE_WRITE,
                    ToolNameConstants.FILE_LIST,
                    ToolNameConstants.FILE_DELETE);
        }
        if ("skills".equalsIgnoreCase(toolset)) {
            return java.util.Arrays.asList(
                    ToolNameConstants.SKILLS_LIST,
                    ToolNameConstants.SKILL_VIEW,
                    ToolNameConstants.SKILL_MANAGE);
        }
        if ("memory".equalsIgnoreCase(toolset)) {
            return java.util.Arrays.asList(
                    ToolNameConstants.MEMORY, ToolNameConstants.SESSION_SEARCH);
        }
        if ("cron".equalsIgnoreCase(toolset)) {
            return java.util.Collections.singletonList(ToolNameConstants.CRONJOB);
        }
        if ("messaging".equalsIgnoreCase(toolset)) {
            return java.util.Collections.singletonList(ToolNameConstants.SEND_MESSAGE);
        }
        if ("todo".equalsIgnoreCase(toolset)) {
            return java.util.Collections.singletonList(ToolNameConstants.TODO);
        }
        if ("delegate".equalsIgnoreCase(toolset)) {
            return java.util.Collections.singletonList(ToolNameConstants.DELEGATE_TASK);
        }
        if ("approval".equalsIgnoreCase(toolset)) {
            return java.util.Collections.emptyList();
        }
        if ("config".equalsIgnoreCase(toolset)) {
            return java.util.Arrays.asList(
                    ToolNameConstants.CONFIG_GET,
                    ToolNameConstants.CONFIG_SET,
                    ToolNameConstants.CONFIG_SET_SECRET,
                    ToolNameConstants.CONFIG_REFRESH);
        }
        return java.util.Collections.emptyList();
    }

    private String descriptorLine(SkillDescriptor descriptor) {
        StringBuilder buffer =
                new StringBuilder(StrUtil.nullToDefault(descriptor.getDescription(), ""));
        if (SkillSetupState.SETUP_NEEDED.name().equals(descriptor.getSetupState())) {
            buffer.append(" [setup_needed]");
        }
        if (StrUtil.isNotBlank(descriptor.getSource()) && !"local".equals(descriptor.getSource())) {
            buffer.append(" [").append(descriptor.getSource()).append("]");
        }
        return buffer.toString().trim();
    }

    /** 以原子替换方式写文本，降低并发写和中断写导致的半成品风险。 */
    private void writeTextAtomically(File target, String content) {
        try {
            FileUtil.mkParentDirs(target);
            File tempFile =
                    FileUtil.file(
                            target.getParentFile(), target.getName() + ".tmp-" + System.nanoTime());
            FileUtil.writeUtf8String(StrUtil.nullToEmpty(content), tempFile);
            try {
                Files.move(
                        tempFile.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                if (tempFile.exists()) {
                    FileUtil.del(tempFile);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to write skill file: " + target.getAbsolutePath(), e);
        }
    }
}
