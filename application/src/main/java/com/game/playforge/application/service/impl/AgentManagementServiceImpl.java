package com.game.playforge.application.service.impl;

import com.game.playforge.application.service.AgentManagementService;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentSkill;
import com.game.playforge.domain.repository.AgentDefinitionRepository;
import com.game.playforge.domain.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final AgentSkillRepository agentSkillRepository;

    @Override
    public List<AgentDefinition> listAgents() {
        log.debug("查询所有启用的Agent");
        return agentDefinitionRepository.findAllActive();
    }

    @Override
    public AgentDefinition getAgent(Long id) {
        log.debug("获取Agent详情, id={}", id);
        AgentDefinition definition = agentDefinitionRepository.findById(id);
        if (definition == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        return definition;
    }

    @Override
    public List<AgentSkill> listSkills() {
        log.debug("查询所有启用的技能");
        return agentSkillRepository.findAllActive();
    }
}
