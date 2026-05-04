package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.llm.LlmErrorClassifier;
import org.junit.jupiter.api.Test;

public class LlmErrorClassifierTest {
    @Test
    void shouldClassifyBillingBalanceErrorsAsFallbackOnly() {
        LlmErrorClassifier.ClassifiedError result =
                LlmErrorClassifier.classify(
                        new IllegalStateException(
                                "HTTP 402 insufficient balance, please top up your credits"));

        assertThat(result.getReason()).isEqualTo(LlmErrorClassifier.FailoverReason.BILLING);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.isShouldFallback()).isTrue();
    }

    @Test
    void shouldTreatTransientUsageLimitAsRetryableRateLimit() {
        LlmErrorClassifier.ClassifiedError result =
                LlmErrorClassifier.classify(
                        new IllegalStateException(
                                "status=402 usage limit reached, resets at next window, please retry"));

        assertThat(result.getReason()).isEqualTo(LlmErrorClassifier.FailoverReason.RATE_LIMIT);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.isShouldFallback()).isTrue();
    }

    @Test
    void shouldRequestCompressionForContextOverflow() {
        LlmErrorClassifier.ClassifiedError result =
                LlmErrorClassifier.classify(
                        new IllegalArgumentException(
                                "prompt exceeds max length: context window exceeded"));

        assertThat(result.getReason())
                .isEqualTo(LlmErrorClassifier.FailoverReason.CONTEXT_OVERFLOW);
        assertThat(result.isShouldCompress()).isTrue();
    }

    @Test
    void shouldNotRetryDeterministicBadRequestErrors() {
        LlmErrorClassifier.ClassifiedError result =
                LlmErrorClassifier.classify(
                        new IllegalArgumentException(
                                "HTTP 400 invalid_request: unsupported parameter 'reasoning'"));

        assertThat(result.getReason()).isEqualTo(LlmErrorClassifier.FailoverReason.UNKNOWN);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.isShouldFallback()).isTrue();
    }

    @Test
    void shouldOnlyRetryUnknownErrorsWhenTransientPatternMatches() {
        LlmErrorClassifier.ClassifiedError unknown =
                LlmErrorClassifier.classify(new IllegalStateException("provider rejected request"));
        LlmErrorClassifier.ClassifiedError transientError =
                LlmErrorClassifier.classify(new IllegalStateException("connection reset by peer"));

        assertThat(unknown.isRetryable()).isFalse();
        assertThat(transientError.isRetryable()).isTrue();
    }
}
