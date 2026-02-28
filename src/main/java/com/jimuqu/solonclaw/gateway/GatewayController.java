package com.jimuqu.solonclaw.gateway;

import com.jimuqu.solonclaw.agent.AgentService;
import org.noear.solon.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 网关控制器
 * <p>
 * 提供 HTTP 接口供外部调用 AI 助手
 *
 * @author SolonClaw
 */
@Controller
@Mapping("/api")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    @Inject
    private AgentService agentService;

    /**
     * 健康检查
     */
    @Get
    @Mapping("/health")
    public Result health() {
        return Result.success("SolonClaw is running", Map.of(
                "status", "ok",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 对话接口
     */
    @Post
    @Mapping("/chat")
    public Result chat(@Body ChatRequest request) {
        log.info("收到对话请求: sessionId={}, message={}",
                request.sessionId(), request.message());

        try {
            // 处理 sessionId
            String sessionId = request.sessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = generateSessionId();
            }

            String response = agentService.chat(request.message(), sessionId);

            return Result.success("对话成功", Map.of(
                    "sessionId", sessionId,
                    "response", response
            ));
        } catch (Exception e) {
            log.error("对话处理异常", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话历史
     */
    @Get
    @Mapping("/sessions/{id}")
    public Result getSessionHistory(String id) {
        log.info("获取会话历史: sessionId={}", id);

        try {
            var history = agentService.getHistory(id);
            return Result.success("获取成功", Map.of(
                    "sessionId", id,
                    "history", history
            ));
        } catch (Exception e) {
            log.error("获取会话历史异常", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 清空会话历史
     */
    @Delete
    @Mapping("/sessions/{id}")
    public Result clearSessionHistory(String id) {
        log.info("清空会话历史: sessionId={}", id);

        try {
            agentService.clearHistory(id);
            return Result.success("清空成功", Map.of(
                    "sessionId", id
            ));
        } catch (Exception e) {
            log.error("清空会话历史异常", e);
            return Result.error("清空失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用工具列表
     */
    @Get
    @Mapping("/tools")
    public Result getAvailableTools() {
        try {
            var tools = agentService.getAvailableTools();
            return Result.success("获取成功", tools);
        } catch (Exception e) {
            log.error("获取工具列表异常", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "sess-" + System.currentTimeMillis();
    }

    /**
     * 对话请求
     *
     * @param message  用户消息
     * @param sessionId 会话ID（可选）
     */
    public record ChatRequest(
            String message,
            String sessionId
    ) {
    }

    /**
     * 统一响应结果
     *
     * @param code    状态码
     * @param message 消息
     * @param data    数据
     */
    public record Result(
            int code,
            String message,
            Object data
    ) {
        public static Result success(String message, Object data) {
            return new Result(200, message, data);
        }

        public static Result error(String message) {
            return new Result(500, message, null);
        }
    }
}
