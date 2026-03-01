package com.jimuqu.solonclaw.config;

import org.noear.solon.annotation.Configuration;
import org.noear.solon.web.cors.CrossFilter;
import org.noear.solon.web.staticfiles.StaticMappings;
import org.noear.solon.web.staticfiles.repository.ClassPathStaticRepository;
import org.noear.solon.annotation.Bean;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web 配置类
 * <p>
 * 配置静态文件服务和 CORS 跨域支持
 *
 * @author SolonClaw
 */
@Configuration
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    /**
     * 配置 CORS 跨域处理
     */
    @Bean
    public Filter corsFilter() {
        log.info("配置 CORS 跨域支持...");

        CrossFilter filter = new CrossFilter();
        filter.allowedOrigins("*");
        filter.allowedMethods("*");
        filter.allowedHeaders("*");
        filter.maxAge(3600);

        log.info("CORS 配置完成");
        return filter;
    }

    /**
     * 配置静态文件服务
     * <p>
     * 将 frontend 目录映射到 / 根路径
     */
    @Bean
    public void initStaticFiles() {
        log.info("配置静态文件服务...");

        // 映射前端文件目录到根路径
        StaticMappings.add("/", new ClassPathStaticRepository("frontend"));

        log.info("静态文件服务配置完成");
    }
}