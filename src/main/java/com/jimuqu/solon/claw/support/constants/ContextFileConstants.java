package com.jimuqu.solon.claw.support.constants;

import cn.hutool.core.util.StrUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 人格工作区上下文文件常量。 */
public final class ContextFileConstants {
    public static final String KEY_AGENTS = "agents";
    public static final String KEY_SOUL = "soul";
    public static final String KEY_IDENTITY = "identity";
    public static final String KEY_USER = "user";
    public static final String KEY_TOOLS = "tools";
    public static final String KEY_HEARTBEAT = "heartbeat";
    public static final String KEY_MEMORY = "memory";
    public static final String KEY_MEMORY_TODAY = "memory_today";

    public static final String FILE_AGENTS = "AGENTS.md";
    public static final String FILE_SOUL = "SOUL.md";
    public static final String FILE_IDENTITY = "IDENTITY.md";
    public static final String FILE_USER = "USER.md";
    public static final String FILE_TOOLS = "TOOLS.md";
    public static final String FILE_HEARTBEAT = "HEARTBEAT.md";
    public static final String FILE_MEMORY = "MEMORY.md";
    public static final String MEMORY_DIR = "memory";

    private static final Map<String, String> FILES_BY_KEY;
    private static final List<String> ORDERED_KEYS;

    static {
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>();
        files.put(KEY_AGENTS, FILE_AGENTS);
        files.put(KEY_SOUL, FILE_SOUL);
        files.put(KEY_IDENTITY, FILE_IDENTITY);
        files.put(KEY_USER, FILE_USER);
        files.put(KEY_TOOLS, FILE_TOOLS);
        files.put(KEY_HEARTBEAT, FILE_HEARTBEAT);
        files.put(KEY_MEMORY, FILE_MEMORY);
        FILES_BY_KEY = Collections.unmodifiableMap(files);
        ArrayList<String> ordered = new ArrayList<String>(files.keySet());
        ordered.add(KEY_MEMORY_TODAY);
        ORDERED_KEYS = Collections.unmodifiableList(ordered);
    }

    private ContextFileConstants() {}

    /** 返回受控文件 key 顺序。 */
    public static List<String> orderedKeys() {
        return ORDERED_KEYS;
    }

    /** 判断是否为受控文件 key。 */
    public static boolean isManagedKey(String key) {
        String normalized = normalizeKey(key);
        return FILES_BY_KEY.containsKey(normalized) || KEY_MEMORY_TODAY.equals(normalized);
    }

    /** 解析 key 对应文件名。 */
    public static String fileName(String key) {
        String normalized = normalizeKey(key);
        if (KEY_MEMORY_TODAY.equals(normalized)) {
            return dailyMemoryRelativePath(LocalDate.now());
        }
        String fileName = FILES_BY_KEY.get(normalized);
        if (fileName == null) {
            throw new IllegalArgumentException("Unsupported context file key: " + key);
        }
        return fileName;
    }

    /** 归一化文件 key。 */
    public static String normalizeKey(String key) {
        return StrUtil.nullToEmpty(key).trim().toLowerCase();
    }

    public static String dailyMemoryRelativePath(LocalDate date) {
        return MEMORY_DIR + "/" + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
    }
}
