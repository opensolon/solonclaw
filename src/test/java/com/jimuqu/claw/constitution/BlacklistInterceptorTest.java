package com.jimuqu.claw.constitution;

import com.jimuqu.claw.config.props.BlacklistProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证黑名单拦截器的匹配规则。
 */
class BlacklistInterceptorTest {

    // ===== 命令关键词 =====

    @Test
    void blocksBlacklistedCommand() {
        BlacklistProperties props = new BlacklistProperties();
        props.setExtraCommands(Arrays.asList("sudo", "reboot", "shutdown"));
        BlacklistInterceptor interceptor = new BlacklistInterceptor(props);

        assertNotNull(interceptor.evaluate(null, args("sudo apt install nginx")));
        assertNotNull(interceptor.evaluate(null, args("reboot")));
        assertNotNull(interceptor.evaluate(null, args("shutdown -h now")));
    }

    @Test
    void allowsNonBlacklistedCommand() {
        BlacklistProperties props = new BlacklistProperties();
        props.setExtraCommands(Collections.singletonList("sudo"));
        BlacklistInterceptor interceptor = new BlacklistInterceptor(props);

        assertNull(interceptor.evaluate(null, args("ls -la")));
        assertNull(interceptor.evaluate(null, args("cat README.md")));
        assertNull(interceptor.evaluate(null, args("pwd")));
        assertNull(interceptor.evaluate(null, args("npm install express")));
    }

    // ===== 路径 =====

    @Test
    void blocksBlacklistedPath() {
        BlacklistProperties props = new BlacklistProperties();
        props.setExtraPaths(Arrays.asList("/etc/shadow", "/.ssh/"));
        BlacklistInterceptor interceptor = new BlacklistInterceptor(props);

        assertNotNull(interceptor.evaluate(null, args("cat /etc/shadow")));
        assertNotNull(interceptor.evaluate(null, args("ls ~/.ssh/id_rsa")));
    }

    @Test
    void allowsNonBlacklistedPath() {
        BlacklistProperties props = new BlacklistProperties();
        props.setExtraPaths(Collections.singletonList("/etc/shadow"));
        BlacklistInterceptor interceptor = new BlacklistInterceptor(props);

        assertNull(interceptor.evaluate(null, args("cat /etc/hosts")));
        assertNull(interceptor.evaluate(null, args("ls /home/user/")));
    }

    // ===== 正则模式 =====

    @Test
    void blocksBlacklistedPattern() {
        BlacklistProperties props = new BlacklistProperties();
        props.setExtraPatterns(Arrays.asList(
                ".*\\brm\\s+-rf\\s+/\\s*$",
                "(?i).*\\bformat\\s+[A-Z]:.*"
        ));
        BlacklistInterceptor interceptor = new BlacklistInterceptor(props);

        assertNotNull(interceptor.evaluate(null, args("rm -rf /")));
        assertNotNull(interceptor.evaluate(null, args("FORMAT C:")));
    }

    @Test
    void allowsNonMatchingPattern() {
        BlacklistProperties props = new BlacklistProperties();
        props.setExtraPatterns(Collections.singletonList(".*\\brm\\s+-rf\\s+/\\s*$"));
        BlacklistInterceptor interceptor = new BlacklistInterceptor(props);

        assertNull(interceptor.evaluate(null, args("rm -rf ./node_modules")));
        assertNull(interceptor.evaluate(null, args("rm file.txt")));
    }

    // ===== 禁用时 =====

    @Test
    void allowsEverythingWhenDisabled() {
        BlacklistInterceptor interceptor = new BlacklistInterceptor(null);

        assertNull(interceptor.evaluate(null, args("sudo rm -rf /")));
        assertNull(interceptor.evaluate(null, args("cat /etc/shadow")));
    }

    // ===== 空/null 命令 =====

    @Test
    void allowsEmptyAndNullCommand() {
        BlacklistProperties props = new BlacklistProperties();
        props.setExtraCommands(Collections.singletonList("sudo"));
        BlacklistInterceptor interceptor = new BlacklistInterceptor(props);

        assertNull(interceptor.evaluate(null, args("")));
        assertNull(interceptor.evaluate(null, args(null)));
    }

    // ===== 辅助方法 =====

    private Map<String, Object> args(String command) {
        Map<String, Object> map = new HashMap<>();
        map.put("command", command);
        return map;
    }
}
