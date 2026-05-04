package com.jimuqu.solon.claw.skillhub.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.service.SkillGuardService;
import com.jimuqu.solon.claw.skillhub.model.Finding;
import com.jimuqu.solon.claw.skillhub.model.InstallDecision;
import com.jimuqu.solon.claw.skillhub.model.ScanResult;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Java 版技能静态扫描器。 */
public class DefaultSkillGuardService implements SkillGuardService {
    private static final int MAX_FILE_COUNT = 50;
    private static final long MAX_TOTAL_SIZE_KB = 1024L;
    private static final long MAX_SINGLE_FILE_KB = 256L;

    private static final Set<String> TRUSTED_REPOS =
            new LinkedHashSet<String>(
                    java.util.Arrays.asList("openai/skills", "anthropics/skills"));

    private static final List<ThreatPattern> THREAT_PATTERNS =
            java.util.Arrays.asList(
                    new ThreatPattern(
                            "hardcoded_secret",
                            "critical",
                            "credential_exposure",
                            "(api[_-]?key|token|secret|password)\\s*[=:]\\s*[\"'][A-Za-z0-9+/=_-]{20,}",
                            "possible hardcoded secret"),
                    new ThreatPattern(
                            "embedded_private_key",
                            "critical",
                            "credential_exposure",
                            "-----BEGIN\\s+(RSA\\s+)?PRIVATE\\s+KEY-----",
                            "embedded private key"),
                    new ThreatPattern(
                            "curl_pipe_shell",
                            "critical",
                            "supply_chain",
                            "curl\\s+[^\\n]*\\|\\s*(ba)?sh",
                            "curl piped to shell"),
                    new ThreatPattern(
                            "wget_pipe_shell",
                            "critical",
                            "supply_chain",
                            "wget\\s+[^\\n]*-O\\s*-\\s*\\|\\s*(ba)?sh",
                            "wget piped to shell"),
                    new ThreatPattern(
                            "dangerous_rm",
                            "critical",
                            "destructive",
                            "rm\\s+-rf\\s+/",
                            "recursive delete from root"),
                    new ThreatPattern(
                            "sudo_usage",
                            "high",
                            "privilege_escalation",
                            "\\bsudo\\b",
                            "uses sudo"),
                    new ThreatPattern(
                            "python_subprocess",
                            "medium",
                            "execution",
                            "subprocess\\.(run|call|Popen|check_output)\\s*\\(",
                            "Python subprocess execution"),
                    new ThreatPattern(
                            "python_os_system",
                            "high",
                            "execution",
                            "os\\.system\\s*\\(",
                            "os.system shell execution"),
                    new ThreatPattern(
                            "prompt_injection_ignore",
                            "critical",
                            "injection",
                            "ignore\\s+(?:\\w+\\s+)*(previous|all|above|prior)\\s+instructions",
                            "prompt injection ignore instructions"),
                    new ThreatPattern(
                            "role_hijack",
                            "high",
                            "injection",
                            "you\\s+are\\s+(?:\\w+\\s+)*now\\s+",
                            "role hijack"),
                    new ThreatPattern(
                            "shell_rc_mod",
                            "medium",
                            "persistence",
                            "\\.(bashrc|zshrc|profile|bash_profile|bash_login|zprofile|zlogin)\\b",
                            "references shell startup file"),
                    new ThreatPattern(
                            "agent_config_mod",
                            "critical",
                            "persistence",
                            "AGENTS\\.md|CLAUDE\\.md|\\.cursorrules|\\.clinerules",
                            "references agent config files"));

    @Override
    public ScanResult scanSkill(File skillPath, String source) throws Exception {
        ScanResult result = new ScanResult();
        result.setSkillName(skillPath.getName());
        result.setSource(source);
        result.setTrustLevel(resolveTrustLevel(source));
        result.setScannedAt(DateUtil.now());

        List<Finding> findings = new ArrayList<Finding>();
        if (skillPath.isDirectory()) {
            findings.addAll(checkStructure(skillPath));
            for (File file : FileUtil.loopFiles(skillPath)) {
                if (file.isDirectory()) {
                    continue;
                }
                findings.addAll(scanFile(skillPath, file));
            }
        } else if (skillPath.isFile()) {
            findings.addAll(scanFile(skillPath.getParentFile(), skillPath));
        }

        result.setFindings(findings);
        result.setVerdict(determineVerdict(findings));
        result.setSummary(buildSummary(result));
        return result;
    }

