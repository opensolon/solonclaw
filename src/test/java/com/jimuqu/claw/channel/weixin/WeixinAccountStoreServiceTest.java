package com.jimuqu.claw.channel.weixin;

import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;
import com.jimuqu.claw.channel.weixin.service.WeixinAccountStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WeixinAccountStoreServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAccountAndCursorUnderWorkspace() {
        WeixinAccountStoreService storeService = new WeixinAccountStoreService(new AgentWorkspaceService(tempDir.toString()));
        WeixinAccount account = new WeixinAccount();
        account.setAccountId("bot-im-bot");
        account.setRemoteAccountId("bot@im.bot");
        account.setBaseUrl("https://ilinkai.weixin.qq.com");
        account.setToken("token-1");
        account.setUserId("user-1");

        storeService.saveAccount(account);
        storeService.saveCursor("bot-im-bot", "cursor-1");

        WeixinAccount restored = storeService.loadAccount("bot-im-bot");
        assertNotNull(restored);
        assertEquals("bot@im.bot", restored.getRemoteAccountId());
        assertEquals("cursor-1", storeService.loadCursor("bot-im-bot"));
        assertEquals("bot-im-bot", storeService.listAccountIds().get(0));
    }
}
