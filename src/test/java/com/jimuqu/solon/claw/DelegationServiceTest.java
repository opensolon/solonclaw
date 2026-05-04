package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.DelegationResult;
import com.jimuqu.solon.claw.core.model.DelegationTask;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.support.FakeLlmGateway;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DelegationServiceTest {
    @Test
    void shouldKeepParentBindingIsolatedWhenDelegating() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord parent = env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");

        DelegationResult result =
                env.delegationService.delegateSingle("MEMORY:room-a:user-a", "sub task", "ctx");

        assertThat(result.getContent()).contains("echo:");
        assertThat(result.getSessionId()).isNotBlank();
        assertThat(env.sessionRepository.getBoundSession("MEMORY:room-a:user-a").getSessionId())
                .isEqualTo(parent.getSessionId());
        assertThat(env.sessionRepository.findById(result.getSessionId()).getParentSessionId())
                .isEqualTo(parent.getSessionId());
    }

    @Test
    void shouldSupportBatchDelegation() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord parent = env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");
        DelegationTask first = new DelegationTask();
        first.setName("one");
        first.setPrompt("task one");
        DelegationTask second = new DelegationTask();
        second.setName("two");
        second.setPrompt("task two");

        List<DelegationResult> results =
                env.delegationService.delegateBatch(
                        "MEMORY:room-a:user-a", Arrays.asList(first, second));
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getContent()).contains("echo:");
        assertThat(results.get(1).getContent()).contains("echo:");
        assertThat(
                        env.sessionRepository
                                .findById(results.get(0).getSessionId())
                                .getParentSessionId())
                .isEqualTo(parent.getSessionId());
        assertThat(
                        env.sessionRepository
                                .findById(results.get(1).getSessionId())
                                .getParentSessionId())
                .isEqualTo(parent.getSessionId());
    }

    @Test
    void shouldIsolateBatchFailuresWithoutBreakingSuccessfulChildren() throws Exception {
        LlmGateway failingGateway =
                new FakeLlmGateway() {
                    @Override
                    public LlmResult chat(
                            SessionRecord session,
                            String systemPrompt,
                            String userMessage,
                            List<Object> toolObjects)
                            throws Exception {
                        if (userMessage.contains("must fail")) {
                            throw new IllegalStateException("simulated delegation failure");
                        }
                        return super.chat(session, systemPrompt, userMessage, toolObjects);
                    }
                };
        TestEnvironment env = TestEnvironment.withLlm(failingGateway);
        env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");

        DelegationTask first = new DelegationTask();
        first.setName("ok");
        first.setPrompt("task ok");
        DelegationTask second = new DelegationTask();
        second.setName("fail");
        second.setPrompt("must fail");

        List<DelegationResult> results =
                env.delegationService.delegateBatch(
                        "MEMORY:room-a:user-a", Arrays.asList(first, second));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isError()).isFalse();
        assertThat(results.get(0).getContent()).contains("echo:");
        assertThat(results.get(1).isError()).isTrue();
        assertThat(results.get(1).getContent()).contains("simulated delegation failure");
    }
}
