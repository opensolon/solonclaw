package com.jimuqu.solon.claw.support.constants;

/** 上下文压缩相关常量。 */
public interface CompressionConstants {
    /** 压缩摘要前缀。 */
    String SUMMARY_PREFIX = "[CONTEXT COMPACTION]";

    /** 被裁剪的旧工具输出占位文本。 */
    String PRUNED_TOOL_PLACEHOLDER = "[Old tool output cleared to save context space]";

    /** 默认压缩阈值，占上下文窗口的百分比。 */
    double DEFAULT_THRESHOLD_PERCENT = 0.50D;

    /** 默认尾部保护比例。 */
    double DEFAULT_TAIL_RATIO = 0.20D;

    /** 默认 head 保护消息数。 */
    int DEFAULT_PROTECT_HEAD_MESSAGES = 3;

    /** 估算字符到 token 的粗略倍率。 */
    int CHARS_PER_TOKEN = 4;

    /** 旧摘要注入到新摘要时的最大保留长度。 */
    int MAX_PREVIOUS_SUMMARY_LENGTH = 400;

    /** 单次结构化摘要的最大长度，避免反复压缩后摘要自身膨胀。 */
    int MAX_SUMMARY_LENGTH = 2400;

    /** 会话标题最大长度。 */
    int MAX_TITLE_LENGTH = 80;

    /** 压缩失败后的冷却时间，单位毫秒。 */
    long FAILURE_COOLDOWN_MILLIS = 10L * 60L * 1000L;

    /** 成功压缩后的最短重压缩间隔，单位毫秒。 */
    long RECOMPRESS_COOLDOWN_MILLIS = 60L * 1000L;

    /** 再次压缩前至少新增的估算 token。 */
    int MIN_RECOMPRESS_DELTA_TOKENS = 512;
}
