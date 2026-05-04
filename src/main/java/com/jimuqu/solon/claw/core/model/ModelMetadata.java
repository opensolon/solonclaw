package com.jimuqu.solon.claw.core.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hermes 对齐的模型能力元数据。 */
@Getter
@Setter
@NoArgsConstructor
public class ModelMetadata {
    private String provider;
    private String model;
    private String dialect;
    private List<String> aliases = new ArrayList<String>();
    private int contextWindow;
    private int maxOutput;
    private boolean supportsTools;
    private boolean supportsReasoning;
    private boolean supportsPromptCache;
    private boolean supportsStreaming;
    private boolean defaultModel;
    private boolean supported;
}
