package com.jimuqu.solon.claw.skillhub.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.service.SkillGuardService;
import com.jimuqu.solon.claw.core.service.SkillHubService;
import com.jimuqu.solon.claw.core.service.SkillImportService;
import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.ScanResult;
import com.jimuqu.solon.claw.skillhub.model.SkillBrowseResult;
import com.jimuqu.solon.claw.skillhub.model.SkillBundle;
import com.jimuqu.solon.claw.skillhub.model.SkillMeta;
import com.jimuqu.solon.claw.skillhub.model.TapRecord;
import com.jimuqu.solon.claw.skillhub.source.ClaudeMarketplaceSkillSource;
import com.jimuqu.solon.claw.skillhub.source.ClawHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.GitHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.HermesIndexSource;
import com.jimuqu.solon.claw.skillhub.source.LobeHubSkillSource;
import com.jimuqu.solon.claw.skillhub.source.OfficialSkillSource;
import com.jimuqu.solon.claw.skillhub.source.SkillSource;
import com.jimuqu.solon.claw.skillhub.source.SkillsShSkillSource;
import com.jimuqu.solon.claw.skillhub.source.WellKnownSkillSource;
import com.jimuqu.solon.claw.skillhub.support.GitHubAuth;
import com.jimuqu.solon.claw.skillhub.support.SkillHubContentSupport;
import com.jimuqu.solon.claw.skillhub.support.SkillHubHttpClient;
import com.jimuqu.solon.claw.skillhub.support.SkillHubStateStore;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认 Skills Hub 服务。 */
public class DefaultSkillHubService implements SkillHubService {
    private static final Logger log = LoggerFactory.getLogger(DefaultSkillHubService.class);

    private final File repoRoot;
    private final File skillsDir;
    private final SkillImportService skillImportService;
    private final SkillGuardService skillGuardService;
    private final SkillHubStateStore stateStore;
    private final SkillHubHttpClient httpClient;
    private final GitHubAuth gitHubAuth;
    private final GitHubSkillSource gitHubSkillSource;

    public DefaultSkillHubService(
            File repoRoot,
            File skillsDir,
            SkillImportService skillImportService,
            SkillGuardService skillGuardService,
            SkillHubStateStore stateStore,
            SkillHubHttpClient httpClient,
            GitHubAuth gitHubAuth,
            GitHubSkillSource gitHubSkillSource) {
        this.repoRoot = repoRoot;
        this.skillsDir = skillsDir;
        this.skillImportService = skillImportService;
        this.skillGuardService = skillGuardService;
        this.stateStore = stateStore;
        this.httpClient = httpClient;
        this.gitHubAuth = gitHubAuth;
        this.gitHubSkillSource = gitHubSkillSource;
    }

    @Override
    public SkillBrowseResult browse(String sourceFilter, int page, int pageSize) throws Exception {
        SourceCollectResult collected =
                collectFromSources(
                        "", sourceFilter, Math.max(pageSize * Math.max(page, 1), pageSize));
        List<SkillMeta> all = collected.items;
        SkillBrowseResult result = new SkillBrowseResult();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int start = (safePage - 1) * safePageSize;
        int end = Math.min(start + safePageSize, all.size());
        result.setTotal(all.size());
        result.setPage(safePage);
        result.setPageSize(safePageSize);
        if (start < all.size()) {
            result.setItems(new ArrayList<SkillMeta>(all.subList(start, end)));
        }
        result.setTimedOutSources(collected.failedSources);
        return result;
    }

    @Override
    public SkillBrowseResult search(String query, String sourceFilter, int limit) throws Exception {
        SourceCollectResult collected = collectFromSources(query, sourceFilter, limit);
        SkillBrowseResult result = new SkillBrowseResult();
        result.setItems(collected.items);
        result.setTotal(result.getItems().size());
        result.setPage(1);
        result.setPageSize(limit);
        result.setTimedOutSources(collected.failedSources);
        return result;
    }

    @Override
    public SkillMeta inspect(String identifier) throws Exception {
        for (SkillSource source : sources()) {
            if (!matchesSourceFilter(source, identifier)) {
                continue;
            }
            SkillMeta meta = source.inspect(normalizeIdentifierForSource(source, identifier));
            if (meta != null) {
                return meta;
            }
        }
        return null;
    }

    @Override
    public HubInstallRecord install(String identifier, String category, boolean force)
            throws Exception {
        for (SkillSource source : sources()) {
            if (!matchesSourceFilter(source, identifier)) {
                continue;
            }
            SkillBundle bundle = source.fetch(normalizeIdentifierForSource(source, identifier));
            if (bundle != null) {
                return skillImportService.installBundle(bundle, category, force, null);
            }
        }
        throw new IllegalStateException("Skill not found in any source: " + identifier);
    }

