package com.jimuqu.solon.claw.bootstrap;

import com.jimuqu.solon.claw.agent.AgentProfileRepository;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.ChannelStateRepository;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.core.repository.GatewayPolicyRepository;
import com.jimuqu.solon.claw.core.repository.GlobalSettingRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.CheckpointService;
import com.jimuqu.solon.claw.storage.repository.SqliteAgentProfileRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteAgentRunRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteCronJobRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.storage.repository.SqliteGatewayPolicyRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteGlobalSettingRepository;
import com.jimuqu.solon.claw.storage.repository.SqlitePreferenceStore;
import com.jimuqu.solon.claw.storage.repository.SqliteSessionRepository;
import com.jimuqu.solon.claw.support.DefaultCheckpointService;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/** storage bean configuration. */
@Configuration
public class StorageConfiguration {
    @Bean(destroyMethod = "shutdown")
    public SqliteDatabase sqliteDatabase(AppConfig appConfig) throws Exception {
        return new SqliteDatabase(appConfig);
    }

    @Bean
    public SqlitePreferenceStore sqlitePreferenceStore(SqliteDatabase sqliteDatabase) {
        return new SqlitePreferenceStore(sqliteDatabase);
    }

    @Bean
    public SessionRepository sessionRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteSessionRepository(sqliteDatabase);
    }

    @Bean
    public CronJobRepository cronJobRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteCronJobRepository(sqliteDatabase);
    }

    @Bean
    public AgentRunRepository agentRunRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteAgentRunRepository(sqliteDatabase);
    }

    @Bean
    public ChannelStateRepository channelStateRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteChannelStateRepository(sqliteDatabase);
    }

    @Bean
    public GlobalSettingRepository globalSettingRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteGlobalSettingRepository(sqliteDatabase);
    }

    @Bean
    public AgentProfileRepository agentProfileRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteAgentProfileRepository(sqliteDatabase);
    }

    @Bean
    public GatewayPolicyRepository gatewayPolicyRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteGatewayPolicyRepository(sqliteDatabase);
    }

    @Bean
    public CheckpointService checkpointService(AppConfig appConfig, SqliteDatabase sqliteDatabase) {
        return new DefaultCheckpointService(appConfig, sqliteDatabase);
    }
}
