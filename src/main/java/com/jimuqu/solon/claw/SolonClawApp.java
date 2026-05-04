package com.jimuqu.solon.claw;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

/** 应用启动入口。 */
@SolonMain
public class SolonClawApp {
    private static volatile String[] startupArgs = new String[0];

    /**
     * 启动 Solon 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        startupArgs = args == null ? new String[0] : args.clone();
        Solon.start(SolonClawApp.class, args);
    }

    public static String[] startupArgs() {
        return startupArgs == null ? new String[0] : startupArgs.clone();
    }
}