    @Override
    public InstallDecision shouldAllowInstall(ScanResult result, boolean force) {
        InstallDecision decision = new InstallDecision();
        String trustLevel = result.getTrustLevel();
        String verdict = result.getVerdict();

        if ("builtin".equals(trustLevel)) {
            decision.setAllowed(true);
            decision.setReason("Allowed builtin source");
            return decision;
        }

        if ("dangerous".equals(verdict)) {
            decision.setAllowed(false);
            decision.setReason("Blocked dangerous verdict");
            return decision;
        }

        if ("trusted".equals(trustLevel)) {
            decision.setAllowed(true);
            decision.setReason("Allowed trusted source");
            return decision;
        }

        if (force && "caution".equals(verdict)) {
            decision.setAllowed(true);
            decision.setReason("Force installed despite caution verdict");
            return decision;
        }

        if ("community".equals(trustLevel) && "caution".equals(verdict)) {
            decision.setAllowed(false);
            decision.setRequiresConfirmation(true);
            decision.setReason("Community source with findings requires confirmation");
            return decision;
        }

        decision.setAllowed(true);
        decision.setReason("Allowed");
        return decision;
    }

    @Override
    public String formatReport(ScanResult result) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("Scan: ")
                .append(result.getSkillName())
                .append(" (")
                .append(result.getSource())
                .append("/")
                .append(result.getTrustLevel())
                .append(")")
                .append(" Verdict: ")
                .append(result.getVerdict().toUpperCase(Locale.ROOT));
        for (Finding finding : result.getFindings()) {
            buffer.append('\n')
                    .append("  ")
                    .append(
                            StrUtil.padAfter(
                                    finding.getSeverity().toUpperCase(Locale.ROOT), 8, ' '))
                    .append(" ")
                    .append(StrUtil.padAfter(finding.getCategory(), 14, ' '))
                    .append(" ")
                    .append(finding.getFile())
                    .append(":")
                    .append(finding.getLine())
                    .append(" \"")
                    .append(finding.getMatch())
                    .append("\"");
        }
        InstallDecision decision = shouldAllowInstall(result, false);
        buffer.append('\n')
                .append("Decision: ")
                .append(
                        decision.isAllowed()
                                ? "ALLOWED"
                                : decision.isRequiresConfirmation()
                                        ? "NEEDS CONFIRMATION"
                                        : "BLOCKED")
                .append(" - ")
                .append(decision.getReason());
        return buffer.toString();
    }

    private List<Finding> checkStructure(File skillDir) {
        List<Finding> findings = new ArrayList<Finding>();
        List<File> files = FileUtil.loopFiles(skillDir);
        long totalSize = 0L;
        int fileCount = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            fileCount++;
            totalSize += file.length();
            String rel = relativePath(skillDir, file);
            if (file.length() > MAX_SINGLE_FILE_KB * 1024L) {
                findings.add(
                        finding(
                                "oversized_file",
                                "medium",
                                "structural",
                                rel,
                                0,
                                (file.length() / 1024L) + "KB",
                                "single file too large"));
            }
            String ext = FileUtil.extName(file).toLowerCase(Locale.ROOT);
            if (java.util.Arrays.asList("exe", "dll", "so", "dylib", "bin", "msi", "dmg")
                    .contains(ext)) {
                findings.add(
                        finding(
                                "binary_file",
                                "critical",
                                "structural",
                                rel,
                                0,
                                ext,
                                "binary file in skill"));
            }
        }

        if (fileCount > MAX_FILE_COUNT) {
            findings.add(
                    finding(
                            "too_many_files",
                            "medium",
                            "structural",
                            "(directory)",
                            0,
                            String.valueOf(fileCount),
                            "too many files"));
        }
        if (totalSize > MAX_TOTAL_SIZE_KB * 1024L) {
            findings.add(
                    finding(
                            "oversized_skill",
                            "high",
                            "structural",
                            "(directory)",
                            0,
                            (totalSize / 1024L) + "KB",
                            "skill too large"));
        }
        return findings;
    }

    private List<Finding> scanFile(File root, File file) throws Exception {
        List<Finding> findings = new ArrayList<Finding>();
        String content = FileUtil.readString(file, StandardCharsets.UTF_8);
        String[] lines = content.split("\\R");
        Set<String> dedupe = new LinkedHashSet<String>();
        for (ThreatPattern pattern : THREAT_PATTERNS) {
            for (int i = 0; i < lines.length; i++) {
                if (pattern.matches(lines[i])) {
                    String key = pattern.getPatternId() + "#" + i;
                    if (dedupe.add(key)) {
                        findings.add(
                                finding(
                                        pattern.getPatternId(),
                                        pattern.getSeverity(),
                                        pattern.getCategory(),
                                        relativePath(root, file),
                                        i + 1,
                                        trim(lines[i], 120),
                                        pattern.getDescription()));
                    }
                }
            }
        }
        return findings;
    }

    private String determineVerdict(List<Finding> findings) {
        boolean hasCritical = false;
        boolean hasHigh = false;
        for (Finding finding : findings) {
            if ("critical".equals(finding.getSeverity())) {
                hasCritical = true;
            }
            if ("high".equals(finding.getSeverity())) {
                hasHigh = true;
            }
        }
        if (hasCritical) {
            return "dangerous";
        }
        if (hasHigh || !findings.isEmpty()) {
            return "caution";
        }
        return "safe";
    }

    private String resolveTrustLevel(String source) {
        if (StrUtil.isBlank(source)) {
            return "community";
        }
        if ("agent-created".equals(source)) {
            return "agent-created";
        }
        if ("official".equals(source) || source.startsWith("official/")) {
            return "builtin";
        }
        for (String trusted : TRUSTED_REPOS) {
            if (source.equals(trusted) || source.startsWith(trusted)) {
                return "trusted";
            }
        }
        return "community";
    }

    private String buildSummary(ScanResult result) {
        if (result.getFindings().isEmpty()) {
            return result.getSkillName() + ": clean scan, no threats detected";
        }
        Set<String> categories = new LinkedHashSet<String>();
        for (Finding finding : result.getFindings()) {
            categories.add(finding.getCategory());
        }
        return result.getSkillName()
                + ": "
                + result.getVerdict()
                + " - "
                + result.getFindings().size()
                + " finding(s) in "
                + String.join(", ", categories);
    }

    private Finding finding(
            String patternId,
            String severity,
            String category,
            String file,
            int line,
            String match,
            String description) {
        Finding finding = new Finding();
        finding.setPatternId(patternId);
        finding.setSeverity(severity);
        finding.setCategory(category);
        finding.setFile(file);
        finding.setLine(line);
        finding.setMatch(match);
        finding.setDescription(description);
        return finding;
    }

    private String relativePath(File root, File file) {
        String base = root.getAbsolutePath() + File.separator;
        String absolute = file.getAbsolutePath();
        return absolute.startsWith(base)
                ? absolute.substring(base.length()).replace(File.separatorChar, '/')
                : file.getName();
    }

    private String trim(String line, int maxLength) {
        String normalized = StrUtil.nullToEmpty(line).trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private static class ThreatPattern {
        private final String patternId;
        private final String severity;
        private final String category;
        private final Pattern pattern;
        private final String description;

        private ThreatPattern(
                String patternId,
                String severity,
                String category,
                String regex,
                String description) {
            this.patternId = patternId;
            this.severity = severity;
            this.category = category;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.description = description;
        }

        private boolean matches(String line) {
            return pattern.matcher(StrUtil.nullToEmpty(line)).find();
        }

        private String getPatternId() {
            return patternId;
        }

        private String getSeverity() {
            return severity;
        }

        private String getCategory() {
            return category;
        }

        private String getDescription() {
            return description;
        }
    }
}
