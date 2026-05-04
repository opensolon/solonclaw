package com.jimuqu.solon.claw.context;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.service.MemoryManager;
import com.jimuqu.solon.claw.core.service.MemoryProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 默认记忆管理器。 */
public class DefaultMemoryManager implements MemoryManager {
    private final List<MemoryProvider> providers;

    public DefaultMemoryManager(List<MemoryProvider> providers) {
        this.providers =
                providers == null
                        ? Collections.<MemoryProvider>emptyList()
                        : new ArrayList<MemoryProvider>(providers);
    }

    @Override
    public String buildSystemPrompt(String sourceKey) throws Exception {
        StringBuilder buffer = new StringBuilder();
        for (MemoryProvider provider : providers) {
            String block = provider.systemPromptBlock(sourceKey);
            if (StrUtil.isBlank(block)) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(block.trim());
        }
        return buffer.toString();
    }

    @Override
    public String prefetch(String sourceKey, String userMessage) throws Exception {
        StringBuilder buffer = new StringBuilder();
        for (MemoryProvider provider : providers) {
            String block = provider.prefetch(sourceKey, userMessage);
            if (StrUtil.isBlank(block)) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(block.trim());
        }
        return buffer.toString();
    }

    @Override
    public void syncTurn(String sourceKey, String userMessage, String assistantMessage)
            throws Exception {
        for (MemoryProvider provider : providers) {
            provider.syncTurn(sourceKey, userMessage, assistantMessage);
        }
    }
}
