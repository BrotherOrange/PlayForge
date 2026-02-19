package com.game.playforge.api.controller;

import com.game.playforge.api.dto.request.CreateAgentRequest;
import com.game.playforge.api.dto.request.CreateAgentWithThreadRequest;
import com.game.playforge.api.dto.response.AgentDefinitionResponse;
import com.game.playforge.api.dto.response.CreateAgentWithThreadResponse;
import com.game.playforge.api.mapper.AgentDefinitionMapper;
import com.game.playforge.api.mapper.AgentThreadMapper;
import com.game.playforge.application.service.AgentManagementService;
import com.game.playforge.application.service.AgentManagementService.AgentWithThread;
import com.game.playforge.application.service.UserService;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.User;
import com.game.playforge.domain.repository.AgentThreadRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent控制器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentManagementService agentManagementService;
    private final AgentThreadRepository agentThreadRepository;
    private final UserService userService;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentThreadMapper agentThreadMapper;

    private void requireAdmin(Long userId) {
        User user = userService.getProfile(userId);
        if (!Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new BusinessException(ResultCode.ADMIN_REQUIRED);
        }
    }

    /**
     * 列出当前用户的所有Agent（会话）
     */
    @GetMapping
    public ApiResult<List<AgentDefinitionResponse>> listAgents(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("列出用户Agent, userId={}", userId);
        List<AgentDefinition> agents = agentManagementService.listAgents(userId);
        if (agents.isEmpty()) {
            return ApiResult.success(List.of());
        }

        List<Long> agentIds = agents.stream().map(AgentDefinition::getId).collect(Collectors.toList());
        Map<Long, Long> latestThreadByAgent =
                agentThreadRepository.findLatestThreadIdsByAgentIds(userId, agentIds);

        List<AgentDefinitionResponse> responses = agents.stream()
                .map(agent -> {
                    AgentDefinitionResponse response = agentDefinitionMapper.toResponse(agent);
                    response.setThreadId(latestThreadByAgent.get(agent.getId()));
                    return response;
                })
                .toList();
        return ApiResult.success(responses);
    }

    /**
     * 获取Agent详情
     */
    @GetMapping("/{id}")
    public ApiResult<AgentDefinitionResponse> getAgent(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("获取Agent详情, userId={}, id={}", userId, id);
        AgentDefinition agent = agentManagementService.getAgent(userId, id);
        return ApiResult.success(agentDefinitionMapper.toResponse(agent));
    }

    /**
     * 创建Agent定义
     */
    @PostMapping
    public ApiResult<AgentDefinitionResponse> createAgent(
            HttpServletRequest request,
            @Valid @RequestBody CreateAgentRequest createRequest) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        requireAdmin(userId);
        log.info("创建Agent, userId={}, name={}", userId, createRequest.getName());
        AgentDefinition definition = agentDefinitionMapper.fromCreateRequest(createRequest);
        AgentDefinition created = agentManagementService.createAgent(userId, definition);
        return ApiResult.success(agentDefinitionMapper.toResponse(created));
    }

    /**
     * 原子创建Agent + Thread
     */
    @PostMapping("/with-thread")
    public ApiResult<CreateAgentWithThreadResponse> createAgentWithThread(
            HttpServletRequest request,
            @Valid @RequestBody CreateAgentWithThreadRequest createRequest) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        requireAdmin(userId);
        log.info("创建Agent+Thread, userId={}, provider={}, model={}",
                userId, createRequest.getProvider(), createRequest.getModelName());
        AgentWithThread result = agentManagementService.createAgentWithThread(
                userId, createRequest.getProvider(), createRequest.getModelName(), createRequest.getDisplayName());

        AgentDefinitionResponse agentResponse = agentDefinitionMapper.toResponse(result.agent());
        agentResponse.setThreadId(result.thread().getId());

        CreateAgentWithThreadResponse response = new CreateAgentWithThreadResponse();
        response.setAgent(agentResponse);
        response.setThread(agentThreadMapper.toResponse(result.thread()));
        return ApiResult.success(response);
    }

    /**
     * 删除Agent（软删除）
     */
    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteAgent(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        requireAdmin(userId);
        log.info("删除Agent, userId={}, agentId={}", userId, id);
        agentManagementService.deleteAgent(userId, id);
        return ApiResult.success(null);
    }

}
