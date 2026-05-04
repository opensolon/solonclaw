package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.TestEnvironment;
import org.junit.jupiter.api.Test;

public class StorageRepositoryTest {
    @Test
    void shouldPersistAndSearchSessions() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");
        session.setNdjson("hello world");
        session.setTitle("alpha session");
        session.setCompressedSummary("beta summary");
        session.setLastInputTokens(12);
        session.setLastOutputTokens(8);
        session.setLastCacheReadTokens(3);
        session.setLastCacheWriteTokens(2);
        session.setLastTotalTokens(25);
        session.setCumulativeInputTokens(42);
        session.setCumulativeOutputTokens(18);
        session.setCumulativeCacheReadTokens(5);
        session.setCumulativeCacheWriteTokens(4);
        session.setCumulativeTotalTokens(69);
        session.setLastResolvedProvider("openai-responses");
        session.setLastResolvedModel("gpt-5.4");
        env.sessionRepository.save(session);

        SessionRecord stored = env.sessionRepository.findById(session.getSessionId());
        assertThat(stored).isNotNull();
        assertThat(stored.getLastInputTokens()).isEqualTo(12);
        assertThat(stored.getLastOutputTokens()).isEqualTo(8);
        assertThat(stored.getLastCacheReadTokens()).isEqualTo(3);
        assertThat(stored.getLastCacheWriteTokens()).isEqualTo(2);
        assertThat(stored.getLastTotalTokens()).isEqualTo(25);
        assertThat(stored.getCumulativeInputTokens()).isEqualTo(42);
        assertThat(stored.getCumulativeOutputTokens()).isEqualTo(18);
        assertThat(stored.getCumulativeCacheReadTokens()).isEqualTo(5);
        assertThat(stored.getCumulativeCacheWriteTokens()).isEqualTo(4);
        assertThat(stored.getCumulativeTotalTokens()).isEqualTo(69);
        assertThat(stored.getLastResolvedProvider()).isEqualTo("openai-responses");
        assertThat(stored.getLastResolvedModel()).isEqualTo("gpt-5.4");
        assertThat(env.sessionRepository.search("hello", 10)).hasSize(1);
        assertThat(env.sessionRepository.search("alpha", 10)).hasSize(1);
        assertThat(env.sessionRepository.search("beta", 10)).hasSize(1);

        SessionRecord clone =
                env.sessionRepository.cloneSession(
                        "MEMORY:room-a:user-a", session.getSessionId(), "review");
        assertThat(clone.getParentSessionId()).isEqualTo(session.getSessionId());
        assertThat(env.sessionRepository.findBySourceAndBranch("MEMORY:room-a:user-a", "review"))
                .isNotNull();
    }
}
