package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SqliteDatabaseConcurrencyTest {
    @Test
    void shouldSerializeConcurrentSqliteAccess() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-sqlite-concurrency").toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.getRuntime()
                .setStateDb(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());

        SqliteChannelStateRepository repository =
                new SqliteChannelStateRepository(new SqliteDatabase(config));
        int workers = 12;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(workers);
        List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
        for (int i = 0; i < workers; i++) {
            final int index = i;
            futures.add(
                    executorService.submit(
                            new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    start.await();
                                    String key = "key-" + index;
                                    String value = "value-" + index;
                                    repository.put(PlatformType.WEIXIN, "scope", key, value);
                                    return value.equals(
                                            repository.get(PlatformType.WEIXIN, "scope", key));
                                }
                            }));
        }

        start.countDown();
        for (Future<Boolean> future : futures) {
            assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
        }
        executorService.shutdownNow();
    }
}
