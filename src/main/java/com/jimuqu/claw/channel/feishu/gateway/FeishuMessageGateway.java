package com.jimuqu.claw.channel.feishu.gateway;

/**
 * 抽象飞书消息创建与更新能力，便于在单元测试中替换底层 SDK。
 */
public interface FeishuMessageGateway {
    /**
     * 向指定 chat 发送一条交互式卡片消息。
     *
     * @param chatId chat 标识
     * @param cardContent 卡片 JSON
     * @return 新创建的消息 ID
     * @throws Exception 发送异常
     */
    String createCardMessage(String chatId, String cardContent) throws Exception;

    /**
     * 更新一条已发送的卡片消息。
     *
     * @param messageId 消息 ID
     * @param cardContent 卡片 JSON
     * @throws Exception 更新异常
     */
    void patchCardMessage(String messageId, String cardContent) throws Exception;
}

