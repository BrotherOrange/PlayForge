package com.game.playforge.application.dto;

/**
 * Agent流式事件
 *
 * @param type    事件类型
 * @param content 事件内容
 */
public record AgentStreamEvent(String type, String content) {

    public static final String TYPE_TOKEN = "token";
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_PROGRESS = "progress";
    public static final String TYPE_RESPONSE = "response";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    public static AgentStreamEvent token(String content) {
        return new AgentStreamEvent(TYPE_TOKEN, content);
    }

    public static AgentStreamEvent thinking(String content) {
        return new AgentStreamEvent(TYPE_THINKING, content);
    }

    public static AgentStreamEvent progress(String content) {
        return new AgentStreamEvent(TYPE_PROGRESS, content);
    }

    public static AgentStreamEvent response(String content) {
        return new AgentStreamEvent(TYPE_RESPONSE, content);
    }

    public static AgentStreamEvent done() {
        return new AgentStreamEvent(TYPE_DONE, "");
    }

    public static AgentStreamEvent error(String content) {
        return new AgentStreamEvent(TYPE_ERROR, content);
    }
}
