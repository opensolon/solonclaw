package com.jimuqu.solon.claw.bootstrap;

import com.jimuqu.solon.claw.config.AppConfig;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/** 根配置类，负责装配应用级配置对象。 */
@Configuration
public class SolonClawConfiguration {
    @Bean
    public AppConfig appConfig() {
        return AppConfig.load(Solon.cfg());
    }
}
