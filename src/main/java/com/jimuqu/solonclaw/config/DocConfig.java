package com.jimuqu.solonclaw.config;

import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.docs.DocDocket;
import org.noear.solon.docs.models.ApiContact;
import org.noear.solon.docs.models.ApiInfo;

/**
 * API 文档配置
 * <p>
 * 配置 OpenAPI 文档，通过 /doc.html 访问
 *
 * @author SolonClaw
 */
@Configuration
public class DocConfig {

    /**
     * 网关 API 文档
     */
    @Bean("gatewayApi")
    public DocDocket gatewayApi() {
        return new DocDocket()
                .groupName("网关接口")
                .info(new ApiInfo()
                        .title("SolonClaw 网关 API")
                        .description("AI Agent 对话接口，包括对话、会话管理、工具列表等")
                        .version("1.0.0")
                        .contact(new ApiContact()
                                .name("SolonClaw Team")
                                .email("solonclaw@example.com")))
                .schemes("http", "https")
                .apis("com.jimuqu.solonclaw.gateway");
    }

    /**
     * 健康检查 API 文档
     */
    @Bean("healthApi")
    public DocDocket healthApi() {
        return new DocDocket()
                .groupName("健康检查")
                .info(new ApiInfo()
                        .title("SolonClaw 健康检查 API")
                        .description("系统健康检查接口，支持 Kubernetes 探针")
                        .version("1.0.0"))
                .schemes("http", "https")
                .apis("com.jimuqu.solonclaw.health");
    }

    /**
     * MCP 管理 API 文档
     */
    @Bean("mcpApi")
    public DocDocket mcpApi() {
        return new DocDocket()
                .groupName("MCP 管理")
                .info(new ApiInfo()
                        .title("SolonClaw MCP API")
                        .description("MCP 服务器管理接口，用于管理 Model Context Protocol 服务器")
                        .version("1.0.0"))
                .schemes("http", "https")
                .apis("com.jimuqu.solonclaw.mcp");
    }

    /**
     * 调度任务 API 文档
     */
    @Bean("schedulerApi")
    public DocDocket schedulerApi() {
        return new DocDocket()
                .groupName("调度任务")
                .info(new ApiInfo()
                        .title("SolonClaw 调度 API")
                        .description("定时任务管理接口，支持 Cron、固定频率、一次性任务")
                        .version("1.0.0"))
                .schemes("http", "https")
                .apis("com.jimuqu.solonclaw.scheduler");
    }

    /**
     * 技能管理 API 文档
     */
    @Bean("skillApi")
    public DocDocket skillApi() {
        return new DocDocket()
                .groupName("技能管理")
                .info(new ApiInfo()
                        .title("SolonClaw 技能 API")
                        .description("动态技能管理接口，支持自定义 AI 技能")
                        .version("1.0.0"))
                .schemes("http", "https")
                .apis("com.jimuqu.solonclaw.skill");
    }

    /**
     * 日志管理 API 文档
     */
    @Bean("logApi")
    public DocDocket logApi() {
        return new DocDocket()
                .groupName("日志管理")
                .info(new ApiInfo()
                        .title("SolonClaw 日志 API")
                        .description("日志查询和管理接口")
                        .version("1.0.0"))
                .schemes("http", "https")
                .apis("com.jimuqu.solonclaw.logging");
    }
}