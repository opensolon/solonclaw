package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.context.PersonaWorkspaceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard 人格工作区文件服务。 */
public class DashboardWorkspaceService {
    private final PersonaWorkspaceService personaWorkspaceService;

    public DashboardWorkspaceService(PersonaWorkspaceService personaWorkspaceService) {
        this.personaWorkspaceService = personaWorkspaceService;
    }

    public Map<String, Object> getFiles() {
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        for (String key : personaWorkspaceService.orderedKeys()) {
            files.add(describeFile(key));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("files", files);
        return result;
    }

    public Map<String, Object> getFile(String key) {
        return describeFile(key);
    }

    public Map<String, Object> saveFile(String key, String content) {
        personaWorkspaceService.write(key, content);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("file", describeFile(key));
        return result;
    }

    public Map<String, Object> restoreFile(String key) {
        personaWorkspaceService.restoreTemplate(key);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.put("file", describeFile(key));
        return result;
    }

    public Map<String, Object> listDiaryFiles() {
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        for (String relativePath : personaWorkspaceService.listDiaryRelativePaths()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", FileUtil.file(relativePath).getName());
            item.put("relativePath", relativePath);
            item.put("path", personaWorkspaceService.absoluteDiaryPath(relativePath));
            files.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("files", files);
        return result;
    }

    public Map<String, Object> getDiaryFile(String relativePath) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("name", FileUtil.file(relativePath).getName());
        result.put("relativePath", relativePath);
        result.put("path", personaWorkspaceService.absoluteDiaryPath(relativePath));
        result.put("content", personaWorkspaceService.readDiary(relativePath));
        return result;
    }

    private Map<String, Object> describeFile(String key) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", key);
        result.put("name", personaWorkspaceService.fileName(key));
        result.put("path", personaWorkspaceService.absolutePath(key));
        result.put("exists", personaWorkspaceService.exists(key));
        result.put("content", personaWorkspaceService.read(key));
        return result;
    }
}
