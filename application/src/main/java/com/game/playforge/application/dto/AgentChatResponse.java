package com.game.playforge.application.dto;

/**
 * Agent聊天响应
 *
 * @param threadId 会话ID
 * @param content  回复内容
 * @author Richard Zhang
 * @since 1.0
 */
public record AgentChatResponse(Long threadId, String content) {
}
