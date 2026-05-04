package com.jimuqu.solon.claw.web;

import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 会话接口。 */
@Controller
public class DashboardSessionController {
    private final DashboardSessionService sessionService;

    public DashboardSessionController(DashboardSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Mapping(value = "/api/sessions", method = MethodType.GET)
    public Map<String, Object> sessions(Context context) throws Exception {
        return DashboardResponse.ok(
                sessionService.getSessions(
                        context.paramAsInt("limit", 20), context.paramAsInt("offset", 0)));
    }

    @Mapping(value = "/api/sessions/search", method = MethodType.GET)
    public Map<String, Object> search(Context context) throws Exception {
        return DashboardResponse.ok(sessionService.searchSessions(context.param("q")));
    }

    @Mapping(value = "/api/sessions/{id}/messages", method = MethodType.GET)
    public Map<String, Object> messages(String id) throws Exception {
        return DashboardResponse.ok(sessionService.getSessionMessages(id));
    }

    @Mapping(value = "/api/sessions/{id}/tree", method = MethodType.GET)
    public Map<String, Object> tree(String id) throws Exception {
        return DashboardResponse.ok(sessionService.sessionTree(id));
    }

    @Mapping(value = "/api/sessions/{id}/checkpoints", method = MethodType.GET)
    public Map<String, Object> checkpoints(String id) throws Exception {
        return DashboardResponse.ok(sessionService.checkpoints(id));
    }

    @Mapping(value = "/api/checkpoints/{id}/preview", method = MethodType.GET)
    public Map<String, Object> checkpointPreview(String id) throws Exception {
        return DashboardResponse.ok(sessionService.checkpointPreview(id));
    }

    @Mapping(value = "/api/checkpoints/{id}/rollback", method = MethodType.POST)
    public Map<String, Object> rollbackCheckpoint(String id) throws Exception {
        return DashboardResponse.ok(sessionService.rollbackCheckpoint(id));
    }

    @Mapping(value = "/api/sessions/{id}", method = MethodType.DELETE)
    public Map<String, Object> delete(String id) throws Exception {
        return DashboardResponse.ok(sessionService.deleteSession(id));
    }
}
