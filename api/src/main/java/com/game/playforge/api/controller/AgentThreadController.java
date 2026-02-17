package com.game.playforge.api.controller;

import com.game.playforge.api.dto.request.ChatRequest;
import com.game.playforge.api.dto.request.CreateThreadRequest;
import com.game.playforge.api.dto.response.AgentMessageResponse;
import com.game.playforge.api.dto.response.AgentThreadResponse;
import com.game.playforge.api.dto.response.ChatResponse;
import com.game.playforge.application.dto.AgentChatResponse;
import com.game.playforge.application.service.AgentChatAppService;
import com.game.playforge.application.service.AgentThreadService;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.domain.model.AgentMessage;
import com.game.playforge.domain.model.AgentThread;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent会话控制器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/agent-threads")
@RequiredArgsConstructor
public class AgentThreadController {

    private final AgentThreadService agentThreadService;
    private final AgentChatAppService agentChatAppService;

    /**
     * 创建会话
     *
     * @param request       HTTP请求
     * @param createRequest 创建请求
     * @return 会话详情
     */
    @PostMapping
    public ApiResult<AgentThreadResponse> createThread(
            HttpServletRequest request,
            @Valid @RequestBody CreateThreadRequest createRequest) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("创建会话, userId={}, agentId={}", userId, createRequest.getAgentId());
        AgentThread thread = agentThreadService.createThread(
                userId, createRequest.getAgentId(), createRequest.getTitle());
        return ApiResult.success(toThreadResponse(thread));
    }

    /**
     * 列出用户的会话
     *
     * @param request HTTP请求
     * @param agentId Agent定义ID（可选过滤）
     * @return 会话列表
     */
    @GetMapping
    public ApiResult<List<AgentThreadResponse>> listThreads(
            HttpServletRequest request,
            @RequestParam(required = false) Long agentId) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("列出会话, userId={}, agentId={}", userId, agentId);
        List<AgentThread> threads = agentThreadService.listThreads(userId, agentId);
        List<AgentThreadResponse> responses = threads.stream()
                .map(this::toThreadResponse)
                .toList();
        return ApiResult.success(responses);
    }

    /**
     * 获取会话详情
     *
     * @param request HTTP请求
     * @param id      会话ID
     * @return 会话详情
     */
    @GetMapping("/{id}")
    public ApiResult<AgentThreadResponse> getThread(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("获取会话详情, userId={}, threadId={}", userId, id);
        AgentThread thread = agentThreadService.getThread(userId, id);
        return ApiResult.success(toThreadResponse(thread));
    }

    /**
     * 删除会话
     *
     * @param request HTTP请求
     * @param id      会话ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteThread(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("删除会话, userId={}, threadId={}", userId, id);
        agentThreadService.deleteThread(userId, id);
        return ApiResult.success();
    }

    /**
     * 获取消息历史
     *
     * @param request HTTP请求
     * @param id      会话ID
     * @param limit   查询条数
     * @param offset  偏移量
     * @return 消息列表
     */
    @GetMapping("/{id}/messages")
    public ApiResult<List<AgentMessageResponse>> getMessages(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("获取消息历史, userId={}, threadId={}, limit={}, offset={}", userId, id, limit, offset);
        List<AgentMessage> messages = agentThreadService.getMessageHistory(userId, id, limit, offset);
        List<AgentMessageResponse> responses = messages.stream()
                .map(this::toMessageResponse)
                .toList();
        return ApiResult.success(responses);
    }

    /**
     * 同步聊天
     *
     * @param request     HTTP请求
     * @param id          会话ID
     * @param chatRequest 聊天请求
     * @return 聊天响应
     */
    @PostMapping("/{id}/chat")
    public ApiResult<ChatResponse> chat(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody ChatRequest chatRequest) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("同步聊天, userId={}, threadId={}", userId, id);
        AgentChatResponse response = agentChatAppService.chat(userId, id, chatRequest.getMessage());
        return ApiResult.success(new ChatResponse(response.threadId(), response.content()));
    }

    private AgentThreadResponse toThreadResponse(AgentThread thread) {
        AgentThreadResponse response = new AgentThreadResponse();
        response.setId(thread.getId());
        response.setAgentId(thread.getAgentId());
        response.setTitle(thread.getTitle());
        response.setStatus(thread.getStatus());
        response.setMessageCount(thread.getMessageCount());
        response.setLastMessageAt(thread.getLastMessageAt());
        response.setCreatedAt(thread.getCreatedAt());
        return response;
    }

    private AgentMessageResponse toMessageResponse(AgentMessage message) {
        AgentMessageResponse response = new AgentMessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setToolName(message.getToolName());
        response.setTokenCount(message.getTokenCount());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }
}