    @Override
    public List<HubInstallRecord> listInstalled() {
        return stateStore.listInstalled();
    }

    @Override
    public List<HubInstallRecord> check(String name) throws Exception {
        List<HubInstallRecord> results = new ArrayList<HubInstallRecord>();
        for (HubInstallRecord record : stateStore.listInstalled()) {
            if (StrUtil.isNotBlank(name) && !name.equals(record.getName())) {
                continue;
            }
            SkillBundle bundle = fetchFromRecordedSource(record);
            if (bundle == null) {
                continue;
            }
            HubInstallRecord copy = cloneRecord(record);
            String latestHash = SkillHubContentSupport.bundleContentHash(bundle);
            copy.getMetadata()
                    .put(
                            "status",
                            record.getContentHash().equals(latestHash)
                                    ? "up_to_date"
                                    : "update_available");
            copy.getMetadata().put("latestHash", latestHash);
            results.add(copy);
        }
        return results;
    }

    @Override
    public List<HubInstallRecord> update(String name, boolean force) throws Exception {
        List<HubInstallRecord> updated = new ArrayList<HubInstallRecord>();
        for (HubInstallRecord record : check(name)) {
            if (!"update_available".equals(record.getMetadata().get("status"))) {
                continue;
            }
            HubInstallRecord installed =
                    install(record.getIdentifier(), deriveCategory(record.getInstallPath()), force);
            updated.add(installed);
        }
        return updated;
    }

    @Override
    public List<ScanResult> audit(String name) throws Exception {
        List<ScanResult> results = new ArrayList<ScanResult>();
        for (HubInstallRecord record : stateStore.listInstalled()) {
            if (StrUtil.isNotBlank(name) && !name.equals(record.getName())) {
                continue;
            }
            File installDir =
                    FileUtil.file(
                            skillsDir, record.getInstallPath().replace('/', File.separatorChar));
            ScanResult scanResult = skillGuardService.scanSkill(installDir, record.getSource());
            results.add(scanResult);
        }
        return results;
    }

    @Override
    public String uninstall(String name) {
        HubInstallRecord record = stateStore.getInstalled(name);
        if (record == null) {
            throw new IllegalStateException("Hub-installed skill not found: " + name);
        }
        File installDir =
                FileUtil.file(skillsDir, record.getInstallPath().replace('/', File.separatorChar));
        if (installDir.exists()) {
            FileUtil.del(installDir);
        }
        stateStore.recordUninstall(name);
        stateStore.appendAuditLog(
                "UNINSTALL",
                name,
                record.getSource(),
                record.getTrustLevel(),
                "n/a",
                "user_request");
        return "Uninstalled " + name;
    }

    @Override
    public List<TapRecord> listTaps() {
        return stateStore.listTaps();
    }

    @Override
    public String addTap(String repo, String path) {
        if (StrUtil.isBlank(repo) || !repo.contains("/")) {
            throw new IllegalStateException("Invalid tap repo: " + repo);
        }
        List<TapRecord> taps = new ArrayList<TapRecord>(stateStore.listTaps());
        for (TapRecord existing : taps) {
            if (repo.equals(existing.getRepo())) {
                return "Tap already exists: " + repo;
            }
        }
        TapRecord tap = new TapRecord();
        tap.setRepo(repo);
        tap.setPath(StrUtil.blankToDefault(path, "skills/"));
        taps.add(tap);
        stateStore.saveTaps(taps);
        return "Added tap: " + repo;
    }

    @Override
    public String removeTap(String repo) {
        List<TapRecord> taps = new ArrayList<TapRecord>(stateStore.listTaps());
        boolean removed = false;
        for (int i = taps.size() - 1; i >= 0; i--) {
            if (repo.equals(taps.get(i).getRepo())) {
                taps.remove(i);
                removed = true;
            }
        }
        if (!removed) {
            throw new IllegalStateException("Tap not found: " + repo);
        }
        stateStore.saveTaps(taps);
        return "Removed tap: " + repo;
    }

