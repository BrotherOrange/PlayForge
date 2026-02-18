package com.game.playforge.application.dto;

/**
 * Agent流式事件
 *
 * @param type    事件类型（token/thinking）
 * @param content 事件内容
 */
public record AgentStreamEvent(String type, String content) {

    public static final String TYPE_TOKEN = "token";
    public static final String TYPE_THINKING = "thinking";

    public static AgentStreamEvent token(String content) {
        return new AgentStreamEvent(TYPE_TOKEN, content);
    }

    public static AgentStreamEvent thinking(String content) {
        return new AgentStreamEvent(TYPE_THINKING, content);
    }
}
