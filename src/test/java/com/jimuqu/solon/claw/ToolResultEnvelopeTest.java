package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.ToolResultEnvelope;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

public class ToolResultEnvelopeTest {
    @Test
    void shouldExposeHermesEnvelopeAndLegacySuccessFlag() {
        ONode node =
                ONode.ofJson(
                        ToolResultEnvelope.ok("done")
                                .data("value", "42")
                                .metadata("tool", "config_get")
                                .preview("value=42")
                                .resultRef("/tmp/result.txt")
                                .size(8)
                                .truncated(false)
                                .toJson());

        assertThat(node.get("status").getString()).isEqualTo("success");
        assertThat(node.get("success").getBoolean()).isTrue();
        assertThat(node.get("summary").getString()).isEqualTo("done");
        assertThat(node.get("preview").getString()).isEqualTo("value=42");
        assertThat(node.get("result_ref").getString()).isEqualTo("/tmp/result.txt");
        assertThat(node.get("metadata").get("tool").getString()).isEqualTo("config_get");
        assertThat(node.get("value").getString()).isEqualTo("42");
    }
}