    private SourceCollectResult collectFromSources(String query, String sourceFilter, int limit)
            throws Exception {
        List<SkillMeta> results = new ArrayList<SkillMeta>();
        List<String> failedSources = new ArrayList<String>();
        for (SkillSource source : sources()) {
            if (!"all".equals(sourceFilter)
                    && !source.sourceId().equals(sourceFilter)
                    && !"official".equals(source.sourceId())) {
                continue;
            }
            try {
                results.addAll(source.search(query, limit));
            } catch (Exception e) {
                failedSources.add(source.sourceId());
                log.warn(
                        "Skills Hub source search failed, skipping source: source={}, query={}, sourceFilter={}, limit={}, error={}",
                        source.sourceId(),
                        StrUtil.nullToEmpty(query),
                        StrUtil.nullToEmpty(sourceFilter),
                        limit,
                        e.toString());
                log.debug(
                        "Skills Hub source search failure detail: source={}",
                        source.sourceId(),
                        e);
            }
        }
        Map<String, SkillMeta> unique = new LinkedHashMap<String, SkillMeta>();
        for (SkillMeta meta : results) {
            SkillMeta current = unique.get(meta.getName());
            if (current == null
                    || trustRank(meta.getTrustLevel()) > trustRank(current.getTrustLevel())) {
                unique.put(meta.getName(), meta);
            }
        }
        List<SkillMeta> deduped = new ArrayList<SkillMeta>(unique.values());
        deduped.sort(
                new Comparator<SkillMeta>() {
                    @Override
                    public int compare(SkillMeta left, SkillMeta right) {
                        int rank =
                                trustRank(right.getTrustLevel()) - trustRank(left.getTrustLevel());
                        if (rank != 0) {
                            return rank;
                        }
                        return left.getName().compareToIgnoreCase(right.getName());
                    }
                });
        if (deduped.size() > limit) {
            return new SourceCollectResult(
                    new ArrayList<SkillMeta>(deduped.subList(0, limit)), failedSources);
        }
        return new SourceCollectResult(deduped, failedSources);
    }

    private SkillBundle fetchFromRecordedSource(HubInstallRecord record) throws Exception {
        for (SkillSource source : sources()) {
            if (record.getSource().equals(source.sourceId())) {
                return source.fetch(normalizeIdentifierForSource(source, record.getIdentifier()));
            }
        }
        return null;
    }

    protected List<SkillSource> sources() {
        List<SkillSource> sources = new ArrayList<SkillSource>();
        sources.add(new OfficialSkillSource(repoRoot));
        sources.add(new HermesIndexSource(httpClient, stateStore, gitHubSkillSource));
        sources.add(new SkillsShSkillSource(httpClient, stateStore, gitHubSkillSource));
        sources.add(new WellKnownSkillSource(httpClient));
        sources.add(gitHubSkillSource);
        sources.add(new ClawHubSkillSource(httpClient, stateStore));
        sources.add(
                new ClaudeMarketplaceSkillSource(
                        httpClient, gitHubAuth, gitHubSkillSource, stateStore));
        sources.add(new LobeHubSkillSource(httpClient, stateStore));
        return sources;
    }

    private boolean matchesSourceFilter(SkillSource source, String identifier) {
        String normalized = StrUtil.nullToEmpty(identifier);
        String sourceId = source.sourceId();
        if ("well-known".equals(sourceId)) {
            return normalized.startsWith("well-known:");
        }
        if ("official".equals(sourceId)) {
            return normalized.startsWith("official/");
        }
        if (normalized.contains(":")) {
            return false;
        }
        if (normalized.startsWith(sourceId + "/")) {
            return true;
        }
        if ("github".equals(sourceId) && slashCount(normalized) >= 2) {
            return true;
        }
        if ("clawhub".equals(sourceId) && slashCount(normalized) == 0) {
            return true;
        }
        return false;
    }

    private String normalizeIdentifierForSource(SkillSource source, String identifier) {
        String normalized = StrUtil.nullToEmpty(identifier);
        if ("well-known".equals(source.sourceId())) {
            return normalized;
        }
        String prefix = source.sourceId() + "/";
        if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    private int slashCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '/') {
                count++;
            }
        }
        return count;
    }

    private int trustRank(String trustLevel) {
        if ("builtin".equals(trustLevel)) {
            return 3;
        }
        if ("trusted".equals(trustLevel)) {
            return 2;
        }
        if ("agent-created".equals(trustLevel)) {
            return 1;
        }
        return 0;
    }

    private HubInstallRecord cloneRecord(HubInstallRecord record) {
        HubInstallRecord copy = new HubInstallRecord();
        copy.setName(record.getName());
        copy.setSource(record.getSource());
        copy.setIdentifier(record.getIdentifier());
        copy.setTrustLevel(record.getTrustLevel());
        copy.setScanVerdict(record.getScanVerdict());
        copy.setContentHash(record.getContentHash());
        copy.setInstallPath(record.getInstallPath());
        copy.setFiles(new ArrayList<String>(record.getFiles()));
        copy.setMetadata(new LinkedHashMap<String, Object>(record.getMetadata()));
        return copy;
    }

    private String deriveCategory(String installPath) {
        int index = installPath.lastIndexOf('/');
        return index < 0 ? null : installPath.substring(0, index);
    }

    private static class SourceCollectResult {
        private final List<SkillMeta> items;
        private final List<String> failedSources;

        private SourceCollectResult(List<SkillMeta> items, List<String> failedSources) {
            this.items = items;
            this.failedSources = failedSources;
        }
    }
}
