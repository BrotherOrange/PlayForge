package com.game.playforge.application.service;

import com.game.playforge.application.dto.AgentChatResponse;
import reactor.core.publisher.Flux;

/**
 * Agent聊天应用服务接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentChatAppService {

    /**
     * 同步聊天
     *
     * @param userId   用户ID
     * @param threadId 会话ID
     * @param message  用户消息
     * @return 聊天响应
     */
    AgentChatResponse chat(Long userId, Long threadId, String message);

    /**
     * 流式聊天
     *
     * @param userId   用户ID
     * @param threadId 会话ID
     * @param message  用户消息
     * @return Token流
     */
    Flux<String> chatStream(Long userId, Long threadId, String message);
}
