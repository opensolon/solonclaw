package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.web.DashboardAuthService;
import org.junit.jupiter.api.Test;

public class DashboardAuthServiceTest {
    @Test
    void shouldUseAdminWhenAccessTokenIsBlank() {
        AppConfig config = new AppConfig();
        config.getDashboard().setAccessToken("");

        DashboardAuthService authService = new DashboardAuthService(config);

        assertThat(authService.sessionToken()).isEqualTo("admin");
    }
}
