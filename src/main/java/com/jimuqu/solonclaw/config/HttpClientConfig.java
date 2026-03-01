package com.jimuqu.solonclaw.config;

import okhttp3.OkHttpClient;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 客户端配置
 * <p>
 * 配置 OkHttpClient 用于调用 OpenAI API
 *
 * @author SolonClaw
 */
@Configuration
public class HttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpClientConfig.class);

    private static final int MAX_LOG_LENGTH = 3000;

    /**
     * 配置 OkHttpClient
     */
    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(new HttpLoggingInterceptor())
            .build();

        log.info("OkHttpClient 已配置（带 HTTP 日志拦截器）");
        return client;
    }

    /**
     * HTTP 日志拦截器
     * <p>
     * 记录所有 HTTP 请求和响应的详细信息
     */
    private static class HttpLoggingInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            // 记录请求信息
            long startTime = System.currentTimeMillis();
            logRequest(request);

            // 执行请求
            Response response;
            try {
                response = chain.proceed(request);
            } catch (IOException e) {
                log.error("HTTP 请求失败: {} {} - {}", request.method(), request.url(), e.getMessage());
                throw e;
            }

            // 记录响应信息
            long duration = System.currentTimeMillis() - startTime;
            logResponse(response, duration);

            return response;
        }

        /**
         * 记录请求
         */
        private void logRequest(Request request) {
            log.info("========== HTTP 请求 ==========");
            log.info("方法: {}", request.method());
            log.info("URL: {}", request.url());

            // 记录请求头
            log.info("请求头:");
            request.headers().forEach(pair -> {
                if (!pair.getFirst().equalsIgnoreCase("Authorization")) {
                    // 不记录 Authorization 头（保护敏感信息）
                    log.info("  {}: {}", pair.getFirst(), pair.getSecond());
                } else {
                    log.info("  {}: Bearer *** (已隐藏)", pair.getFirst());
                }
            });

            // 记录请求体（如果有）
            if (request.body() != null) {
                try {
                    okhttp3.MediaType contentType = request.body().contentType();
                    if (contentType != null && contentType.subtype() != null &&
                        (contentType.subtype().contains("json") || contentType.subtype().contains("x-www-form-urlencoded"))) {
                        // 使用 okio.Buffer 读取请求体
                        okio.Buffer buffer = new okio.Buffer();
                        request.body().writeTo(buffer);
                        String bodyString = buffer.readUtf8();
                        log.info("请求体 ({} 字节): {}", request.body().contentLength(),
                               truncate(bodyString, MAX_LOG_LENGTH));
                    } else {
                        log.info("请求体: {} 字节 (Content-Type: {})",
                               request.body().contentLength(), contentType);
                    }
                } catch (Exception e) {
                    log.warn("无法记录请求体: {}", e.getMessage());
                }
            }
            log.info("==============================");
        }

        /**
         * 记录响应
         */
        private void logResponse(Response response, long duration) {
            log.info("========== HTTP 响应 ==========");
            log.info("状态码: {}", response.code());
            log.info("消息: {}", response.message());
            log.info("耗时: {} ms", duration);

            // 记录响应头
            log.info("响应头:");
            response.headers().forEach(pair -> {
                log.info("  {}: {}", pair.getFirst(), pair.getSecond());
            });

            // 记录响应体
            try (ResponseBody responseBody = response.body()) {
                if (responseBody != null) {
                    String responseBodyString = responseBody.string();
                    long contentLength = responseBodyString.length();

                    log.info("响应体 ({} 字节):", contentLength);

                    // 检查是否是 HTML（可能是错误页面）
                    if (responseBodyString.trim().startsWith("<")) {
                        log.error("⚠️ 响应体是 HTML 格式，而不是 JSON！");
                        log.error("HTML 内容前 500 字符: {}", truncate(responseBodyString, 500));
                    } else {
                        // 记录 JSON 响应（截断）
                        log.info("JSON 内容: {}", truncate(responseBodyString, MAX_LOG_LENGTH));
                    }

                    // 如果内容过长，记录到 DEBUG 级别
                    if (contentLength > MAX_LOG_LENGTH) {
                        log.debug("完整响应体: {}", responseBodyString);
                    }

                    // 检查是否是错误响应
                    if (!response.isSuccessful()) {
                        log.error("HTTP 错误响应: {} {}", response.code(), response.message());
                        log.error("错误响应内容: {}", responseBodyString);
                    }
                }
            } catch (Exception e) {
                log.warn("无法记录响应体: {}", e.getMessage());
            }
            log.info("==============================");
        }

        /**
         * 截断长文本
         */
        private String truncate(String text, int maxLength) {
            if (text == null) return null;
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength) + "... (已截断，总长度: " + text.length() + ")";
        }
    }
}