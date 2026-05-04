package com.jimuqu.solon.claw.skillhub.source;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import java.util.ArrayList;
import java.util.List;
import org.noear.snack4.ONode;

/** Hermes 中央索引来源。 */
public class HermesIndexSource implements SkillSource {
    private static final String INDEX_URL =
            "https://hermes-agent.nousresearch.com/docs/api/skills-index.json";

    private final SkillHubHttpClient httpClient;
    private final SkillHubStateStore stateStore;
    private final GitHubSkillSource githubSkillSource;

    public HermesIndexSource(
            SkillHubHttpClient httpClient,
            SkillHubStateStore stateStore,
            GitHubSkillSource githubSkillSource) {
        this.httpClient = httpClient;
        this.stateStore = stateStore;
        this.githubSkillSource = githubSkillSource;
    }

    @Override
    public List<SkillMeta> search(String query, int limit) throws Exception {
        ONode index = loadIndex();
        List<SkillMeta> results = new ArrayList<SkillMeta>();
        String normalized = StrUtil.nullToEmpty(query).toLowerCase();
        ONode skills = index.get("skills");
        for (int i = 0; i < skills.size(); i++) {
            ONode item = skills.get(i);
            String searchable =
                    (item.get("name").getString()
                                    + " "
                                    + item.get("description").getString()
                                    + " "
                                    + item.get("tags").toJson())
                            .toLowerCase();
            if (normalized.length() == 0 || searchable.contains(normalized)) {
                SkillMeta meta = toMeta(item);
                results.add(meta);
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        return results;
    }

    @Override
    public SkillBundle fetch(String identifier) throws Exception {
        ONode entry = findEntry(identifier);
        if (entry == null) {
            return null;
        }
        String resolved = entry.get("resolved_github_id").getString();
        SkillBundle bundle = null;
        if (StrUtil.isNotBlank(resolved)) {
            bundle = githubSkillSource.fetch(resolved);
        }
        if (bundle == null) {
            String repo = entry.get("repo").getString();
            String path = entry.get("path").getString();
            if (StrUtil.isNotBlank(repo) && StrUtil.isNotBlank(path)) {
                bundle = githubSkillSource.fetch(repo + "/" + path);
            }
        }
        if (bundle != null) {
            bundle.setSource(StrUtil.blankToDefault(entry.get("source").getString(), sourceId()));
            bundle.setIdentifier(identifier);
            bundle.setTrustLevel(
                    StrUtil.blankToDefault(entry.get("trust_level").getString(), "community"));
        }
        return bundle;
    }

    @Override
    public SkillMeta inspect(String identifier) throws Exception {
        ONode entry = findEntry(identifier);
        return entry == null ? null : toMeta(entry);
    }

    @Override
    public String sourceId() {
        return "hermes-index";
    }

    @Override
    public String trustLevelFor(String identifier) {
        try {
            ONode entry = findEntry(identifier);
            return entry == null
                    ? "community"
                    : StrUtil.blankToDefault(entry.get("trust_level").getString(), "community");
        } catch (Exception e) {
            return "community";
        }
    }

    private ONode loadIndex() throws Exception {
        String cached = stateStore.readCachedIndex("hermes-index");
        if (StrUtil.isNotBlank(cached)) {
            return ONode.ofJson(cached);
        }
        String text = httpClient.getText(INDEX_URL, null);
        stateStore.writeCachedIndex("hermes-index", text);
        return ONode.ofJson(text);
    }

    private ONode findEntry(String identifier) throws Exception {
        ONode index = loadIndex();
        String normalized = stripPrefix(identifier);
        ONode skills = index.get("skills");
        for (int i = 0; i < skills.size(); i++) {
            ONode item = skills.get(i);
            String candidate = stripPrefix(item.get("identifier").getString());
            if (identifier.equals(item.get("identifier").getString())
                    || normalized.equals(candidate)) {
                return item;
            }
        }
        return null;
    }

    private SkillMeta toMeta(ONode node) {
        SkillMeta meta = new SkillMeta();
        meta.setName(node.get("name").getString());
        meta.setDescription(node.get("description").getString());
        meta.setSource(StrUtil.blankToDefault(node.get("source").getString(), sourceId()));
        meta.setIdentifier(node.get("identifier").getString());
        meta.setTrustLevel(
                StrUtil.blankToDefault(node.get("trust_level").getString(), "community"));
        meta.setRepo(node.get("repo").getString());
        meta.setPath(node.get("path").getString());
        List<String> tags = new ArrayList<String>();
        ONode tagNodes = node.get("tags");
        for (int i = 0; i < tagNodes.size(); i++) {
            tags.add(tagNodes.get(i).getString());
        }
        meta.setTags(tags);
        return meta;
    }

    private String stripPrefix(String identifier) {
        String normalized = StrUtil.nullToEmpty(identifier);
        String[] prefixes =
                new String[] {"skills-sh/", "skills.sh/", "official/", "github/", "clawhub/"};
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
        }
        return normalized;
    }
}
