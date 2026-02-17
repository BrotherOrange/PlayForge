package com.game.playforge.api.controller;

import com.game.playforge.api.dto.response.AgentDefinitionResponse;
import com.game.playforge.application.service.AgentManagementService;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.domain.model.AgentDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 列出所有可用Agent
     *
     * @return Agent列表
     */
    @GetMapping
    public ApiResult<List<AgentDefinitionResponse>> listAgents() {
        log.info("列出所有可用Agent");
        List<AgentDefinition> agents = agentManagementService.listAgents();
        List<AgentDefinitionResponse> responses = agents.stream()
                .map(this::toResponse)
                .toList();
        return ApiResult.success(responses);
    }

    /**
     * 获取Agent详情
     *
     * @param id Agent定义ID
     * @return Agent详情
     */
    @GetMapping("/{id}")
    public ApiResult<AgentDefinitionResponse> getAgent(@PathVariable Long id) {
        log.info("获取Agent详情, id={}", id);
        AgentDefinition agent = agentManagementService.getAgent(id);
        return ApiResult.success(toResponse(agent));
    }

    private AgentDefinitionResponse toResponse(AgentDefinition agent) {
        AgentDefinitionResponse response = new AgentDefinitionResponse();
        response.setId(agent.getId());
        response.setName(agent.getName());
        response.setDisplayName(agent.getDisplayName());
        response.setDescription(agent.getDescription());
        response.setProvider(agent.getProvider());
        response.setModelName(agent.getModelName());
        response.setCreatedAt(agent.getCreatedAt());
        return response;
    }
}
