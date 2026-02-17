package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.AgentSkill;

import java.util.List;

/**
 * Agent技能仓储接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentSkillRepository {

    /**
     * 根据ID查询技能
     *
     * @param id 技能ID
     * @return 技能实体，不存在返回null
     */
    AgentSkill findById(Long id);

    /**
     * 根据唯一标识查询技能
     *
     * @param name 技能唯一标识
     * @return 技能实体，不存在返回null
     */
    AgentSkill findByName(String name);

    /**
     * 查询所有启用的技能
     *
     * @return 启用的技能列表
     */
    List<AgentSkill> findAllActive();

    /**
     * 根据ID列表批量查询技能
     *
     * @param ids 技能ID列表
     * @return 技能列表
     */
    List<AgentSkill> findByIds(List<Long> ids);

    /**
     * 新增技能
     *
     * @param agentSkill 技能实体
     */
    void insert(AgentSkill agentSkill);

    /**
     * 更新技能
     *
     * @param agentSkill 技能实体
     */
    void update(AgentSkill agentSkill);
}
