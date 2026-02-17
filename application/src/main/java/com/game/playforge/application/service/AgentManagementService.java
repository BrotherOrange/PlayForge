package com.game.playforge.application.service;

import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentSkill;

import java.util.List;

/**
 * Agent管理服务接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentManagementService {

    /**
     * 列出所有启用的Agent
     *
     * @return Agent定义列表
     */
    List<AgentDefinition> listAgents();

    /**
     * 获取Agent详情
     *
     * @param id Agent定义ID
     * @return Agent定义
     */
    AgentDefinition getAgent(Long id);

    /**
     * 列出所有启用的技能
     *
     * @return 技能列表
     */
    List<AgentSkill> listSkills();
}
