package com.jimuqu.solon.claw.skillhub.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.service.SkillGuardService;
import com.jimuqu.solon.claw.core.service.SkillImportService;
import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.InstallDecision;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillImportResult;
import com.jimuqu.solon.claw.skillhub.source.ClawHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.LobeHubSkillSource;
import com.jimuqu.solon.claw.skillhub.support.SkillBundlePathSupport;
import com.jimuqu.solon.claw.skillhub.support.SkillFrontmatterSupport;
import com.jimuqu.solon.claw.skillhub.support.SkillHubContentSupport;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.noear.snack4.ONode;

/** 默认技能导入服务。 */
public class DefaultSkillImportService implements SkillImportService {
    private final File skillsDir;
    private final SkillGuardService skillGuardService;
    private final SkillHubStateStore stateStore;

    public DefaultSkillImportService(
            File skillsDir, SkillGuardService skillGuardService, SkillHubStateStore stateStore) {
        this.skillsDir = skillsDir;
        this.skillGuardService = skillGuardService;
        this.stateStore = stateStore;
    }

    @Override
    public SkillImportResult processPendingImports(boolean force) throws Exception {
        SkillImportResult result = new SkillImportResult();
        File[] children = skillsDir.listFiles();
        if (children == null) {
            return result;
        }

        for (File child : children) {
            if (".hub".equals(child.getName())) {
                continue;
            }
            if (child.isDirectory()
                    && (isCanonicalSkillDir(child) || isCanonicalCategoryDir(child))) {
                continue;
            }
            try {
                List<SkillBundle> bundles =
                        child.isFile()
                                ? detectBundlesFromFile(child)
                                : detectBundlesFromDirectory(child);
                if (bundles.isEmpty()) {
                    continue;
                }
                for (SkillBundle bundle : bundles) {
                    installBundle(bundle, null, force, child);
                    result.setInstalledCount(result.getInstalledCount() + 1);
                    result.getMessages()
                            .add("Imported " + bundle.getName() + " from " + child.getName());
                }
                result.setArchivedCount(result.getArchivedCount() + 1);
            } catch (Exception e) {
                result.setBlockedCount(result.getBlockedCount() + 1);
                result.getMessages()
                        .add("Blocked import " + child.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public HubInstallRecord installBundle(
            SkillBundle bundle, String category, boolean force, File sourceArtifact)
            throws Exception {
        String skillName = SkillBundlePathSupport.normalizeSkillName(bundle.getName());
        File quarantineDir =
                FileUtil.file(stateStore.quarantineDir(), skillName + "-" + System.nanoTime());
        SkillHubContentSupport.writeBundle(quarantineDir, bundle);

        com.jimuqu.solon.claw.skillhub.model.ScanResult scanResult =
                skillGuardService.scanSkill(quarantineDir, bundle.getSource());
        InstallDecision decision = skillGuardService.shouldAllowInstall(scanResult, force);
        if (!decision.isAllowed()) {
            stateStore.appendAuditLog(
                    "BLOCKED",
                    skillName,
                    bundle.getSource(),
                    scanResult.getTrustLevel(),
                    scanResult.getVerdict(),
                    decision.getReason());
            archiveOriginal(sourceArtifact, "blocked-" + skillName);
            throw new IllegalStateException(decision.getReason());
        }

        String resolvedCategory = resolveCategory(category, bundle);
        File installDir =
                StrUtil.isBlank(resolvedCategory)
                        ? FileUtil.file(skillsDir, skillName)
                        : FileUtil.file(skillsDir, resolvedCategory, skillName);
        if (installDir.exists()) {
            FileUtil.del(installDir);
        }
        FileUtil.mkParentDirs(installDir);
        FileUtil.move(quarantineDir, installDir, true);

        HubInstallRecord record = new HubInstallRecord();
        record.setName(skillName);
        record.setSource(bundle.getSource());
        record.setIdentifier(bundle.getIdentifier());
        record.setTrustLevel(bundle.getTrustLevel());
        record.setScanVerdict(scanResult.getVerdict());
        record.setContentHash(SkillHubContentSupport.contentHash(installDir));
        record.setInstallPath(
                StrUtil.isBlank(resolvedCategory) ? skillName : resolvedCategory + "/" + skillName);
        record.setFiles(new ArrayList<String>(bundle.getFiles().keySet()));
        record.setMetadata(new LinkedHashMap<String, Object>(bundle.getMetadata()));
        stateStore.recordInstall(record);
        stateStore.appendAuditLog(
                "INSTALL",
                skillName,
                bundle.getSource(),
                bundle.getTrustLevel(),
                scanResult.getVerdict(),
                record.getContentHash());

        archiveOriginal(sourceArtifact, "imported-" + skillName);
        return record;
    }

    private List<SkillBundle> detectBundlesFromFile(File file) throws Exception {
        if ("zip".equalsIgnoreCase(FileUtil.extName(file))) {
            SkillBundle bundle = bundleFromZip(file);
            return bundle == null
                    ? Collections.<SkillBundle>emptyList()
                    : java.util.Collections.singletonList(bundle);
        }
        if ("json".equalsIgnoreCase(FileUtil.extName(file))) {
            SkillBundle bundle = bundleFromJsonFile(file);
            return bundle == null
                    ? Collections.<SkillBundle>emptyList()
                    : java.util.Collections.singletonList(bundle);
        }
        return Collections.emptyList();
    }

    private List<SkillBundle> detectBundlesFromDirectory(File dir) throws Exception {
        List<SkillBundle> bundles = new ArrayList<SkillBundle>();
        File marketplace = FileUtil.file(dir, ".claude-plugin", "marketplace.json");
        if (marketplace.exists()) {
            bundles.addAll(bundlesFromMarketplaceRepo(dir, marketplace));
        }

        File wellKnownIndex = FileUtil.file(dir, ".well-known", "skills", "index.json");
        if (wellKnownIndex.exists()) {
            bundles.addAll(
                    bundlesFromWellKnownIndex(
                            FileUtil.file(dir, ".well-known", "skills"), wellKnownIndex));
        }
        File indexFile = FileUtil.file(dir, "index.json");
        if (indexFile.exists()) {
            bundles.addAll(bundlesFromWellKnownIndex(dir, indexFile));
        }

        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile() && "json".equalsIgnoreCase(FileUtil.extName(child))) {
                    SkillBundle bundle = bundleFromJsonFile(child);
                    if (bundle != null) {
                        bundles.add(bundle);
                    }
                }
                if (child.isFile() && "zip".equalsIgnoreCase(FileUtil.extName(child))) {
                    SkillBundle bundle = bundleFromZip(child);
                    if (bundle != null) {
                        bundles.add(bundle);
                    }
                }
            }
        }

        if (bundles.isEmpty()) {
            SkillBundle nested = bundleFromNestedSkill(dir);
            if (nested != null) {
                bundles.add(nested);
            }
        }
        return bundles;
    }

    private SkillBundle bundleFromZip(File zipFile) throws Exception {
        byte[] bytes = FileUtil.readBytes(zipFile);
        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes));
        try {
            Map<String, String> files = new LinkedHashMap<String, String>();
            String commonPrefix = null;
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                int slash = name.indexOf('/');
                if (slash > 0) {
                    String prefix = name.substring(0, slash + 1);
                    commonPrefix =
                            commonPrefix == null
                                    ? prefix
                                    : commonPrefix.equals(prefix) ? commonPrefix : "";
                } else {
                    commonPrefix = "";
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zipInputStream.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                files.put(name, new String(out.toByteArray(), "UTF-8"));
            }

            Map<String, String> normalized = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entrySet : files.entrySet()) {
                String path = entrySet.getKey();
                if (StrUtil.isNotBlank(commonPrefix) && path.startsWith(commonPrefix)) {
                    path = path.substring(commonPrefix.length());
                }
                normalized.put(
                        SkillBundlePathSupport.normalizeBundlePath(path), entrySet.getValue());
            }
            if (!normalized.containsKey("SKILL.md")) {
                return null;
            }
            SkillBundle bundle = new SkillBundle();
            bundle.setName(FileUtil.mainName(zipFile));
            bundle.setFiles(normalized);
            bundle.setSource("clawhub");
            bundle.setIdentifier(FileUtil.mainName(zipFile));
            bundle.setTrustLevel("community");
            return bundle;
        } finally {
            zipInputStream.close();
        }
    }

    private SkillBundle bundleFromJsonFile(File jsonFile) {
        ONode node = ONode.ofJson(FileUtil.readUtf8String(jsonFile));
        if (looksLikeLobeHub(node)) {
            SkillBundle bundle = new SkillBundle();
            bundle.setName(FileUtil.mainName(jsonFile));
            bundle.getFiles().put("SKILL.md", LobeHubSkillSource.convertToSkillMd(node));
            bundle.setSource("lobehub");
            bundle.setIdentifier("lobehub/" + FileUtil.mainName(jsonFile));
            bundle.setTrustLevel("community");
            return bundle;
        }

        try {
            Map<String, String> files = ClawHubSkillSource.extractFiles(node, null);
            if (!files.containsKey("SKILL.md")) {
                return null;
            }
            SkillBundle bundle = new SkillBundle();
            bundle.setName(FileUtil.mainName(jsonFile));
            bundle.setFiles(files);
            bundle.setSource("clawhub");
            bundle.setIdentifier(FileUtil.mainName(jsonFile));
            bundle.setTrustLevel("community");
            return bundle;
        } catch (Exception e) {
            return null;
        }
    }

    private List<SkillBundle> bundlesFromMarketplaceRepo(File repoDir, File marketplaceFile) {
        List<SkillBundle> bundles = new ArrayList<SkillBundle>();
        ONode node = ONode.ofJson(FileUtil.readUtf8String(marketplaceFile));
        ONode plugins = node.get("plugins");
        for (int i = 0; i < plugins.size(); i++) {
            ONode plugin = plugins.get(i);
            String source = plugin.get("source").getString();
            if (StrUtil.isBlank(source)) {
                continue;
            }
            if (source.startsWith("./")) {
                source = source.substring(2);
            }
            File pluginDir = FileUtil.file(repoDir, source);
            SkillBundle bundle =
                    bundleFromNestedDirectory(
                            pluginDir, "claude-marketplace", repoDir.getName() + "/" + source);
            if (bundle != null) {
                bundles.add(bundle);
            }
        }
        return bundles;
    }

    private List<SkillBundle> bundlesFromWellKnownIndex(File baseDir, File indexFile) {
        List<SkillBundle> bundles = new ArrayList<SkillBundle>();
        ONode index = ONode.ofJson(FileUtil.readUtf8String(indexFile));
        ONode skills = index.get("skills");
        for (int i = 0; i < skills.size(); i++) {
            ONode skill = skills.get(i);
            String skillName = skill.get("name").getString();
            if (StrUtil.isBlank(skillName)) {
                continue;
            }
            File skillDir = FileUtil.file(baseDir, skillName);
            if (!skillDir.exists()) {
                continue;
            }
            SkillBundle bundle =
                    bundleFromNestedDirectory(skillDir, "well-known", "well-known:" + skillName);
            if (bundle != null) {
                bundles.add(bundle);
            }
        }
        return bundles;
    }

    private SkillBundle bundleFromNestedSkill(File dir) {
        File[] children = dir.listFiles();
        if (children == null || children.length != 1 || !children[0].isDirectory()) {
            return null;
        }
        return bundleFromNestedDirectory(children[0], "community", dir.getName());
    }

    private SkillBundle bundleFromNestedDirectory(File skillDir, String source, String identifier) {
        File skillFile = FileUtil.file(skillDir, "SKILL.md");
        if (!skillFile.exists()) {
            return null;
        }
        SkillBundle bundle = new SkillBundle();
        bundle.setName(skillDir.getName());
        bundle.setSource(source);
        bundle.setIdentifier(identifier);
        bundle.setTrustLevel("official".equals(source) ? "builtin" : "community");
        for (File file : FileUtil.loopFiles(skillDir)) {
            if (!file.isDirectory()) {
                String relative =
                        file.getAbsolutePath()
                                .substring(skillDir.getAbsolutePath().length() + 1)
                                .replace(File.separatorChar, '/');
                bundle.getFiles().put(relative, FileUtil.readUtf8String(file));
            }
        }
        return bundle;
    }

    private boolean isCanonicalSkillDir(File dir) {
        return FileUtil.file(dir, "SKILL.md").exists();
    }

    private boolean isCanonicalCategoryDir(File dir) {
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            return false;
        }
        boolean hasSkillChildren = false;
        for (File child : children) {
            if (child.isDirectory() && FileUtil.file(child, "SKILL.md").exists()) {
                hasSkillChildren = true;
                continue;
            }
            if (child.isDirectory()) {
                continue;
            }
            return false;
        }
        return hasSkillChildren;
    }

    private void archiveOriginal(File sourceArtifact, String prefix) {
        if (sourceArtifact == null || !sourceArtifact.exists()) {
            return;
        }
        File target =
                FileUtil.file(
                        stateStore.importedDir(),
                        prefix + "-" + sourceArtifact.getName() + "-" + System.nanoTime());
        FileUtil.mkParentDirs(target);
        FileUtil.move(sourceArtifact, target, true);
    }

    private String resolveCategory(String explicitCategory, SkillBundle bundle) {
        if (StrUtil.isNotBlank(explicitCategory)) {
            return SkillBundlePathSupport.normalizeCategoryName(explicitCategory);
        }
        Object metadataCategory = bundle.getMetadata().get("category");
        if (metadataCategory instanceof String && StrUtil.isNotBlank((String) metadataCategory)) {
            return SkillBundlePathSupport.normalizeCategoryName((String) metadataCategory);
        }
        String skillMd = bundle.getFiles().get("SKILL.md");
        if (StrUtil.isBlank(skillMd)) {
            return null;
        }
        java.util.Map<String, Object> frontmatter =
                SkillFrontmatterSupport.parseFrontmatter(skillMd);
        java.util.Map<String, Object> hermes =
                SkillFrontmatterSupport.getHermesMetadata(frontmatter);
        Object category = hermes.get("category");
        if (category instanceof String && StrUtil.isNotBlank((String) category)) {
            return SkillBundlePathSupport.normalizeCategoryName((String) category);
        }
        return null;
    }

    private boolean looksLikeLobeHub(ONode node) {
        return node.get("config").get("systemRole").isValue()
                || node.get("meta").get("title").isValue()
                || node.get("meta").get("description").isValue();
    }
}
