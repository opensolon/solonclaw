package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.source.ClaudeMarketplaceSkillSource;
import com.jimuqu.solon.claw.skillhub.source.ClawHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.GitHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.HermesIndexSource;
import com.jimuqu.solon.claw.skillhub.source.LobeHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.SkillsShSkillSource;
import com.jimuqu.solon.claw.skillhub.source.WellKnownSkillSource;
import com.jimuqu.solon.claw.skillhub.support.GitHubAuth;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import com.jimuqu.solon.claw.support.FixtureSkillHubHttpClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

public class SkillSourceAdapterTest {
    @Test
    void shouldInspectAndFetchGithubSkillBundle() throws Exception {
        FixtureSkillHubHttpClient http =
                new FixtureSkillHubHttpClient()
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills",
                                "[{\"type\":\"dir\",\"name\":\"demo-skill\"}]")
                        .onText(
                                "https://api.github.com/repos/anthropics/skills/contents/skills",
                                "[]")
                        .onText(
                                "https://api.github.com/repos/VoltAgent/awesome-agent-skills/contents/skills",
                                "[]")
                        .onText("https://api.github.com/repos/garrytan/gstack/contents/", "[]")
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/demo-skill/SKILL.md",
                                skillMd("demo-skill", "demo desc"))
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/demo-skill",
                                "[{\"type\":\"file\",\"name\":\"SKILL.md\"},{\"type\":\"dir\",\"name\":\"references\"}]")
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/demo-skill/references",
                                "[{\"type\":\"file\",\"name\":\"notes.md\"}]")
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/demo-skill/references/notes.md",
                                "# Notes");

        GitHubSkillSource source =
                new GitHubSkillSource(new GitHubAuth(http), http, newStateStore());

        List<SkillMeta> metas = source.search("demo", 10);
        SkillMeta inspected = source.inspect("openai/skills/skills/demo-skill");
        SkillBundle bundle = source.fetch("openai/skills/skills/demo-skill");

        assertThat(metas).hasSize(1);
        assertThat(inspected.getTrustLevel()).isEqualTo("trusted");
        assertThat(inspected.getTags()).contains("java", "hub");
        assertThat(bundle.getFiles()).containsKeys("SKILL.md", "references/notes.md");
    }

    @Test
    void shouldSupportWellKnownAndPreferDomesticSkillHubFixtures() throws Exception {
        FixtureSkillHubHttpClient http =
                new FixtureSkillHubHttpClient()
                        .onText(
                                "https://example.com/docs/.well-known/skills/index.json",
                                "{\"skills\":[{\"name\":\"mintlify\",\"description\":\"docs helper\",\"files\":[\"SKILL.md\",\"references/api.md\"]}]}")
                        .onText(
                                "https://example.com/docs/.well-known/skills/mintlify/SKILL.md",
                                skillMd("mintlify", "docs helper"))
                        .onText(
                                "https://example.com/docs/.well-known/skills/mintlify/references/api.md",
                                "# API")
                        .onText(
                                ClawHubSkillSource.CN_INDEX_URL,
                                "{\"skills\":[{\"slug\":\"demo-claw\",\"name\":\"Demo Claw\",\"description\":\"claw desc\",\"version\":\"1.0.0\",\"categories\":[\"automation\"]}]}")
                        .onText(
                                ClawHubSkillSource.CN_SEARCH_URL + "?q=demo&limit=10",
                                "{\"results\":[{\"slug\":\"demo-claw\",\"displayName\":\"Demo Claw\",\"summary\":\"claw desc\",\"version\":\"1.0.0\",\"tags\":[\"hub\"]}]}")
                        .onText(
                                ClawHubSkillSource.CN_SEARCH_URL + "?q=demo-claw&limit=20",
                                "{\"results\":[{\"slug\":\"demo-claw\",\"displayName\":\"Demo Claw\",\"summary\":\"claw desc\",\"version\":\"1.0.0\",\"tags\":[\"hub\"]}]}")
                        .onBytes(
                                String.format(
                                        ClawHubSkillSource.CN_PRIMARY_DOWNLOAD_URL, "demo-claw"),
                                zipBytes(
                                        bundleFiles(
                                                "demo-claw",
                                                "claw desc",
                                                "references/notes.md",
                                                "hello")));

        WellKnownSkillSource wellKnown = new WellKnownSkillSource(http);
        ClawHubSkillSource clawHub = new ClawHubSkillSource(http, newStateStore());

        List<SkillMeta> metas = clawHub.search("demo", 10);
        List<SkillMeta> browse = clawHub.search("", 10);
        SkillMeta inspected = clawHub.inspect("demo-claw");
        SkillBundle wellKnownBundle =
                wellKnown.fetch("well-known:https://example.com/docs/.well-known/skills#mintlify");
        SkillBundle clawBundle = clawHub.fetch("demo-claw");

        assertThat(metas).hasSize(1);
        assertThat(browse).hasSize(1);
        assertThat(inspected.getIdentifier()).isEqualTo("demo-claw");
        assertThat(inspected.getExtra()).containsEntry("version", "1.0.0");
        assertThat(wellKnownBundle.getFiles()).containsKeys("SKILL.md", "references/api.md");
        assertThat(clawBundle.getFiles()).containsKeys("SKILL.md", "references/notes.md");
    }

    @Test
    void shouldFallbackToPublicClawHubWhenDomesticMirrorUnavailable() throws Exception {
        FixtureSkillHubHttpClient http =
                new FixtureSkillHubHttpClient()
                        .onText(
                                ClawHubSkillSource.PUBLIC_BASE_URL + "/skills/demo-claw",
                                "{\"slug\":\"demo-claw\",\"displayName\":\"Demo Claw\",\"summary\":\"claw desc\",\"latestVersion\":{\"version\":\"1.0.0\"}}")
                        .onBytes(
                                ClawHubSkillSource.PUBLIC_BASE_URL
                                        + "/download?slug=demo-claw&version=1.0.0",
                                zipBytes(
                                        bundleFiles(
                                                "demo-claw",
                                                "claw desc",
                                                "references/notes.md",
                                                "hello")));

        ClawHubSkillSource clawHub = new ClawHubSkillSource(http, newStateStore());

        SkillMeta inspected = clawHub.inspect("demo-claw");
        SkillBundle bundle = clawHub.fetch("demo-claw");

        assertThat(inspected.getIdentifier()).isEqualTo("demo-claw");
        assertThat(bundle.getFiles()).containsKeys("SKILL.md", "references/notes.md");
    }

    @Test
    void shouldResolveSkillsShAndClaudeMarketplaceThroughGithubFixtures() throws Exception {
        FixtureSkillHubHttpClient http =
                new FixtureSkillHubHttpClient()
                        .onText(
                                "https://skills.sh/api/search?q=json&limit=10",
                                "{\"skills\":[{\"id\":\"openai/skills/skills/json-render\",\"name\":\"json-render\",\"installs\":12}]}")
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/json-render/SKILL.md",
                                skillMd("json-render", "render json"))
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/json-render",
                                "[{\"type\":\"file\",\"name\":\"SKILL.md\"}]")
                        .onText(
                                "https://api.github.com/repos/anthropics/skills/contents/.claude-plugin/marketplace.json",
                                "{\"plugins\":[{\"name\":\"review-skill\",\"description\":\"review helper\",\"source\":\"skills/review-skill\"}]}")
                        .onText(
                                "https://api.github.com/repos/aiskillstore/marketplace/contents/.claude-plugin/marketplace.json",
                                "{\"plugins\":[]}")
                        .onText(
                                "https://api.github.com/repos/anthropics/skills/contents/skills/review-skill/SKILL.md",
                                skillMd("review-skill", "review helper"))
                        .onText(
                                "https://api.github.com/repos/anthropics/skills/contents/skills/review-skill",
                                "[{\"type\":\"file\",\"name\":\"SKILL.md\"}]");

        SkillHubStateStore stateStore = newStateStore();
        GitHubSkillSource github = new GitHubSkillSource(new GitHubAuth(http), http, stateStore);
        SkillsShSkillSource skillsSh = new SkillsShSkillSource(http, stateStore, github);
        ClaudeMarketplaceSkillSource marketplace =
                new ClaudeMarketplaceSkillSource(http, new GitHubAuth(http), github, stateStore);

        assertThat(skillsSh.search("json", 10)).hasSize(1);
        assertThat(skillsSh.fetch("skills-sh/openai/skills/skills/json-render").getFiles())
                .containsKey("SKILL.md");
        assertThat(marketplace.search("review", 10)).hasSize(1);
        assertThat(marketplace.fetch("anthropics/skills/skills/review-skill").getFiles())
                .containsKey("SKILL.md");
    }

    @Test
    void shouldResolveLobeHubAndHermesIndexFixtures() throws Exception {
        FixtureSkillHubHttpClient http =
                new FixtureSkillHubHttpClient()
                        .onText(
                                "https://chat-agents.lobehub.com/index.json",
                                "{\"agents\":[{\"identifier\":\"demo-agent\",\"meta\":{\"title\":\"Demo Agent\",\"description\":\"agent desc\",\"tags\":[\"assist\"]}}]}")
                        .onText(
                                "https://chat-agents.lobehub.com/demo-agent.json",
                                "{\"identifier\":\"demo-agent\",\"meta\":{\"title\":\"Demo Agent\",\"description\":\"agent desc\",\"tags\":[\"assist\"]},\"config\":{\"systemRole\":\"Help the user\"}}")
                        .onText(
                                "https://hermes-agent.nousresearch.com/docs/api/skills-index.json",
                                "{\"skills\":[{\"name\":\"hub-skill\",\"description\":\"hub desc\",\"source\":\"github\",\"identifier\":\"github/openai/skills/skills/hub-skill\",\"trust_level\":\"trusted\",\"resolved_github_id\":\"openai/skills/skills/hub-skill\",\"tags\":[\"hub\"]}]}")
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/hub-skill/SKILL.md",
                                skillMd("hub-skill", "hub desc"))
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/hub-skill",
                                "[{\"type\":\"file\",\"name\":\"SKILL.md\"}]");

        SkillHubStateStore stateStore = newStateStore();
        GitHubSkillSource github = new GitHubSkillSource(new GitHubAuth(http), http, stateStore);
        LobeHubSkillSource lobeHub = new LobeHubSkillSource(http, stateStore);
        HermesIndexSource hermesIndex = new HermesIndexSource(http, stateStore, github);

        SkillBundle lobeBundle = lobeHub.fetch("lobehub/demo-agent");
        SkillBundle indexBundle = hermesIndex.fetch("github/openai/skills/skills/hub-skill");

        assertThat(lobeBundle.getFiles().get("SKILL.md"))
                .contains("## Instructions")
                .contains("Help the user");
        assertThat(indexBundle.getFiles()).containsKey("SKILL.md");
    }

    private SkillHubStateStore newStateStore() throws Exception {
        File dir = Files.createTempDirectory("skillhub-state").toFile();
        return new SkillHubStateStore(dir);
    }

    private String skillMd(String name, String description) {
        return "---\nname: "
                + name
                + "\ndescription: "
                + description
                + "\ntags: [java, hub]\n---\n\n# Steps\n- example\n";
    }

    private Map<String, String> bundleFiles(
            String name, String description, String extraPath, String extraContent) {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("SKILL.md", skillMd(name, description));
        files.put(extraPath, extraContent);
        files.put("_meta.json", "{\"slug\":\"" + name + "\",\"version\":\"1.0.0\"}");
        return files;
    }

    private byte[] zipBytes(Map<String, String> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output);
        try {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes("UTF-8"));
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return output.toByteArray();
    }

    private String escaped(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
