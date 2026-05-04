package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.context.SkillCuratorService;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SkillCuratorServiceTest {
    @Test
    void shouldMarkAgentCreatedSkillsAndWriteReportsWithoutDeleting() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getCurator().setEnabled(true);
        env.appConfig.getCurator().setStaleAfterDays(1);
        env.appConfig.getCurator().setArchiveAfterDays(2);
        File staleSkill = createSkill(env, "old-skill", false);
        File pinnedSkill = createSkill(env, "pinned-skill", true);
        long old = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L;
        touch(staleSkill, old);
        touch(pinnedSkill, old);

        SkillCuratorService service = new SkillCuratorService(env.appConfig, env.localSkillService);
        Map<String, Object> report = service.runOnce(true);

        assertThat(report.get("status")).isEqualTo("ok");
        assertThat(new File(env.appConfig.getRuntime().getSkillsDir(), ".curator_state")).isFile();
        assertThat(new File(staleSkill, "SKILL.md")).isFile();
        assertThat(FileUtil.loopFiles(new File(env.appConfig.getRuntime().getLogsDir(), "curator")))
                .extracting(File::getName)
                .contains("run.json", "REPORT.md");
    }

    @Test
    void shouldPauseAndResumeCurator() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SkillCuratorService service = new SkillCuratorService(env.appConfig, env.localSkillService);

        service.pause();
        assertThat(service.runOnce(false).get("status")).isEqualTo("paused");
        service.resume();
        assertThat(service.runOnce(true).get("status")).isEqualTo("ok");
    }

    private File createSkill(TestEnvironment env, String name, boolean pinned) {
        File dir = new File(env.appConfig.getRuntime().getSkillsDir(), name);
        FileUtil.mkdir(dir);
        String frontmatter =
                "---\nname: "
                        + name
                        + "\ndescription: demo\n"
                        + (pinned ? "pinned: true\n" : "")
                        + "---\n\n# Demo\n";
        FileUtil.writeUtf8String(frontmatter, new File(dir, "SKILL.md"));
        return dir;
    }

    private void touch(File dir, long timestamp) {
        for (File file : FileUtil.loopFiles(dir)) {
            file.setLastModified(timestamp);
        }
        dir.setLastModified(timestamp);
    }
}
