package com.game.playforge.application.service.agent;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * Agent流式聊天代理接口
 * <p>
 * 由LangChain4J {@code AiServices} 动态代理实现，返回 {@link TokenStream} 支持流式输出。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentStreamingChatService {

    /**
     * 流式聊天
     *
     * @param userMessage 用户消息
     * @return Token流
     */
    TokenStream chat(@UserMessage String userMessage);
}
