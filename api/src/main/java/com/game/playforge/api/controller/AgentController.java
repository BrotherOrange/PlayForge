package com.game.playforge.api.controller;

import com.game.playforge.api.dto.request.CreateAgentRequest;
import com.game.playforge.api.dto.request.CreateAgentWithThreadRequest;
import com.game.playforge.api.dto.request.CreateSkillRequest;
import com.game.playforge.api.dto.response.AgentDefinitionResponse;
import com.game.playforge.api.dto.response.AgentThreadResponse;
import com.game.playforge.api.dto.response.CreateAgentWithThreadResponse;
import com.game.playforge.api.mapper.AgentDefinitionMapper;
import com.game.playforge.api.mapper.AgentSkillMapper;
import com.game.playforge.api.mapper.AgentThreadMapper;
import com.game.playforge.application.service.AgentManagementService;
import com.game.playforge.application.service.AgentManagementService.AgentWithThread;
import com.game.playforge.application.service.UserService;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentSkill;
import com.game.playforge.domain.model.AgentThread;
import com.game.playforge.domain.model.User;
import com.game.playforge.domain.repository.AgentThreadRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final AgentSkillMapper agentSkillMapper;

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
        List<AgentDefinitionResponse> responses = agents.stream()
                .map(agent -> toResponseWithThread(agent, userId))
                .toList();
        return ApiResult.success(responses);
    }

    /**
     * 获取Agent详情
     */
    @GetMapping("/{id}")
    public ApiResult<AgentDefinitionResponse> getAgent(@PathVariable Long id) {
        log.info("获取Agent详情, id={}", id);
        AgentDefinition agent = agentManagementService.getAgent(id);
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

    /**
     * 创建技能
     */
    @PostMapping("/skills")
    public ApiResult<AgentSkill> createSkill(@Valid @RequestBody CreateSkillRequest request) {
        log.info("创建技能, name={}", request.getName());
        AgentSkill skill = agentSkillMapper.fromCreateRequest(request);
        AgentSkill created = agentManagementService.createSkill(skill);
        return ApiResult.success(created);
    }

    private AgentDefinitionResponse toResponseWithThread(AgentDefinition agent, Long userId) {
        AgentDefinitionResponse response = agentDefinitionMapper.toResponse(agent);
        List<AgentThread> threads = agentThreadRepository.findByUserIdAndAgentId(userId, agent.getId());
        if (!threads.isEmpty()) {
            response.setThreadId(threads.getFirst().getId());
        }
        return response;
    }
}
