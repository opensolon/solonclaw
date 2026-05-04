package com.jimuqu.solon.claw.skillhub.support;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.List;

/** bundle 路径安全校验。 */
public final class SkillBundlePathSupport {
    private SkillBundlePathSupport() {}

    public static String normalizeSkillName(String value) {
        return normalize(value, false, "skill name");
    }

    public static String normalizeCategoryName(String value) {
        return normalize(value, false, "category");
    }

    public static String normalizeBundlePath(String value) {
        return normalize(value, true, "bundle path");
    }

    private static String normalize(String value, boolean allowNested, String fieldName) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("Unsafe " + fieldName + ": empty path");
        }
        String normalized = value.trim().replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.startsWith("./")
                || normalized.contains("..")) {
            throw new IllegalStateException("Unsafe " + fieldName + ": " + value);
        }

        String[] rawParts = normalized.split("/");
        List<String> parts = new ArrayList<String>();
        for (String rawPart : rawParts) {
            String part = rawPart.trim();
            if (part.length() == 0 || ".".equals(part)) {
                continue;
            }
            if (part.contains(":")) {
                throw new IllegalStateException("Unsafe " + fieldName + ": " + value);
            }
            parts.add(part);
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException("Unsafe " + fieldName + ": " + value);
        }
        if (!allowNested && parts.size() != 1) {
            throw new IllegalStateException("Unsafe " + fieldName + ": " + value);
        }
        return String.join("/", parts);
    }
}
