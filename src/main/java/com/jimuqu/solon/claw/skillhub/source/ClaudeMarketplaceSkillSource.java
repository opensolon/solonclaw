package com.jimuqu.solon.claw.skillhub.source;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.support.GitHubAuth;
import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import java.util.ArrayList;
import java.util.List;
import org.noear.snack4.ONode;

/** Claude marketplace 风格来源。 */
public class ClaudeMarketplaceSkillSource implements SkillSource {
    private static final String[] KNOWN_MARKETPLACES =
            new String[] {"anthropics/skills", "aiskillstore/marketplace"};

    private final SkillHubHttpClient httpClient;
    private final GitHubAuth auth;
    private final GitHubSkillSource githubSkillSource;
    private final SkillHubStateStore stateStore;

    public ClaudeMarketplaceSkillSource(
            SkillHubHttpClient httpClient,
            GitHubAuth auth,
            GitHubSkillSource githubSkillSource,
            SkillHubStateStore stateStore) {
        this.httpClient = httpClient;
        this.auth = auth;
        this.githubSkillSource = githubSkillSource;
        this.stateStore = stateStore;
    }

    @Override
    public List<SkillMeta> search(String query, int limit) throws Exception {
        List<SkillMeta> results = new ArrayList<SkillMeta>();
        String normalized = StrUtil.nullToEmpty(query).toLowerCase();
        for (String repo : KNOWN_MARKETPLACES) {
            ONode plugins = fetchMarketplaceIndex(repo);
            for (int i = 0; i < plugins.size(); i++) {
                ONode item = plugins.get(i);
                String name = item.get("name").getString();
                String description = item.get("description").getString();
                if ((name + " " + description).toLowerCase().contains(normalized)) {
                    String identifier = resolveIdentifier(repo, item.get("source").getString());
                    SkillMeta meta = new SkillMeta();
                    meta.setName(name);
                    meta.setDescription(description);
                    meta.setSource(sourceId());
                    meta.setIdentifier(identifier);
                    meta.setTrustLevel(trustLevelFor(identifier));
                    meta.setRepo(repo);
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
    public SkillBundle fetch(String identifier) throws Exception {
        SkillBundle bundle = githubSkillSource.fetch(normalizeIdentifier(identifier));
        if (bundle != null) {
            bundle.setSource(sourceId());
        }
        return bundle;
    }

    @Override
    public SkillMeta inspect(String identifier) throws Exception {
        SkillMeta meta = githubSkillSource.inspect(normalizeIdentifier(identifier));
        if (meta != null) {
            meta.setSource(sourceId());
            meta.setTrustLevel(trustLevelFor(identifier));
        }
        return meta;
    }

    @Override
    public String sourceId() {
        return "claude-marketplace";
    }

    @Override
    public String trustLevelFor(String identifier) {
        return githubSkillSource.trustLevelFor(normalizeIdentifier(identifier));
    }

    private ONode fetchMarketplaceIndex(String repo) throws Exception {
        String cacheKey = "claude_marketplace_" + repo.replace("/", "_");
        String cached = stateStore.readCachedIndex(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            return ONode.ofJson(cached).get("plugins");
        }
        java.util.Map<String, String> headers = auth.getHeaders();
        headers.put("Accept", "application/vnd.github.v3.raw");
        String text =
                httpClient.getText(
                        "https://api.github.com/repos/"
                                + repo
                                + "/contents/.claude-plugin/marketplace.json",
                        headers);
        ONode node = ONode.ofJson(text);
        stateStore.writeCachedIndex(cacheKey, node.toJson());
        return node.get("plugins");
    }

    private String resolveIdentifier(String repo, String sourcePath) {
        if (StrUtil.isBlank(sourcePath)) {
            return repo;
        }
        if (sourcePath.startsWith("./")) {
            return repo + "/" + sourcePath.substring(2);
        }
        if (sourcePath.contains("/")) {
            return sourcePath;
        }
        return repo + "/" + sourcePath;
    }

    private String normalizeIdentifier(String identifier) {
        String normalized = StrUtil.nullToEmpty(identifier);
        if (normalized.startsWith(sourceId() + "/")) {
            return normalized.substring(sourceId().length() + 1);
        }
        return normalized;
    }
}
