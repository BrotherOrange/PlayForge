package com.game.playforge.api.config;

import com.game.playforge.api.websocket.AgentChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置
 * <p>
 * 注册Agent聊天WebSocket处理器到 {@code /ws/agent-chat} 路径。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentChatWebSocketHandler agentChatWebSocketHandler;
    @Value("${app.security.websocket-allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentChatWebSocketHandler, "/ws/agent-chat")
                .setAllowedOrigins(allowedOrigins);
    }
}
