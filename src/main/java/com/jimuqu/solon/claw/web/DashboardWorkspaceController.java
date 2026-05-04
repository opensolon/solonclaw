package com.jimuqu.solon.claw.web;

import java.util.Collections;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 人格工作区文件接口。 */
@Controller
public class DashboardWorkspaceController {
    private final DashboardWorkspaceService workspaceService;

    public DashboardWorkspaceController(DashboardWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Mapping(value = "/api/workspace/files", method = MethodType.GET)
    public Map<String, Object> files(Context context) {
        return execute(
                context,
                new Callback() {
                    @Override
                    public Map<String, Object> run() {
                        return workspaceService.getFiles();
                    }
                });
    }

    @Mapping(value = "/api/workspace/files/{key}", method = MethodType.GET)
    public Map<String, Object> file(Context context, final String key) {
        return execute(
                context,
                new Callback() {
                    @Override
                    public Map<String, Object> run() {
                        return workspaceService.getFile(key);
                    }
                });
    }

    @Mapping(value = "/api/workspace/files/{key}", method = MethodType.PUT)
    public Map<String, Object> save(Context context, final String key) throws Exception {
        final String content = ONode.ofJson(context.body()).get("content").getString();
        return execute(
                context,
                new Callback() {
                    @Override
                    public Map<String, Object> run() {
                        return workspaceService.saveFile(key, content);
                    }
                });
    }

    @Mapping(value = "/api/workspace/files/{key}/restore", method = MethodType.POST)
    public Map<String, Object> restore(Context context, final String key) {
        return execute(
                context,
                new Callback() {
                    @Override
                    public Map<String, Object> run() {
                        return workspaceService.restoreFile(key);
                    }
                });
    }

    @Mapping(value = "/api/workspace/diaries", method = MethodType.GET)
    public Map<String, Object> diaries(Context context) {
        return execute(
                context,
                new Callback() {
                    @Override
                    public Map<String, Object> run() {
                        return workspaceService.listDiaryFiles();
                    }
                });
    }

    @Mapping(value = "/api/workspace/diaries/read", method = MethodType.GET)
    public Map<String, Object> diary(Context context) {
        final String relativePath = context.param("path");
        return execute(
                context,
                new Callback() {
                    @Override
                    public Map<String, Object> run() {
                        return workspaceService.getDiaryFile(relativePath);
                    }
                });
    }

    private Map<String, Object> execute(Context context, Callback callback) {
        try {
            return callback.run();
        } catch (IllegalArgumentException e) {
            context.status(400);
            return Collections.<String, Object>singletonMap("error", e.getMessage());
        }
    }

    private interface Callback {
        Map<String, Object> run();
    }
}
