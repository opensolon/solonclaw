package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class CheckpointRollbackTest {
    @Test
    void shouldRollbackLatestStructuredFileWrite() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        String sourceKey = "MEMORY:room-a:user-a";
        SessionRecord session = env.sessionRepository.bindNewSession(sourceKey);

        File file = FileUtil.file(env.appConfig.getRuntime().getCacheDir(), "sample.txt");
        FileUtil.writeUtf8String("v1", file);
        env.checkpointService.createCheckpoint(
                sourceKey, session.getSessionId(), Collections.singletonList(file));
        FileUtil.writeUtf8String("v2", file);

        env.checkpointService.rollbackLatest(sourceKey);
        assertThat(FileUtil.readUtf8String(file)).isEqualTo("v1");
    }
}
