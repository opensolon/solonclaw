package com.jimuqu.claw;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.scheduling.annotation.EnableScheduling;

/**
 * 应用主入口类。
 */
@SolonMain
@EnableScheduling
public class SolonClawApp {
    /**
     * 启动 Solon 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        Solon.start(SolonClawApp.class, args);
    }
}
