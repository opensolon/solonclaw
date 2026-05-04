package com.jimuqu.solon.claw.skillhub.source;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.support.SkillFrontmatterSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 仓库内 optional-skills 来源。 */
public class OfficialSkillSource implements SkillSource {
    private final File repoRoot;

    public OfficialSkillSource(File repoRoot) {
        this.repoRoot = repoRoot;
    }

    @Override
    public List<SkillMeta> search(String query, int limit) {
        List<SkillMeta> results = new ArrayList<SkillMeta>();
        File root = FileUtil.file(repoRoot, "optional-skills");
        if (!root.exists()) {
            return results;
        }
        String normalized = StrUtil.nullToEmpty(query).toLowerCase();
        for (File categoryDir : root.listFiles()) {
            if (categoryDir == null || !categoryDir.isDirectory()) {
                continue;
            }
            for (File skillDir : categoryDir.listFiles()) {
                if (skillDir == null || !skillDir.isDirectory()) {
                    continue;
                }
                File skillFile = FileUtil.file(skillDir, "SKILL.md");
                if (!skillFile.exists()) {
                    continue;
                }
                String content = FileUtil.readUtf8String(skillFile);
                Map<String, Object> frontmatter = SkillFrontmatterSupport.parseFrontmatter(content);
                SkillMeta meta = new SkillMeta();
                meta.setName(SkillFrontmatterSupport.resolveName(frontmatter, skillDir.getName()));
                meta.setDescription(SkillFrontmatterSupport.resolveDescription(frontmatter, ""));
                meta.setSource(sourceId());
                meta.setIdentifier("official/" + categoryDir.getName() + "/" + skillDir.getName());
                meta.setTrustLevel("builtin");
                meta.setPath(categoryDir.getName() + "/" + skillDir.getName());
                meta.setTags(
                        new ArrayList<String>(SkillFrontmatterSupport.resolveTags(frontmatter)));
                if ((meta.getName() + " " + meta.getDescription())
                        .toLowerCase()
                        .contains(normalized)) {
                    results.add(meta);
                    if (results.size() >= limit) {
                        return results;
                    }
                }
            }
        }
        return results;
    }

    @Override
    public SkillBundle fetch(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        File skillDir = FileUtil.file(repoRoot, "optional-skills", normalized);
        if (!skillDir.exists()) {
            return null;
        }
        SkillBundle bundle = new SkillBundle();
        bundle.setName(skillDir.getName());
        bundle.setSource(sourceId());
        bundle.setIdentifier(identifier);
        bundle.setTrustLevel("builtin");
        for (File file : FileUtil.loopFiles(skillDir)) {
            if (!file.isDirectory()) {
                String relative =
                        file.getAbsolutePath()
                                .substring(skillDir.getAbsolutePath().length() + 1)
                                .replace(File.separatorChar, '/');
                bundle.getFiles().put(relative, FileUtil.readUtf8String(file));
            }
        }
        return bundle.getFiles().containsKey("SKILL.md") ? bundle : null;
    }

    @Override
    public SkillMeta inspect(String identifier) {
        SkillBundle bundle = fetch(identifier);
        if (bundle == null) {
            return null;
        }
        SkillMeta meta = new SkillMeta();
        Map<String, Object> frontmatter =
                SkillFrontmatterSupport.parseFrontmatter(bundle.getFiles().get("SKILL.md"));
        meta.setName(SkillFrontmatterSupport.resolveName(frontmatter, bundle.getName()));
        meta.setDescription(SkillFrontmatterSupport.resolveDescription(frontmatter, ""));
        meta.setSource(sourceId());
        meta.setIdentifier(identifier);
        meta.setTrustLevel("builtin");
        meta.setTags(new ArrayList<String>(SkillFrontmatterSupport.resolveTags(frontmatter)));
        return meta;
    }

    @Override
    public String sourceId() {
        return "official";
    }

    @Override
    public String trustLevelFor(String identifier) {
        return "builtin";
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier.startsWith("official/")) {
            return identifier.substring("official/".length());
        }
        return identifier;
    }
}
