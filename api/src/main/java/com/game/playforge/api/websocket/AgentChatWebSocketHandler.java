package com.game.playforge.api.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.playforge.application.service.AgentChatAppService;
import com.game.playforge.application.service.UserService;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.domain.model.User;
import com.game.playforge.infrastructure.external.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Disposable;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent聊天WebSocket处理器
 * <p>
 * 处理WebSocket连接的AI聊天流式通信。
 * </p>
 * <p>
 * 客户端发送JSON:
 * <ul>
 *   <li>{@code {"type": "message", "content": "用户消息"}} — 发送聊天消息</li>
 *   <li>{@code {"type": "cancel"}} — 中断当前流式响应</li>
 * </ul>
 * <p>
 * 服务端发送JSON:
 * <ul>
 *   <li>{@code {"type": "token", "content": "部分内容"}} — 流式Token</li>
 *   <li>{@code {"type": "done"}} — 流式完成</li>
 *   <li>{@code {"type": "error", "content": "错误信息"}} — 错误</li>
 * </ul>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentChatWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private final AgentChatAppService agentChatAppService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_THREAD_ID = "threadId";
    private static final String ATTR_TRACE_ID = "traceId";
    private static final String GENERIC_ERROR_MESSAGE = "服务异常，请稍后重试";
    private static final String RATE_LIMIT_ERROR_MESSAGE = "模型请求过于频繁，请稍后重试";
    private final Map<String, Disposable> activeStreams = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        session.getAttributes().put(ATTR_TRACE_ID, traceId);
        MDC.put(AuthConstants.TRACE_ID_MDC_KEY, traceId);
        try {
            doAfterConnectionEstablished(session);
        } finally {
            MDC.remove(AuthConstants.TRACE_ID_MDC_KEY);
        }
    }

    private void doAfterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA.withReason("缺少URI"));
            return;
        }

        Map<String, String> params = UriComponentsBuilder.fromUri(uri).build()
                .getQueryParams().toSingleValueMap();

        String token = extractToken(session);
        String threadIdStr = params.get("threadId");

        if (token == null || threadIdStr == null) {
            session.close(CloseStatus.BAD_DATA.withReason("缺少认证token或threadId参数"));
            return;
        }

        if (!jwtUtil.isValid(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("token无效"));
            return;
        }

        Long userId = jwtUtil.parseUserId(token);
        Long threadId;
        try {
            threadId = Long.parseLong(threadIdStr);
        } catch (NumberFormatException e) {
            session.close(CloseStatus.BAD_DATA.withReason("threadId格式错误"));
            return;
        }

        // 仅管理员可使用聊天
        User user = userService.getProfile(userId);
        if (!Boolean.TRUE.equals(user.getIsAdmin())) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("需要管理员权限"));
            return;
        }

        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_THREAD_ID, threadId);

        log.info("WebSocket连接建立, sessionId={}, userId={}, threadId={}",
                session.getId(), userId, threadId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        setTraceId(session);
        try {
            Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
            Long threadId = (Long) session.getAttributes().get(ATTR_THREAD_ID);

            if (userId == null || threadId == null) {
                sendError(session, "未认证");
                return;
            }

            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.has("type") ? node.get("type").asText() : "";

            switch (type) {
                case "message" -> handleChatMessage(session, userId, threadId, node);
                case "cancel" -> handleCancel(session);
                default -> sendError(session, "未知消息类型: " + type);
            }
        } finally {
            MDC.remove(AuthConstants.TRACE_ID_MDC_KEY);
        }
    }

    private void handleChatMessage(WebSocketSession session, Long userId, Long threadId, JsonNode node)
            throws IOException {
        String content = node.has("content") ? node.get("content").asText() : "";
        if (content.isBlank()) {
            sendError(session, "消息内容不能为空");
            return;
        }

        log.info("WebSocket聊天, sessionId={}, userId={}, threadId={}", session.getId(), userId, threadId);

        // 取消之前的流
        cancelActiveStream(session.getId());

        try {
            Disposable disposable = agentChatAppService.chatStream(userId, threadId, content)
                    .subscribe(
                            token -> {
                                try {
                                    if (session.isOpen()) {
                                        String json = objectMapper.writeValueAsString(
                                                Map.of("type", "token", "content", token));
                                        session.sendMessage(new TextMessage(json));
                                    }
                                } catch (IOException e) {
                                    log.error("发送Token失败, sessionId={}", session.getId(), e);
                                }
                            },
                            error -> {
                                log.error("流式聊天错误, sessionId={}", session.getId(), error);
                                sendErrorSafe(session, resolveErrorMessage(error));
                                activeStreams.remove(session.getId());
                            },
                            () -> {
                                try {
                                    if (session.isOpen()) {
                                        String json = objectMapper.writeValueAsString(Map.of("type", "done"));
                                        session.sendMessage(new TextMessage(json));
                                    }
                                } catch (IOException e) {
                                    log.error("发送完成消息失败, sessionId={}", session.getId(), e);
                                }
                                activeStreams.remove(session.getId());
                            }
                    );

            activeStreams.put(session.getId(), disposable);
        } catch (Exception e) {
            log.error("启动流式聊天失败, sessionId={}", session.getId(), e);
            sendError(session, GENERIC_ERROR_MESSAGE);
        }
    }

    private void handleCancel(WebSocketSession session) {
        log.info("取消流式聊天, sessionId={}", session.getId());
        cancelActiveStream(session.getId());
    }

    private void cancelActiveStream(String sessionId) {
        Disposable disposable = activeStreams.remove(sessionId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.debug("已取消活跃流, sessionId={}", sessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        setTraceId(session);
        try {
            cancelActiveStream(session.getId());
            log.info("WebSocket连接关闭, sessionId={}, status={}", session.getId(), status);
        } finally {
            MDC.remove(AuthConstants.TRACE_ID_MDC_KEY);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        setTraceId(session);
        try {
            cancelActiveStream(session.getId());
            if (exception instanceof IOException) {
                log.warn("WebSocket传输中断, sessionId={}, cause={}", session.getId(), exception.getMessage());
            } else {
                log.error("WebSocket传输错误, sessionId={}", session.getId(), exception);
            }
        } finally {
            MDC.remove(AuthConstants.TRACE_ID_MDC_KEY);
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(
                    Map.of("type", "error", "content", errorMessage));
            session.sendMessage(new TextMessage(json));
        }
    }

    private void sendErrorSafe(WebSocketSession session, String errorMessage) {
        try {
            sendError(session, errorMessage);
        } catch (IOException e) {
            log.error("发送错误消息失败, sessionId={}", session.getId(), e);
        }
    }

    private void setTraceId(WebSocketSession session) {
        String traceId = (String) session.getAttributes().get(ATTR_TRACE_ID);
        if (traceId != null) {
            MDC.put(AuthConstants.TRACE_ID_MDC_KEY, traceId);
        }
    }

    private String resolveErrorMessage(Throwable error) {
        if (isRateLimitError(error)) {
            return RATE_LIMIT_ERROR_MESSAGE;
        }
        return GENERIC_ERROR_MESSAGE;
    }

    private boolean isRateLimitError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if ("RateLimitException".equals(cursor.getClass().getSimpleName())) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("rate_limit") || lower.contains("rate limit")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of("bearer");
    }

    private String extractToken(WebSocketSession session) {
        String protocolHeader = session.getHandshakeHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocolHeader != null && !protocolHeader.isBlank()) {
            String[] protocols = Arrays.stream(protocolHeader.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isBlank())
                    .toArray(String[]::new);
            if (protocols.length >= 2 && "bearer".equalsIgnoreCase(protocols[0])) {
                return protocols[1];
            }
        }

        String authorization = session.getHandshakeHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            return token.isBlank() ? null : token;
        }
        return null;
    }
}
