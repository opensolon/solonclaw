package com.jimuqu.solon.claw.skillhub.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import com.jimuqu.solon.claw.skillhub.model.SkillSetupState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** SKILL.md frontmatter 解析辅助。 */
public final class SkillFrontmatterSupport {
    private SkillFrontmatterSupport() {}

    public static Map<String, Object> parseFrontmatter(String content) {
        if (StrUtil.isBlank(content) || !content.startsWith("---")) {
            return Collections.emptyMap();
        }

        String[] lines = content.split("\\R");
        StringBuilder yamlBlock = new StringBuilder();
        boolean closed = false;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                closed = true;
                break;
            }
            yamlBlock.append(lines[i]).append('\n');
        }
        if (!closed) {
            return Collections.emptyMap();
        }

        Object parsed = new Yaml().load(yamlBlock.toString());
        if (!(parsed instanceof Map)) {
            return Collections.emptyMap();
        }
        return sanitizeMap((Map<?, ?>) parsed);
    }

    public static List<String> parseStringList(Object value) {
        if (value instanceof List) {
            List<String> results = new ArrayList<String>();
            for (Object item : (List<?>) value) {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    results.add(String.valueOf(item).trim());
                }
            }
            return results;
        }
        if (value instanceof String && StrUtil.isNotBlank((String) value)) {
            List<String> results = new ArrayList<String>();
            results.add(((String) value).trim());
            return results;
        }
        return Collections.emptyList();
    }

    public static String resolveDescription(Map<String, Object> frontmatter, String fallback) {
        Object description = frontmatter.get("description");
        if (description != null && StrUtil.isNotBlank(String.valueOf(description))) {
            return String.valueOf(description).trim();
        }
        return StrUtil.blankToDefault(fallback, "");
    }

    public static String resolveName(Map<String, Object> frontmatter, String fallback) {
        Object name = frontmatter.get("name");
        if (name != null && StrUtil.isNotBlank(String.valueOf(name))) {
            return String.valueOf(name).trim();
        }
        return fallback;
    }

    public static List<String> resolveTags(Map<String, Object> frontmatter) {
        List<String> tags = parseStringList(frontmatter.get("tags"));
        if (!tags.isEmpty()) {
            return tags;
        }
        Map<String, Object> hermes = getHermesMetadata(frontmatter);
        return parseStringList(hermes.get("tags"));
    }

    public static Map<String, Object> getHermesMetadata(Map<String, Object> frontmatter) {
        Object metadata = frontmatter.get("metadata");
        if (!(metadata instanceof Map)) {
            return Collections.emptyMap();
        }
        Object hermes = ((Map<?, ?>) metadata).get("hermes");
        if (!(hermes instanceof Map)) {
            return Collections.emptyMap();
        }
        return sanitizeMap((Map<?, ?>) hermes);
    }

    public static SkillSetupState resolveSetupState(Map<String, Object> frontmatter) {
        List<String> platforms = parseStringList(frontmatter.get("platforms"));
        if (!platforms.isEmpty()) {
            String os = currentPlatform();
            boolean match = false;
            for (String platform : platforms) {
                if (os.equalsIgnoreCase(platform)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                return SkillSetupState.UNSUPPORTED;
            }
        }

        Object required = frontmatter.get("required_environment_variables");
        if (required instanceof List) {
            for (Object item : (List<?>) required) {
                if (item instanceof Map) {
                    Object name = ((Map<?, ?>) item).get("name");
                    if (name != null
                            && StrUtil.isBlank(
                                    RuntimeConfigResolver.getValue(String.valueOf(name)))) {
                        return SkillSetupState.SETUP_NEEDED;
                    }
                }
            }
        }

        Map<String, Object> prerequisites = getMap(frontmatter, "prerequisites");
        List<String> envVars = parseStringList(prerequisites.get("env_vars"));
        for (String envVar : envVars) {
            if (StrUtil.isBlank(RuntimeConfigResolver.getValue(envVar))) {
                return SkillSetupState.SETUP_NEEDED;
            }
        }

        return SkillSetupState.AVAILABLE;
    }

    public static Map<String, Object> getMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Map)) {
            return Collections.emptyMap();
        }
        return sanitizeMap((Map<?, ?>) value);
    }

    private static String currentPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("mac")) {
            return "macos";
        }
        return "linux";
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> input) {
        Map<String, Object> output = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            output.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue()));
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value) {
        if (value instanceof Map) {
            return sanitizeMap((Map<?, ?>) value);
        }
        if (value instanceof List) {
            List<Object> results = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                results.add(sanitizeValue(item));
            }
            return results;
        }
        return value;
    }
}
