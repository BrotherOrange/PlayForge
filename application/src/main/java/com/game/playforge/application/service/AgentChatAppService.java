package com.game.playforge.application.service;

import com.game.playforge.application.dto.AgentChatResponse;
import com.game.playforge.application.dto.AgentStreamEvent;
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
     * @return 流式事件（文本Token/Thinking）
     */
    Flux<AgentStreamEvent> chatStream(Long userId, Long threadId, String message);

    /**
     * 带进度的聊天（SSE）
     * <p>
     * 返回进度事件流，包含子Agent操作进度和最终响应。
     * </p>
     *
     * @param userId   用户ID
     * @param threadId 会话ID
     * @param message  用户消息
     * @return 流式事件（progress/response/done/error）
     */
    Flux<AgentStreamEvent> chatWithProgress(Long userId, Long threadId, String message);
}
