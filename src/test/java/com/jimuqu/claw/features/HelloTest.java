package com.jimuqu.claw.features;

import com.jimuqu.claw.SolonClawApp;
import org.junit.jupiter.api.Test;
import org.noear.solon.test.HttpTester;
import org.noear.solon.test.SolonTest;

import java.io.IOException;

/**
 * Http请求接口 Test示例
 */
@SolonTest(SolonClawApp.class)
public class HelloTest extends HttpTester {

    @Test
    public void hello() throws IOException {
        //assert path("/hello?name=world").get().contains("world");
        // assert path("/hello?name=solon").get().contains("solon");
    }

}