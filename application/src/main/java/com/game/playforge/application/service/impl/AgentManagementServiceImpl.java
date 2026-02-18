package com.game.playforge.application.service.impl;

import com.game.playforge.application.service.AgentManagementService;
import com.game.playforge.application.service.agent.SubAgentService;
import com.game.playforge.common.enums.ModelProvider;
import com.game.playforge.common.enums.ThreadStatus;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentThread;
import com.game.playforge.domain.repository.AgentDefinitionRepository;
import com.game.playforge.domain.repository.AgentThreadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent管理服务实现
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentManagementServiceImpl implements AgentManagementService {

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentThreadRepository agentThreadRepository;
    private final SubAgentService subAgentService;

    private static final String LEAD_AGENT_PROMPT_REF = "agents/lead-designer.txt";
    private static final String LEAD_AGENT_TOOL_NAMES = "subAgentTool";


    @Override
    public List<AgentDefinition> listAgents(Long userId) {
        log.debug("查询用户的Agent, userId={}", userId);
        return agentDefinitionRepository.findByUserId(userId);
    }

    @Override
    public AgentDefinition getAgent(Long userId, Long id) {
        log.debug("获取Agent详情, userId={}, id={}", userId, id);
        AgentDefinition definition = agentDefinitionRepository.findById(id);
        if (definition == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        if (!userId.equals(definition.getUserId())) {
            throw new BusinessException(ResultCode.AGENT_ACCESS_DENIED);
        }
        return definition;
    }

    @Override
    public AgentDefinition createAgent(Long userId, AgentDefinition agentDefinition) {
        log.info("创建Agent定义, userId={}, name={}", userId, agentDefinition.getName());
        agentDefinition.setUserId(userId);
        agentDefinitionRepository.insert(agentDefinition);
        return agentDefinition;
    }

    @Override
    @Transactional
    public AgentWithThread createAgentWithThread(Long userId, String provider, String modelName, String displayName) {
        log.info("原子创建Agent+Thread, userId={}, provider={}, model={}", userId, provider, modelName);
        ModelProvider providerEnum = parseProvider(provider);
        String normalizedProvider = providerEnum.getValue();

        // 创建 Agent
        AgentDefinition agent = new AgentDefinition();
        agent.setUserId(userId);
        agent.setName(normalizedProvider + "-" + modelName + "-" + UUID.randomUUID().toString().substring(0, 8));
        agent.setDisplayName(displayName != null ? displayName : modelName);
        agent.setProvider(normalizedProvider);
        agent.setModelName(modelName);
        agent.setSystemPromptRef(LEAD_AGENT_PROMPT_REF);
        agent.setToolNames(LEAD_AGENT_TOOL_NAMES);
        agent.setMemoryWindowSize(20);
        agent.setTemperature(0.7);
        agent.setMaxTokens(32768);
        agent.setIsActive(true);
        agentDefinitionRepository.insert(agent);

        // 创建 Thread
        AgentThread thread = new AgentThread();
        thread.setAgentId(agent.getId());
        thread.setUserId(userId);
        thread.setTitle("New Chat");
        thread.setStatus(ThreadStatus.ACTIVE.name());
        thread.setMessageCount(0);
        thread.setTotalTokensUsed(0L);
        agentThreadRepository.insert(thread);

        log.info("原子创建Agent+Thread成功, agentId={}, threadId={}", agent.getId(), thread.getId());
        return new AgentWithThread(agent, thread);
    }

    @Override
    @Transactional
    public void deleteAgent(Long userId, Long agentId) {
        log.info("软删除Agent, userId={}, agentId={}", userId, agentId);
        AgentDefinition agent = agentDefinitionRepository.findById(agentId);
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        if (!userId.equals(agent.getUserId())) {
            throw new BusinessException(ResultCode.AGENT_ACCESS_DENIED);
        }

        // 删除Lead Agent时，级联销毁其所有子Agent，避免产生不可见孤儿Agent
        if (agent.getParentThreadId() == null) {
            List<AgentThread> leadThreads = agentThreadRepository.findByUserIdAndAgentId(userId, agentId);
            for (AgentThread leadThread : leadThreads) {
                subAgentService.destroyAllByParentThread(userId, leadThread.getId());
            }
        }

        agent.setIsActive(false);
        agentDefinitionRepository.update(agent);
        log.info("软删除Agent成功, agentId={}", agentId);
    }

    private ModelProvider parseProvider(String provider) {
        try {
            return ModelProvider.valueOf(provider.toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "不支持的模型供应商");
        }
    }
}
