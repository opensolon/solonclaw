package com.jimuqu.solon.claw.web;

import java.util.Collections;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.UploadedFile;

/** Dashboard chat 运行接口。 */
@Controller
public class DashboardChatController {
    private final DashboardChatService chatService;

    public DashboardChatController(DashboardChatService chatService) {
        this.chatService = chatService;
    }

    @Mapping(value = "/api/chat/uploads", method = MethodType.POST, multipart = true)
    public Map<String, Object> uploads(Context context, UploadedFile[] file) {
        try {
            return chatService.uploads(file);
        } catch (IllegalArgumentException e) {
            context.status(400);
            return Collections.<String, Object>singletonMap("error", e.getMessage());
        } catch (Exception e) {
            context.status(500);
            return Collections.<String, Object>singletonMap("error", e.getMessage());
        }
    }

    @Mapping(value = "/api/chat/runs", method = MethodType.POST)
    public Map<String, Object> startRun(Context context) {
        try {
            return chatService.startRun(ONode.ofJson(context.body()));
        } catch (IllegalArgumentException e) {
            context.status(400);
            return Collections.<String, Object>singletonMap("error", e.getMessage());
        } catch (Exception e) {
            context.status(500);
            return Collections.<String, Object>singletonMap("error", e.getMessage());
        }
    }

    @Mapping(value = "/api/chat/runs/{runId}/events", method = MethodType.GET)
    public void events(Context context, String runId) throws Exception {
        chatService.streamEvents(runId, context);
    }

    @Mapping(value = "/api/chat/runs/{runId}/cancel", method = MethodType.POST)
    public Map<String, Object> cancel(Context context, String runId) {
        try {
            return chatService.cancelRun(runId);
        } catch (IllegalArgumentException e) {
            context.status(404);
            return Collections.<String, Object>singletonMap("error", e.getMessage());
        }
    }
}
