package com.jimuqu.solon.claw.skillhub.source;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import java.util.ArrayList;
import java.util.List;
import org.noear.snack4.ONode;

/** LobeHub source adapter。 */
public class LobeHubSkillSource implements SkillSource {
    private static final String INDEX_URL = "https://chat-agents.lobehub.com/index.json";

    private final SkillHubHttpClient httpClient;
    private final SkillHubStateStore stateStore;

    public LobeHubSkillSource(SkillHubHttpClient httpClient, SkillHubStateStore stateStore) {
        this.httpClient = httpClient;
        this.stateStore = stateStore;
    }

    @Override
    public List<SkillMeta> search(String query, int limit) throws Exception {
        ONode index = fetchIndex();
        List<SkillMeta> results = new ArrayList<SkillMeta>();
        String normalized = StrUtil.nullToEmpty(query).toLowerCase();
        ONode agents = index.get("agents").isArray() ? index.get("agents") : index;
        for (int i = 0; i < agents.size(); i++) {
            ONode agent = agents.get(i);
            ONode metaNode = agent.get("meta").isObject() ? agent.get("meta") : agent;
            String identifier =
                    StrUtil.blankToDefault(
                            agent.get("identifier").getString(),
                            metaNode.get("title").getString().toLowerCase().replace(" ", "-"));
            String title = StrUtil.blankToDefault(metaNode.get("title").getString(), identifier);
            String description = metaNode.get("description").getString();
            String tagsJoined = String.join(" ", normalizeTags(metaNode.get("tags")));
            if ((title + " " + description + " " + tagsJoined).toLowerCase().contains(normalized)) {
                SkillMeta meta = new SkillMeta();
                meta.setName(identifier);
                meta.setDescription(description);
                meta.setSource(sourceId());
                meta.setIdentifier("lobehub/" + identifier);
                meta.setTrustLevel("community");
                meta.setTags(normalizeTags(metaNode.get("tags")));
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
        String agentId = normalizeIdentifier(identifier);
        ONode agent =
                ONode.ofJson(
                        httpClient.getText(
                                "https://chat-agents.lobehub.com/" + agentId + ".json", null));
        if (!agent.isObject()) {
            return null;
        }
        SkillBundle bundle = new SkillBundle();
        bundle.setName(agentId);
        bundle.getFiles().put("SKILL.md", convertToSkillMd(agent));
        bundle.setSource(sourceId());
        bundle.setIdentifier("lobehub/" + agentId);
        bundle.setTrustLevel("community");
        return bundle;
    }

    @Override
    public SkillMeta inspect(String identifier) throws Exception {
        String agentId = normalizeIdentifier(identifier);
        ONode index = fetchIndex();
        ONode agents = index.get("agents").isArray() ? index.get("agents") : index;
        for (int i = 0; i < agents.size(); i++) {
            ONode agent = agents.get(i);
            if (agentId.equals(agent.get("identifier").getString())) {
                ONode metaNode = agent.get("meta").isObject() ? agent.get("meta") : agent;
                SkillMeta meta = new SkillMeta();
                meta.setName(agentId);
                meta.setDescription(metaNode.get("description").getString());
                meta.setSource(sourceId());
                meta.setIdentifier("lobehub/" + agentId);
                meta.setTrustLevel("community");
                meta.setTags(normalizeTags(metaNode.get("tags")));
                return meta;
            }
        }
        return null;
    }

    @Override
    public String sourceId() {
        return "lobehub";
    }

    @Override
    public String trustLevelFor(String identifier) {
        return "community";
    }

    private ONode fetchIndex() throws Exception {
        String cached = stateStore.readCachedIndex("lobehub_index");
        if (StrUtil.isNotBlank(cached)) {
            return ONode.ofJson(cached);
        }
        String text = httpClient.getText(INDEX_URL, null);
        stateStore.writeCachedIndex("lobehub_index", text);
        return ONode.ofJson(text);
    }

    public static String convertToSkillMd(ONode agentData) {
        ONode meta = agentData.get("meta").isObject() ? agentData.get("meta") : agentData;
        String identifier =
                StrUtil.blankToDefault(agentData.get("identifier").getString(), "lobehub-agent");
        String title = StrUtil.blankToDefault(meta.get("title").getString(), identifier);
        String description = meta.get("description").getString();
        List<String> tags = normalizeTagsStatic(meta.get("tags"));
        String systemRole = agentData.get("config").get("systemRole").getString();

        StringBuilder buffer = new StringBuilder();
        buffer.append("---\n");
        buffer.append("name: ").append(identifier).append("\n");
        buffer.append("description: ")
                .append(StrUtil.blankToDefault(description, title))
                .append("\n");
        buffer.append("metadata:\n");
        buffer.append("  hermes:\n");
        buffer.append("    tags: [").append(String.join(", ", tags)).append("]\n");
        buffer.append("---\n\n");
        buffer.append("# ").append(title).append("\n\n");
        if (StrUtil.isNotBlank(description)) {
            buffer.append(description).append("\n\n");
        }
        buffer.append("## Instructions\n\n");
        buffer.append(
                StrUtil.blankToDefault(
                        systemRole, "Follow the original LobeHub agent instructions."));
        buffer.append("\n");
        return buffer.toString();
    }

    private List<String> normalizeTags(ONode node) {
        return normalizeTagsStatic(node);
    }

    private static List<String> normalizeTagsStatic(ONode node) {
        List<String> tags = new ArrayList<String>();
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                tags.add(node.get(i).getString());
            }
        }
        return tags;
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier.startsWith("lobehub/")) {
            return identifier.substring("lobehub/".length());
        }
        return identifier;
    }
}
