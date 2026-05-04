package com.jimuqu.solon.claw.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SkillDescriptor;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;

/** Hermes Curator 对齐的技能后台维护器。 */
@RequiredArgsConstructor
public class SkillCuratorService {
    private final AppConfig appConfig;
    private final LocalSkillService localSkillService;

    public synchronized Map<String, Object> runOnce(boolean force) throws Exception {
        Map<String, Object> state = readState();
        long now = System.currentTimeMillis();
        if (Boolean.TRUE.equals(state.get("paused")) && !force) {
            return report(state, now, "paused", new ArrayList<Map<String, Object>>());
        }
        if (!force && !appConfig.getCurator().isEnabled()) {
            return report(state, now, "disabled", new ArrayList<Map<String, Object>>());
        }
        long lastRunAt = asLong(state.get("lastRunAt"));
        long intervalMillis =
                Math.max(1, appConfig.getCurator().getIntervalHours()) * 60L * 60L * 1000L;
        if (!force && lastRunAt > 0 && now - lastRunAt < intervalMillis) {
            return report(state, now, "interval_wait", new ArrayList<Map<String, Object>>());
        }

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Map<String, Object> skillsState = ensureMap(state, "skills");
        for (SkillDescriptor descriptor : localSkillService.listSkills(null)) {
            if (!"agent-created".equals(descriptor.getTrustLevel())) {
                continue;
            }
            Map<String, Object> item = reviewSkill(descriptor, skillsState, now);
            items.add(item);
        }
        items.sort(
                new java.util.Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> left, Map<String, Object> right) {
                        return Long.compare(
                                asLong(right.get("usageScore")), asLong(left.get("usageScore")));
                    }
                });

        state.put("lastRunAt", Long.valueOf(now));
        writeState(state);
        return report(state, now, "ok", items);
    }

    public synchronized void pause() {
        Map<String, Object> state = readState();
        state.put("paused", Boolean.TRUE);
        writeState(state);
    }

    public synchronized void resume() {
        Map<String, Object> state = readState();
        state.put("paused", Boolean.FALSE);
        writeState(state);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reviewSkill(
            SkillDescriptor descriptor, Map<String, Object> skillsState, long now) {
        String name = descriptor.canonicalName();
        Map<String, Object> record =
                skillsState.get(name) instanceof Map
                        ? (Map<String, Object>) skillsState.get(name)
                        : new LinkedHashMap<String, Object>();
        long touchedAt = lastTouchedAt(FileUtil.file(descriptor.getSkillDir()));
        long ageDays = Math.max(0L, (now - touchedAt) / (24L * 60L * 60L * 1000L));
        boolean pinned = isPinned(descriptor);
        long loadCount = asLong(record.get("loadCount"));
        long callCount = asLong(record.get("callCount"));
        long usageScore = loadCount * 3L + callCount;
        String previousStatus =
                StrUtil.nullToDefault(String.valueOf(record.get("status")), "active");
        String status = previousStatus;
        String action = "unchanged";
        String archiveKind = "";
        List<String> suggestions = new ArrayList<String>();
        if (pinned) {
            status = "pinned";
            action = "skipped_pinned";
        } else if (ageDays >= appConfig.getCurator().getArchiveAfterDays()) {
            status = "archived";
            archiveKind = usageScore <= 0 ? "pruned" : "consolidated";
            action = "marked_" + archiveKind;
            suggestions.add(archiveKind + ": archive candidate");
        } else if (ageDays >= appConfig.getCurator().getStaleAfterDays()) {
            status = "stale";
            action = "marked_stale";
            suggestions.add("stale: refresh or verify against current project behavior");
        } else {
            status = "active";
        }
        List<String> contentFlags = inspectContentFlags(descriptor);
        suggestions.addAll(contentFlags);

        record.put("status", status);
        record.put("lastSeenAt", Long.valueOf(now));
        record.put("lastTouchedAt", Long.valueOf(touchedAt));
        record.put("ageDays", Long.valueOf(ageDays));
        record.put("pinned", Boolean.valueOf(pinned));
        record.put("archiveKind", archiveKind);
        record.put("usageScore", Long.valueOf(usageScore));
        record.put("loadCount", Long.valueOf(loadCount));
        record.put("callCount", Long.valueOf(callCount));
        record.put("suggestions", suggestions);
        skillsState.put(name, record);

        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", name);
        item.put("status", status);
        item.put("previousStatus", previousStatus);
        item.put("action", action);
        item.put("ageDays", Long.valueOf(ageDays));
        item.put("pinned", Boolean.valueOf(pinned));
        item.put("archiveKind", archiveKind);
        item.put("usageScore", Long.valueOf(usageScore));
        item.put("loadCount", Long.valueOf(loadCount));
        item.put("callCount", Long.valueOf(callCount));
        item.put("suggestions", suggestions);
        item.put("path", descriptor.getSkillDir());
        return item;
    }

    public synchronized Map<String, Object> applySuggestion(String skillName, String suggestion) {
        return recordSuggestionState(skillName, suggestion, "applied");
    }

    public synchronized Map<String, Object> ignoreSuggestion(String skillName, String suggestion) {
        return recordSuggestionState(skillName, suggestion, "ignored");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> recordSuggestionState(
            String skillName, String suggestion, String status) {
        Map<String, Object> state = readState();
        Map<String, Object> audit = ensureMap(state, "suggestionAudit");
        List<Map<String, Object>> rows =
                audit.get(skillName) instanceof List
                        ? (List<Map<String, Object>>) audit.get(skillName)
                        : new ArrayList<Map<String, Object>>();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("suggestion", suggestion);
        row.put("status", status);
        row.put("at", Long.valueOf(System.currentTimeMillis()));
        rows.add(row);
        audit.put(skillName, rows);
        writeState(state);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("skill", skillName);
        result.put("suggestion", suggestion);
        result.put("status", status);
        return result;
    }

    private List<String> inspectContentFlags(SkillDescriptor descriptor) {
        List<String> flags = new ArrayList<String>();
        try {
            String content =
                    FileUtil.readUtf8String(
                            FileUtil.file(descriptor.getSkillDir(), "SKILL.md"));
            String normalized = StrUtil.nullToEmpty(content).toLowerCase();
            if (normalized.contains("todo") || normalized.contains("待补充")) {
                flags.add("hollow: contains TODO/待补充");
            }
            if (normalized.contains("deprecated") || normalized.contains("过期")) {
                flags.add("stale_content: marked deprecated/过期");
            }
            if (normalized.indexOf("冲突") >= 0 || normalized.contains("conflict")) {
                flags.add("conflict: conflict marker text present");
            }
            if (content.length() < 300) {
                flags.add("hollow: content is too short");
            }
        } catch (Exception ignored) {
        }
        return flags;
    }

    private Map<String, Object> report(
            Map<String, Object> state, long now, String status, List<Map<String, Object>> items) {
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("status", status);
        report.put("startedAt", Long.valueOf(now));
        report.put("finishedAt", Long.valueOf(System.currentTimeMillis()));
        report.put("items", items);
        report.put("stateFile", stateFile().getAbsolutePath());
        writeReport(report, now);
        return report;
    }

    private void writeReport(Map<String, Object> report, long now) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(now));
        File runDir = FileUtil.file(appConfig.getRuntime().getLogsDir(), "curator", stamp);
        FileUtil.mkdir(runDir);
        FileUtil.writeUtf8String(ONode.serialize(report), FileUtil.file(runDir, "run.json"));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Curator Report\n\n");
        markdown.append("- status: ").append(report.get("status")).append('\n');
        markdown.append("- items: ").append(((List<?>) report.get("items")).size()).append("\n\n");
        for (Object itemObj : (List<?>) report.get("items")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) itemObj;
            markdown.append("- ")
                    .append(item.get("name"))
                    .append(" -> ")
                    .append(item.get("status"))
                    .append(" (")
                    .append(item.get("action"))
                    .append(")\n");
        }
        FileUtil.writeUtf8String(markdown.toString(), FileUtil.file(runDir, "REPORT.md"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readState() {
        File stateFile = stateFile();
        if (!stateFile.isFile()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Object parsed = ONode.deserialize(FileUtil.readUtf8String(stateFile), Object.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception ignored) {
        }
        return new LinkedHashMap<String, Object>();
    }

    private void writeState(Map<String, Object> state) {
        File file = stateFile();
        FileUtil.mkParentDirs(file);
        FileUtil.writeUtf8String(ONode.serialize(state), file);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureMap(Map<String, Object> state, String key) {
        Object current = state.get(key);
        if (current instanceof Map) {
            return (Map<String, Object>) current;
        }
        Map<String, Object> created = new LinkedHashMap<String, Object>();
        state.put(key, created);
        return created;
    }

    private File stateFile() {
        return FileUtil.file(appConfig.getRuntime().getSkillsDir(), ".curator_state");
    }

    private long lastTouchedAt(File dir) {
        long latest = dir == null ? 0L : dir.lastModified();
        if (dir != null && dir.exists()) {
            for (File file : FileUtil.loopFiles(dir)) {
                latest = Math.max(latest, file.lastModified());
            }
        }
        return latest <= 0 ? System.currentTimeMillis() : latest;
    }

    @SuppressWarnings("unchecked")
    private boolean isPinned(SkillDescriptor descriptor) {
        Map<String, Object> metadata = descriptor.getMetadata();
        if (metadata == null) {
            return false;
        }
        if (asBoolean(metadata.get("pinned"))) {
            return true;
        }
        Object curator = metadata.get("curator");
        if (curator instanceof Map && asBoolean(((Map<String, Object>) curator).get("pinned"))) {
            return true;
        }
        Object hermes = metadata.get("hermes");
        return hermes instanceof Map && asBoolean(((Map<String, Object>) hermes).get("pinned"));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
