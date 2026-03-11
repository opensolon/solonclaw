package com.jimuqu.solonclaw.bootstrap;

import com.jimuqu.solonclaw.config.WorkspaceConfig;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 引导初始化器
 * <p>
 * 应用启动时自动初始化引导文件
 *
 * @author SolonClaw
 */
@Component
public class BootstrapInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapInitializer.class);

    @Inject(required = false)
    private WorkspaceConfig.WorkspaceInfo workspaceInfo;

    @Inject
    private BootstrapConfig config;

    @Inject
    private BootstrapService bootstrapService;

    /**
     * 初始化引导文件
     */
    @Init
    public void init() {
        if (!config.isEnabled()) {
            log.info("Bootstrap 引导机制已禁用");
            return;
        }

        if (workspaceInfo == null) {
            log.warn("工作区信息未注入，跳过引导初始化");
            return;
        }

        String workspaceDir = workspaceInfo.workspace().toString();
        log.info("开始 Bootstrap 引导初始化...");

        // 种子引导文件
        bootstrapService.seedBootstrapFiles(workspaceDir);

        // 检查引导状态
        boolean hasBootstrap = bootstrapService.hasBootstrap(workspaceDir);
        boolean onboardingComplete = bootstrapService.checkOnboardingComplete(workspaceDir);

        if (hasBootstrap && !onboardingComplete) {
            log.info("检测到首次运行引导：BOOTSTRAP.md 存在，等待用户完成引导...");
            // TODO: 这里可以触发一个内部事件，通知 Agent 主动发起引导对话
        } else if (onboardingComplete) {
            log.info("引导已完成，IDENTITY.md 和 USER.md 已配置");
        }

        log.info("Bootstrap 引导初始化完成");
    }
}
