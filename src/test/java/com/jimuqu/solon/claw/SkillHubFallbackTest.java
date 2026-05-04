package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.service.SkillGuardService;
import com.jimuqu.solon.claw.core.service.SkillImportService;
import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.InstallDecision;
import com.jimuqu.solon.claw.skillhub.model.ScanResult;
import com.jimuqu.solon.claw.skillhub.model.SkillBrowseResult;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillImportResult;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.service.DefaultSkillHubService;
import com.jimuqu.solon.claw.skillhub.source.GitHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.SkillSource;
import com.jimuqu.solon.claw.skillhub.support.GitHubAuth;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import com.jimuqu.solon.claw.support.FixtureSkillHubHttpClient;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SkillHubFallbackTest {
    @Test
    void searchShouldSkipFailingSourcesAndReturnAvailableResults() throws Exception {
        DefaultSkillHubService service =
                new TestableSkillHubService(
                        Collections.<SkillSource>singletonList(failingSource()),
                        Collections.<SkillSource>singletonList(fixedSource()));

        SkillBrowseResult result = service.search("demo", "all", 10);

        assertThat(result.getItems()).extracting(SkillMeta::getName).containsExactly("demo-skill");
        assertThat(result.getTimedOutSources()).containsExactly("github");
    }

    @Test
    void githubSearchShouldSkipDeadTapAndContinueWithOtherTaps() throws Exception {
        FixtureSkillHubHttpClient http =
                new FixtureSkillHubHttpClient()
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills",
                                "[{\"type\":\"dir\",\"name\":\"demo-skill\"}]")
                        .onText(
                                "https://api.github.com/repos/openai/skills/contents/skills/demo-skill/SKILL.md",
                                skillMd("demo-skill", "demo desc"));
        GitHubSkillSource source =
                new GitHubSkillSource(new GitHubAuth(http), http, newStateStore());

        List<SkillMeta> results = source.search("demo", 10);

        assertThat(results).extracting(SkillMeta::getName).containsExactly("demo-skill");
    }

    private SkillSource failingSource() {
        return new SkillSource() {
            @Override
            public List<SkillMeta> search(String query, int limit) throws Exception {
                throw new IllegalStateException("HTTP 404");
            }

            @Override
            public SkillBundle fetch(String identifier) {
                return null;
            }

            @Override
            public SkillMeta inspect(String identifier) {
                return null;
            }

            @Override
            public String sourceId() {
                return "github";
            }

            @Override
            public String trustLevelFor(String identifier) {
                return "community";
            }
        };
    }

    private SkillSource fixedSource() {
        return new SkillSource() {
            @Override
            public List<SkillMeta> search(String query, int limit) {
                SkillMeta meta = new SkillMeta();
                meta.setName("demo-skill");
                meta.setDescription("demo desc");
                meta.setSource("clawhub");
                meta.setIdentifier("demo-skill");
                meta.setTrustLevel("community");
                return Collections.singletonList(meta);
            }

            @Override
            public SkillBundle fetch(String identifier) {
                return null;
            }

            @Override
            public SkillMeta inspect(String identifier) {
                return null;
            }

            @Override
            public String sourceId() {
                return "clawhub";
            }

            @Override
            public String trustLevelFor(String identifier) {
                return "community";
            }
        };
    }

    private SkillHubStateStore newStateStore() throws Exception {
        File dir = Files.createTempDirectory("skillhub-fallback-state").toFile();
        return new SkillHubStateStore(dir);
    }

    private String skillMd(String name, String description) {
        return "---\nname: "
                + name
                + "\ndescription: "
                + description
                + "\ntags: [java, hub]\n---\n\n# Steps\n- example\n";
    }

    private static class TestableSkillHubService extends DefaultSkillHubService {
        private final List<SkillSource> sources;

        private TestableSkillHubService(List<SkillSource> failing, List<SkillSource> succeeding)
                throws Exception {
            super(
                    Files.createTempDirectory("skillhub-repo").toFile(),
                    Files.createTempDirectory("skillhub-skills").toFile(),
                    new NoopSkillImportService(),
                    new NoopSkillGuardService(),
                    new SkillHubStateStore(Files.createTempDirectory("skillhub-state").toFile()),
                    new FixtureSkillHubHttpClient(),
                    new GitHubAuth(new FixtureSkillHubHttpClient()),
                    null);
            this.sources = new ArrayList<SkillSource>();
            this.sources.addAll(failing);
            this.sources.addAll(succeeding);
        }

        @Override
        protected List<SkillSource> sources() {
            return sources;
        }
    }

    private static class NoopSkillImportService implements SkillImportService {
        @Override
        public SkillImportResult processPendingImports(boolean force) {
            return new SkillImportResult();
        }

        @Override
        public HubInstallRecord installBundle(
                SkillBundle bundle, String category, boolean force, File sourceArtifact) {
            return null;
        }
    }

    private static class NoopSkillGuardService implements SkillGuardService {
        @Override
        public ScanResult scanSkill(File skillDir, String source) {
            return new ScanResult();
        }

        @Override
        public InstallDecision shouldAllowInstall(ScanResult result, boolean force) {
            return new InstallDecision();
        }

        @Override
        public String formatReport(ScanResult result) {
            return "";
        }
    }
}
