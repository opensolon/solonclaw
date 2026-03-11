package com.jimuqu.solonclaw.bootstrap;

import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 引导配置
 * <p>
 * 管理 Bootstrap 引导文件机制的配置项
 *
 * @author SolonClaw
 */
@Configuration
public class BootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(BootstrapConfig.class);

    @Inject("${solonclaw.bootstrap.enabled}")
    private boolean enabled = true;

    @Inject("${solonclaw.bootstrap.templatesDir}")
    private String templatesDir = "templates";

    @Inject("${solonclaw.bootstrap.autoSeed}")
    private boolean autoSeed = true;

    /**
     * 是否启用引导机制
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取模板目录
     */
    public String getTemplatesDir() {
        return templatesDir;
    }

    /**
     * 是否自动创建模板文件
     */
    public boolean isAutoSeed() {
        return autoSeed;
    }

    /**
     * 初始化配置
     */
    public void init() {
        log.info("- Bootstrap 引导机制: {}", enabled ? "启用" : "禁用");
        if (enabled) {
            log.info("- Bootstrap 模板目录: {}", templatesDir);
            log.info("- Bootstrap 自动初始化: {}", autoSeed);
        }
    }
}
