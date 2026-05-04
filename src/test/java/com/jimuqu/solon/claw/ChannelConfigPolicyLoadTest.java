package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

public class ChannelConfigPolicyLoadTest {
    @Test
    void shouldLoadChannelPoliciesAndWecomGroupAllowMap() throws Exception {
        File runtimeHome = Files.createTempDirectory("solon-claw-config-policies").toFile();
        File overrideFile = new File(runtimeHome, "config.yml");
        FileUtil.writeUtf8String(
                "solonclaw:\n"
                        + "  channels:\n"
                        + "    feishu:\n"
                        + "      dmPolicy: allowlist\n"
                        + "      groupPolicy: open\n"
                        + "      groupAllowedUsers:\n"
                        + "        - oc_group_a\n"
                        + "      botName: SolonClaw Bot\n"
                        + "    wecom:\n"
                        + "      groups:\n"
                        + "        room-a:\n"
                        + "          allowFrom:\n"
                        + "            - alice\n"
                        + "            - bob\n"
                        + "        '*':\n"
                        + "          allowFrom:\n"
                        + "            - admin\n",
                overrideFile);

        Props props = new Props();
        props.put("solonclaw.runtime.home", runtimeHome.getAbsolutePath());
        props.put("solonclaw.channels.feishu.enabled", "true");

        AppConfig config = AppConfig.load(props);

        assertThat(config.getChannels().getFeishu().getDmPolicy()).isEqualTo("allowlist");
        assertThat(config.getChannels().getFeishu().getGroupPolicy()).isEqualTo("open");
        assertThat(config.getChannels().getFeishu().getGroupAllowedUsers())
                .containsExactly("oc_group_a");
        assertThat(config.getChannels().getFeishu().getBotName()).isEqualTo("SolonClaw Bot");
        assertThat(config.getChannels().getWecom().getGroupMemberAllowedUsers().get("room-a"))
                .containsExactly("alice", "bob");
        assertThat(config.getChannels().getWecom().getGroupMemberAllowedUsers().get("*"))
                .isEqualTo(Arrays.asList("admin"));
    }
}
