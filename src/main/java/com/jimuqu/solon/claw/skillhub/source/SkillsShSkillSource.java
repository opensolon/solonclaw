package com.jimuqu.solon.claw.skillhub.source;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.noear.snack4.ONode;

/** skills.sh source adapter。 */
public class SkillsShSkillSource implements SkillSource {
    private static final String BASE_URL = "https://skills.sh";
    private static final String SEARCH_URL = BASE_URL + "/api/search";

    private final SkillHubHttpClient httpClient;
    private final SkillHubStateStore stateStore;
    private final GitHubSkillSource githubSkillSource;

    public SkillsShSkillSource(
            SkillHubHttpClient httpClient,
            SkillHubStateStore stateStore,
            GitHubSkillSource githubSkillSource) {
        this.httpClient = httpClient;
        this.stateStore = stateStore;
        this.githubSkillSource = githubSkillSource;
    }

    @Override
    public List<SkillMeta> search(String query, int limit) throws Exception {
        if (StrUtil.isBlank(query)) {
            return new ArrayList<SkillMeta>();
        }
        String cacheKey =
                "skills_sh_search_" + Integer.toHexString((query + "|" + limit).hashCode());
        String cached = stateStore.readCachedIndex(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            return deserializeList(cached);
        }

        String url =
                SEARCH_URL + "?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&limit=" + limit;
        ONode node = ONode.ofJson(httpClient.getText(url, null));
        ONode items = node.get("skills").isArray() ? node.get("skills") : node;
        List<SkillMeta> results = new ArrayList<SkillMeta>();
        for (int i = 0; i < items.size(); i++) {
            ONode item = items.get(i);
            SkillMeta meta = metaFromSearchItem(item);
            if (meta != null) {
                results.add(meta);
            }
        }
        stateStore.writeCachedIndex(cacheKey, ONode.serialize(results));
        return results;
    }

    @Override
    public SkillBundle fetch(String identifier) throws Exception {
        String canonical = normalizeIdentifier(identifier);
        SkillBundle bundle = githubSkillSource.fetch(canonical);
        if (bundle != null) {
            bundle.setSource(sourceId());
            bundle.setIdentifier(wrapIdentifier(canonical));
        }
        return bundle;
    }

    @Override
    public SkillMeta inspect(String identifier) throws Exception {
        String canonical = normalizeIdentifier(identifier);
        SkillMeta meta = githubSkillSource.inspect(canonical);
        if (meta != null) {
            meta.setSource(sourceId());
            meta.setIdentifier(wrapIdentifier(canonical));
            meta.setTrustLevel(trustLevelFor(canonical));
        }
        return meta;
    }

    @Override
    public String sourceId() {
        return "skills-sh";
    }

    @Override
    public String trustLevelFor(String identifier) {
        return githubSkillSource.trustLevelFor(normalizeIdentifier(identifier));
    }

    private SkillMeta metaFromSearchItem(ONode item) {
        String canonical = item.get("id").getString();
        if (StrUtil.isBlank(canonical)) {
            String repo = item.get("source").getString();
            String skillId = item.get("skillId").getString();
            if (StrUtil.hasBlank(repo, skillId)) {
                return null;
            }
            canonical = repo + "/" + skillId;
        }
        String[] parts = canonical.split("/", 3);
        if (parts.length < 3) {
            return null;
        }

        SkillMeta meta = new SkillMeta();
        meta.setName(
                StrUtil.blankToDefault(
                        item.get("name").getString(),
                        parts[2].substring(parts[2].lastIndexOf('/') + 1)));
        int installsCount = item.get("installs").getInt(0);
        String installs = installsCount > 0 ? String.valueOf(installsCount) : "";
        meta.setDescription(
                "Indexed by skills.sh from "
                        + parts[0]
                        + "/"
                        + parts[1]
                        + (StrUtil.isNotBlank(installs) ? " · " + installs + " installs" : ""));
        meta.setSource(sourceId());
        meta.setIdentifier(wrapIdentifier(canonical));
        meta.setTrustLevel(trustLevelFor(canonical));
        meta.setRepo(parts[0] + "/" + parts[1]);
        meta.setPath(parts[2]);
        return meta;
    }

    private List<SkillMeta> deserializeList(String json) {
        SkillMeta[] array = ONode.deserialize(json, SkillMeta[].class);
        List<SkillMeta> results = new ArrayList<SkillMeta>();
        if (array != null) {
            Collections.addAll(results, array);
        }
        return results;
    }

    private String normalizeIdentifier(String identifier) {
        String normalized = StrUtil.nullToEmpty(identifier).trim();
        if (normalized.startsWith("skills-sh/")) {
            normalized = normalized.substring("skills-sh/".length());
        }
        if (normalized.startsWith("skills.sh/")) {
            normalized = normalized.substring("skills.sh/".length());
        }
        return normalized.replace('\\', '/');
    }

    private String wrapIdentifier(String canonical) {
        return "skills-sh/" + canonical;
    }
}
