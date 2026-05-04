package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.AgentRunOutcome;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.support.RuntimeFooterService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class RuntimeFooterServiceTest {
    @Test
    void shouldStaySilentByDefault() {
        AppConfig config = new AppConfig();
        RuntimeFooterService service = new RuntimeFooterService(config);

        assertThat(service.appendFooter("完成", PlatformType.FEISHU, outcome())).isEqualTo("完成");
    }

    @Test
    void shouldRenderConfiguredFieldsWhenEnabled() {
        AppConfig config = new AppConfig();
        config.getDisplay().getRuntimeFooter().setEnabled(true);
        config.getDisplay()
                .getRuntimeFooter()
                .setFields(Arrays.asList("provider", "model", "context_pct", "tokens"));
        RuntimeFooterService service = new RuntimeFooterService(config);

        String rendered = service.appendFooter("完成", PlatformType.FEISHU, outcome());

        assertThat(rendered).contains("—— default · gpt-5.4 · 50% · 123 tokens");
    }

    @Test
    void shouldHonorPlatformOverride() {
        AppConfig config = new AppConfig();
        config.getDisplay().getRuntimeFooter().setEnabled(true);
        config.getChannels().getWeixin().setRuntimeFooterEnabled(Boolean.FALSE);
        RuntimeFooterService service = new RuntimeFooterService(config);

        assertThat(service.appendFooter("完成", PlatformType.WEIXIN, outcome())).isEqualTo("完成");
    }

    private AgentRunOutcome outcome() {
        LlmResult result = new LlmResult();
        result.setTotalTokens(123L);
        AgentRunOutcome outcome = new AgentRunOutcome();
        outcome.setProvider("default");
        outcome.setModel("openai/gpt-5.4");
        outcome.setContextEstimateTokens(1000);
        outcome.setContextWindowTokens(2000);
        outcome.setResult(result);
        outcome.setCwd(System.getProperty("user.dir"));
        return outcome;
    }
}
