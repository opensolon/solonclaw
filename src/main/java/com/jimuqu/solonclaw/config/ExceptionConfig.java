package com.jimuqu.solonclaw.config;

import com.jimuqu.solonclaw.exception.GlobalExceptionHandler;
import org.noear.solon.annotation.*;
import org.noear.solon.core.aspect.Interceptor;
import org.noear.solon.core.aspect.Invocation;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局异常配置
 * <p>
 * 配置全局异常拦截器，处理应用程序中的所有异常
 *
 * @author SolonClaw
 */
@Component
public class ExceptionConfig {

    private static final Logger log = LoggerFactory.getLogger(ExceptionConfig.class);

    @Inject
    private GlobalExceptionHandler exceptionHandler;

    /**
     * 全局异常拦截器（基于 Solon 的 @Tran 注解机制）
     */
    @Bean
    public Interceptor globalExceptionInterceptor() {
        return new Interceptor() {
            @Override
            public Object doIntercept(Invocation inv) throws Throwable {
                try {
                    return inv.invoke();
                } catch (Exception e) {
                    Context ctx = Context.current();
                    String path = ctx.path();
                    String method = ctx.method();

                    log.error("[未捕获异常] {} {} - {}",
                            method, path, e.getClass().getSimpleName(), e);

                    // 根据异常类型返回不同的错误响应
                    return handleException(e);
                }
            }

            /**
             * 根据 exception 类型处理
             */
            private Object handleException(Exception e) {
                Context ctx = Context.current();
                ctx.contentType("application/json;charset=UTF-8");

                try {
                    // 尝试获取 exceptionHandler 并调用相应方法
                    String message = e.getMessage();
                    int code = 5000;
                    int httpStatus = 500;

                    // 根据异常类型设置不同的错误码
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("用户不存在")) {
                            code = 4001;
                            httpStatus = 401;
                        } else if (e.getMessage().contains("用户名已存在")) {
                            code = 4002;
                            httpStatus = 400;
                        } else if (e.getMessage().contains("密码错误")) {
                            code = 4003;
                            httpStatus = 401;
                        } else if (e.getMessage().contains("Token") || e.getMessage().contains("认证")) {
                            code = 4004;
                            httpStatus = 401;
                        } else if (e.getMessage().contains("权限不足")) {
                            code = 4007;
                            httpStatus = 403;
                        }
                    }

                    ctx.status(httpStatus);
                    String jsonResponse = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                            code, escapeJson(message != null ? message : "系统内部错误"));
                    ctx.output(jsonResponse);
                    return null;

                } catch (Exception ex) {
                    log.error("异常处理失败", ex);
                    ctx.status(500);
                    ctx.output("{\"code\":5000,\"message\":\"系统内部错误\",\"data\":null}");
                    return null;
                }
            }

            /**
             * 转义 JSON 字符串
             */
            private String escapeJson(String value) {
                if (value == null) return "";
                return value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
            }
        };
    }
}