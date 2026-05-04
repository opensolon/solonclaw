package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.SecretRedactor;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Dashboard 日志读取服务。 */
public class DashboardLogsService {
    private final AppConfig appConfig;

    public DashboardLogsService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public List<String> read(String fileName, int lineCount, String level, String component) {
        File file = resolveFile(fileName);
        if (!file.exists()) {
            return Collections.emptyList();
        }

        List<String> all = FileUtil.readUtf8Lines(file);
        List<String> filtered = new ArrayList<String>();
        for (String line : all) {
            if (!matchesLevel(line, level)) {
                continue;
            }
            if (!matchesComponent(line, component)) {
                continue;
            }
            filtered.add(SecretRedactor.redact(line, 2000));
        }

        int safeLineCount = lineCount <= 0 ? 100 : Math.min(lineCount, 500);
        int start = Math.max(0, filtered.size() - safeLineCount);
        return new ArrayList<String>(filtered.subList(start, filtered.size()));
    }

    private File resolveFile(String fileName) {
        String resolved = StrUtil.blankToDefault(fileName, "agent").toLowerCase(Locale.ROOT);
        if (!"gateway".equals(resolved) && !"errors".equals(resolved)) {
            resolved = "agent";
        }
        return FileUtil.file(appConfig.getRuntime().getLogsDir(), resolved + ".log");
    }

    private boolean matchesLevel(String line, String level) {
        String normalized = StrUtil.blankToDefault(level, "ALL").toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return true;
        }
        return line.toUpperCase(Locale.ROOT).contains(" " + normalized + " ");
    }

    private boolean matchesComponent(String line, String component) {
        String normalized = StrUtil.blankToDefault(component, "all").toLowerCase(Locale.ROOT);
        if ("all".equals(normalized)) {
            return true;
        }
        if ("gateway".equals(normalized)) {
            return line.contains(".gateway.");
        }
        if ("tools".equals(normalized)) {
            return line.contains(".tool.");
        }
        if ("cron".equals(normalized)) {
            return line.contains(".scheduler.");
        }
        if ("agent".equals(normalized)) {
            return line.contains("com.jimuqu.solon.claw");
        }
        return line.toLowerCase(Locale.ROOT).contains(normalized);
    }
}
