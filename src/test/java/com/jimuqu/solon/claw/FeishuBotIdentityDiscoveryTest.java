package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.solon.claw.support.AttachmentCacheService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class FeishuBotIdentityDiscoveryTest {
    @Test
    void shouldHydrateBotIdentityFromOfficialInterfaces() throws Exception {
        AppConfig.ChannelConfig channelConfig = new AppConfig.ChannelConfig();
        channelConfig.setEnabled(true);
        channelConfig.setAppId("cli_xxx");
        channelConfig.setAppSecret("secret_xxx");

        FeishuChannelAdapter adapter =
                new FeishuChannelAdapter(
                        channelConfig, new AttachmentCacheService(new AppConfig())) {
                    @Override
                    protected Map<String, String> fetchApplicationInfo() {
                        Map<String, String> values = new LinkedHashMap<String, String>();
                        values.put("app_name", "Jimuqu Feishu Bot");
                        return values;
                    }

                    @Override
                    protected Map<String, String> fetchBotInfo() {
                        Map<String, String> values = new LinkedHashMap<String, String>();
                        values.put("bot_name", "Jimuqu Feishu Bot");
                        values.put("bot_open_id", "ou_bot");
                        values.put("bot_user_id", "cli_bot");
                        return values;
                    }

                    public void exposeHydrate() {
                        hydrateBotIdentity();
                    }
                };

        adapter.getClass().getMethod("exposeHydrate").invoke(adapter);

        assertThat(channelConfig.getBotName()).isEqualTo("Jimuqu Feishu Bot");
        assertThat(channelConfig.getBotOpenId()).isEqualTo("ou_bot");
        assertThat(channelConfig.getBotUserId()).isEqualTo("cli_bot");
    }
}
