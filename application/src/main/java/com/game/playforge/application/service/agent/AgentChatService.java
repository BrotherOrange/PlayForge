package com.game.playforge.application.service.agent;

import dev.langchain4j.service.UserMessage;

/**
 * Agent同步聊天代理接口
 * <p>
 * 由LangChain4J {@code AiServices} 动态代理实现。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentChatService {

    /**
     * 同步聊天
     *
     * @param userMessage 用户消息
     * @return 助手回复
     */
    String chat(@UserMessage String userMessage);
}
