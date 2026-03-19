package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.util.StrUtil;

/**
 * 将模型流式输出折叠为“对用户可见的累计正文”。
 */
class VisibleProgressAccumulator {
    /** 最近一次对用户可见的累计正文。 */
    private String visibleText = "";

    /**
     * 追加一段新的流式内容，并返回最新的累计正文。
     *
     * @param chunkContent 新片段
     * @param thinking 是否为思考内容
     * @param toolCalls 是否为工具调用内容
     * @return 若当前没有可见正文则返回 null；否则返回累计正文
     */
    public String append(String chunkContent, boolean thinking, boolean toolCalls) {
        if (thinking || toolCalls || StrUtil.isBlank(chunkContent)) {
            return null;
        }

        String normalizedChunk = normalizeChunk(chunkContent);
        String candidate = mergeVisibleText(visibleText, normalizedChunk);
        if (StrUtil.isBlank(candidate) || StrUtil.equals(candidate, visibleText)) {
            return null;
        }

        visibleText = candidate;
        return visibleText;
    }

    /**
     * 返回当前累计正文。
     *
     * @return 累计正文
     */
    public String getVisibleText() {
        return visibleText;
    }

    /**
     * 预处理单个片段。
     *
     * @param chunkContent 原始片段
     * @return 归一化后的片段
     */
    private String normalizeChunk(String chunkContent) {
        return StrUtil.blankToDefault(chunkContent, "").replace("\r\n", "\n");
    }

    /**
     * 将新的可见片段合并为累计正文。
     *
     * @param current 当前累计正文
     * @param next 新片段
     * @return 合并后的累计正文
     */
    private String mergeVisibleText(String current, String next) {
        String currentValue = StrUtil.blankToDefault(current, "");
        String nextValue = StrUtil.blankToDefault(next, "");

        if (StrUtil.isBlank(currentValue)) {
            return StrUtil.trim(nextValue);
        }

        if (StrUtil.equals(currentValue, StrUtil.trim(nextValue))) {
            return currentValue;
        }

        if (StrUtil.startWith(nextValue, currentValue)) {
            return StrUtil.trim(nextValue);
        }

        if (StrUtil.startWith(StrUtil.trim(nextValue), currentValue)) {
            return StrUtil.trim(nextValue);
        }

        if (StrUtil.startWith(currentValue, StrUtil.trim(nextValue))) {
            return currentValue;
        }

        return StrUtil.trim(currentValue + nextValue);
    }
}
