package com.jimuqu.solonclaw.config;

import com.zaxxer.hikari.HikariDataSource;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Files;

/**
 * 数据库配置
 * <p>
 * 配置 H2 数据源并初始化表结构
 *
 * @author SolonClaw
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    /**
     * 配置 H2 数据源
     * 通过方法参数注入 WorkspaceInfo
     */
    @Bean
    public DataSource dataSource(WorkspaceConfig.WorkspaceInfo workspaceInfo) {
        try {
            // 创建数据库目录
            java.nio.file.Path dbPath = workspaceInfo.databaseFile();
            if (!Files.exists(dbPath.getParent())) {
                Files.createDirectories(dbPath.getParent());
            }

            // 创建 HikariCP 数据源
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl("jdbc:h2:" + dbPath.toString().replace(".mv.db", "") +
                ";MODE=MySQL;AUTO_SERVER=TRUE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setMaximumPoolSize(10);
            dataSource.setConnectionTimeout(30000);

            log.info("H2 数据源已配置，数据库路径: {}", dbPath);

            return dataSource;
        } catch (Exception e) {
            log.error("配置数据源失败", e);
            throw new RuntimeException("配置数据源失败", e);
        }
    }
}
