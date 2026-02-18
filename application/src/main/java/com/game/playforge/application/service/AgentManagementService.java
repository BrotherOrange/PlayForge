package com.game.playforge.application.service;

import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentSkill;
import com.game.playforge.domain.model.AgentThread;

import java.util.List;

/**
 * Agent管理服务接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentManagementService {

    /**
     * 列出用户的所有启用Agent
     *
     * @param userId 用户ID
     * @return Agent定义列表
     */
    List<AgentDefinition> listAgents(Long userId);

    /**
     * 获取Agent详情
     *
     * @param userId 用户ID（用于校验归属）
     * @param id Agent定义ID
     * @return Agent定义
     */
    AgentDefinition getAgent(Long userId, Long id);

    /**
     * 创建Agent定义（绑定用户）
     *
     * @param userId          用户ID
     * @param agentDefinition Agent定义实体
     * @return 创建后的Agent定义
     */
    AgentDefinition createAgent(Long userId, AgentDefinition agentDefinition);

    /**
     * 原子创建Agent + Thread
     *
     * @param userId   用户ID
     * @param provider AI供应商
     * @param modelName 模型名称
     * @param displayName 显示名称（可选）
     * @return 创建的Agent和Thread
     */
    AgentWithThread createAgentWithThread(Long userId, String provider, String modelName, String displayName);

    /**
     * 软删除Agent（设置isActive=false）
     *
     * @param userId  用户ID（校验归属）
     * @param agentId Agent ID
     */
    void deleteAgent(Long userId, Long agentId);

    /**
     * 列出所有启用的技能
     *
     * @return 技能列表
     */
    List<AgentSkill> listSkills();

    /**
     * 创建技能
     *
     * @param agentSkill 技能实体
     * @return 创建后的技能
     */
    AgentSkill createSkill(AgentSkill agentSkill);

    /**
     * Agent + Thread 组合结果
     */
    record AgentWithThread(AgentDefinition agent, AgentThread thread) {}
}
