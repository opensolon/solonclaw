package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

public class SkillImportServiceTest {
    @Test
    void shouldAutoImportLobeHubJsonDroppedIntoRuntimeSkills() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        File json = FileUtil.file(env.appConfig.getRuntime().getSkillsDir(), "weather-agent.json");
        FileUtil.writeUtf8String(
                "{\"identifier\":\"weather-agent\",\"meta\":{\"title\":\"Weather Agent\",\"description\":\"weather helper\",\"tags\":[\"weather\"]},\"config\":{\"systemRole\":\"Answer weather questions\"}}",
                json);

        List<String> names = env.localSkillService.listSkillNames();

        assertThat(names).contains("weather-agent");
        assertThat(env.localSkillService.viewSkill("weather-agent", null).getContent())
                .contains("Answer weather questions");
        assertThat(
                        FileUtil.loopFiles(
                                FileUtil.file(
                                        env.appConfig.getRuntime().getSkillsDir(),
                                        ".hub",
                                        "imported")))
                .isNotEmpty();
    }

    @Test
    void shouldAutoImportClawHubZipDroppedIntoRuntimeSkills() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        File zip = FileUtil.file(env.appConfig.getRuntime().getSkillsDir(), "demo-claw.zip");
        writeZip(
                zip,
                "demo-claw/SKILL.md",
                skill("demo-claw", "zip skill"),
                "demo-claw/references/info.md",
                "hello");

        List<String> names = env.localSkillService.listSkillNames();

        assertThat(names).contains("demo-claw");
        assertThat(env.localSkillService.viewSkill("demo-claw", "references/info.md").getContent())
                .contains("hello");
    }

    @Test
    void shouldAutoImportClaudeMarketplaceRepoDroppedIntoRuntimeSkills() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        File repo = FileUtil.file(env.appConfig.getRuntime().getSkillsDir(), "marketplace-repo");
        FileUtil.mkdir(FileUtil.file(repo, ".claude-plugin"));
        FileUtil.writeUtf8String(
                "{\"plugins\":[{\"name\":\"review-skill\",\"description\":\"review helper\",\"source\":\"skills/review-skill\"}]}",
                FileUtil.file(repo, ".claude-plugin", "marketplace.json"));
        FileUtil.mkdir(FileUtil.file(repo, "skills", "review-skill"));
        FileUtil.writeUtf8String(
                skill("review-skill", "review helper"),
                FileUtil.file(repo, "skills", "review-skill", "SKILL.md"));

        List<String> names = env.localSkillService.listSkillNames();

        assertThat(names).contains("review-skill");
        assertThat(env.localSkillService.inspect("review-skill")).contains("review helper");
    }

    @Test
    void shouldAutoImportWellKnownExportDroppedIntoRuntimeSkills() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        File export = FileUtil.file(env.appConfig.getRuntime().getSkillsDir(), "site-export");
        FileUtil.mkdir(FileUtil.file(export, "mintlify"));
        FileUtil.writeUtf8String(
                "{\"skills\":[{\"name\":\"mintlify\",\"description\":\"docs helper\",\"files\":[\"SKILL.md\"]}]}",
                FileUtil.file(export, "index.json"));
        FileUtil.writeUtf8String(
                skill("mintlify", "docs helper"), FileUtil.file(export, "mintlify", "SKILL.md"));

        List<String> names = env.localSkillService.listSkillNames();

        assertThat(names).contains("mintlify");
        assertThat(env.localSkillService.inspect("mintlify")).contains("docs helper");
    }

    private void writeZip(File file, String path1, String content1, String path2, String content2)
            throws Exception {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
        try {
            zip.putNextEntry(new ZipEntry(path1));
            zip.write(content1.getBytes("UTF-8"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(path2));
            zip.write(content2.getBytes("UTF-8"));
            zip.closeEntry();
        } finally {
            zip.close();
        }
    }

    private String skill(String name, String description) {
        return "---\nname: "
                + name
                + "\ndescription: "
                + description
                + "\nmetadata:\n  hermes:\n    tags: [imported]\n---\n\n# Steps\n- example\n";
    }
}
