package com.jimuqu.solonclaw;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

/**
 * SolonClaw 主入口
 * <p>
 * 基于 Solon 框架的轻量级 AI 助手服务
 *
 * @author SolonClaw
 */
@SolonMain
public class SolonClawApp {

    public static void main(String[] args) {
        Solon.start(SolonClawApp.class, args);
    }
}
