package com.jimuqu.solon.claw.skillhub.support;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.skillhub.model.HubAuditEntry;
import com.jimuqu.solon.claw.skillhub.model.HubInstallRecord;
import com.jimuqu.solon.claw.skillhub.model.TapRecord;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.snack4.ONode;

/** Skills Hub 状态存储。 */
public class SkillHubStateStore {
    private final File skillsDir;

    public SkillHubStateStore(File skillsDir) {
        this.skillsDir = skillsDir;
        SkillHubPathSupport.ensureHubDirs(skillsDir);
    }

    public List<HubInstallRecord> listInstalled() {
        return new ArrayList<HubInstallRecord>(loadLock().values());
    }

    public HubInstallRecord getInstalled(String name) {
        return loadLock().get(name);
    }

    public void recordInstall(HubInstallRecord record) {
        Map<String, HubInstallRecord> installed = loadLock();
        installed.put(record.getName(), record);
        saveLock(installed);
    }

    public void recordUninstall(String name) {
        Map<String, HubInstallRecord> installed = loadLock();
        installed.remove(name);
        saveLock(installed);
    }

    public List<TapRecord> listTaps() {
        File tapsFile = SkillHubPathSupport.tapsFile(skillsDir);
        if (!tapsFile.exists()) {
            return Collections.emptyList();
        }
        TapContainer container =
                ONode.deserialize(FileUtil.readUtf8String(tapsFile), TapContainer.class);
        return container == null || container.getTaps() == null
                ? Collections.<TapRecord>emptyList()
                : container.getTaps();
    }

    public void saveTaps(List<TapRecord> taps) {
        TapContainer container = new TapContainer();
        container.setTaps(new ArrayList<TapRecord>(taps));
        FileUtil.writeUtf8String(
                ONode.serialize(container), SkillHubPathSupport.tapsFile(skillsDir));
    }

    public void appendAuditLog(
            String action,
            String skillName,
            String source,
            String trustLevel,
            String verdict,
            String extra) {
        HubAuditEntry entry = new HubAuditEntry();
        entry.setTimestamp(String.valueOf(new Date().getTime()));
        entry.setAction(action);
        entry.setSkillName(skillName);
        entry.setSource(source);
        entry.setTrustLevel(trustLevel);
        entry.setVerdict(verdict);
        entry.setExtra(StrUtil.nullToEmpty(extra));
        String line = ONode.serialize(entry) + System.lineSeparator();
        FileUtil.appendUtf8String(line, SkillHubPathSupport.auditLog(skillsDir));
    }

    public String readCachedIndex(String key) {
        File target = cacheFile(key);
        if (!target.exists()) {
            return null;
        }
        return FileUtil.readUtf8String(target);
    }

    public void writeCachedIndex(String key, String content) {
        FileUtil.writeUtf8String(StrUtil.nullToEmpty(content), cacheFile(key));
    }

    public File quarantineDir() {
        return SkillHubPathSupport.quarantineDir(skillsDir);
    }

    public File importedDir() {
        return SkillHubPathSupport.importedDir(skillsDir);
    }

    private Map<String, HubInstallRecord> loadLock() {
        File lockFile = SkillHubPathSupport.lockFile(skillsDir);
        if (!lockFile.exists()) {
            return new LinkedHashMap<String, HubInstallRecord>();
        }
        LockContainer container =
                ONode.deserialize(FileUtil.readUtf8String(lockFile), LockContainer.class);
        return container == null || container.getInstalled() == null
                ? new LinkedHashMap<String, HubInstallRecord>()
                : container.getInstalled();
    }

    private void saveLock(Map<String, HubInstallRecord> installed) {
        LockContainer container = new LockContainer();
        container.setVersion(1);
        container.setInstalled(new LinkedHashMap<String, HubInstallRecord>(installed));
        FileUtil.writeUtf8String(
                ONode.serialize(container), SkillHubPathSupport.lockFile(skillsDir));
    }

    private File cacheFile(String key) {
        return FileUtil.file(SkillHubPathSupport.indexCacheDir(skillsDir), key + ".json");
    }

    public static class LockContainer {
        private int version;
        private Map<String, HubInstallRecord> installed =
                new LinkedHashMap<String, HubInstallRecord>();

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public Map<String, HubInstallRecord> getInstalled() {
            return installed;
        }

        public void setInstalled(Map<String, HubInstallRecord> installed) {
            this.installed = installed;
        }
    }

    public static class TapContainer {
        private List<TapRecord> taps = new ArrayList<TapRecord>();

        public List<TapRecord> getTaps() {
            return taps;
        }

        public void setTaps(List<TapRecord> taps) {
            this.taps = taps;
        }
    }
}
