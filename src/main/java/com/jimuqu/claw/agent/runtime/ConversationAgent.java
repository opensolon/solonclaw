package com.jimuqu.claw.agent.runtime;

import java.util.function.Consumer;

/**
 * 抽象一次会话执行所需的 Agent 能力。
 */
public interface ConversationAgent {
    /**
     * 执行一次会话请求，并在执行过程中回调进度文本。
     *
     * @param request 会话执行请求
     * @param progressConsumer 进度回调
     * @return 最终回复文本
     * @throws Throwable 执行过程中的异常
     */
    String execute(ConversationExecutionRequest request, Consumer<String> progressConsumer) throws Throwable;
}
