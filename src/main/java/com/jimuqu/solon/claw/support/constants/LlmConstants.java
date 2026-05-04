package com.jimuqu.solon.claw.support.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 大模型协议与运行时相关常量。 */
public interface LlmConstants {
    /** OpenAI Chat Completions 协议。 */
    String PROVIDER_OPENAI = "openai";

    /** OpenAI Responses 协议。 */
    String PROVIDER_OPENAI_RESPONSES = "openai-responses";

    /** Ollama 协议。 */
    String PROVIDER_OLLAMA = "ollama";

    /** Gemini 协议。 */
    String PROVIDER_GEMINI = "gemini";

    /** Anthropic 协议。 */
    String PROVIDER_ANTHROPIC = "anthropic";

    /** 支持的 provider 白名单。 */
    List<String> SUPPORTED_PROVIDERS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            PROVIDER_OPENAI,
                            PROVIDER_OPENAI_RESPONSES,
                            PROVIDER_OLLAMA,
                            PROVIDER_GEMINI,
                            PROVIDER_ANTHROPIC));
}
