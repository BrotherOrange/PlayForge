package com.game.playforge.api.controller;

import com.game.playforge.api.dto.request.ChatRequest;
import com.game.playforge.api.dto.request.CreateThreadRequest;
import com.game.playforge.api.dto.response.AgentMessageResponse;
import com.game.playforge.api.dto.response.AgentThreadResponse;
import com.game.playforge.api.dto.response.ChatResponse;
import com.game.playforge.api.mapper.AgentMessageMapper;
import com.game.playforge.api.mapper.AgentThreadMapper;
import com.game.playforge.application.dto.AgentChatResponse;
import com.game.playforge.application.dto.AgentStreamEvent;
import com.game.playforge.application.service.AgentChatAppService;
import com.game.playforge.application.service.AgentThreadService;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.domain.model.AgentMessage;
import com.game.playforge.domain.model.AgentThread;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

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
@Validated
public class AgentThreadController {

    private final AgentThreadService agentThreadService;
    private final AgentChatAppService agentChatAppService;
    private final AgentThreadMapper agentThreadMapper;
    private final AgentMessageMapper agentMessageMapper;

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
        return ApiResult.success(agentThreadMapper.toResponse(thread));
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
        return ApiResult.success(agentThreadMapper.toResponseList(threads));
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
        return ApiResult.success(agentThreadMapper.toResponse(thread));
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
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "limit最小为1") @Max(value = 200, message = "limit最大为200") int limit,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "offset不能小于0") int offset) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("获取消息历史, userId={}, threadId={}, limit={}, offset={}", userId, id, limit, offset);
        List<AgentMessage> messages = agentThreadService.getMessageHistory(userId, id, limit, offset);
        return ApiResult.success(agentMessageMapper.toResponseList(messages));
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

    /**
     * 带进度的聊天（SSE）
     * <p>
     * 返回Server-Sent Events流，包含子Agent操作进度与Lead Agent流式输出。
     * 事件类型：token（文本分片）、thinking（思考分片）、progress（操作进度）、error（错误）
     * </p>
     *
     * @param request     HTTP请求
     * @param id          会话ID
     * @param chatRequest 聊天请求
     * @return SSE事件流
     */
    @PostMapping("/{id}/chat-progress")
    public SseEmitter chatWithProgress(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody ChatRequest chatRequest) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("带进度聊天(SSE), userId={}, threadId={}", userId, id);

        SseEmitter emitter = new SseEmitter(300_000L);

        agentChatAppService.chatWithProgress(userId, id, chatRequest.getMessage())
                .subscribe(
                        event -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name(event.type())
                                        .data(event));
                            } catch (IOException e) {
                                log.warn("SSE发送失败, threadId={}", id, e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("SSE流错误, threadId={}", id, error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(new AgentStreamEvent("error", error.getMessage())));
                            } catch (IOException ignored) {
                            }
                            emitter.complete();
                        },
                        emitter::complete
                );

        return emitter;
    }
}
