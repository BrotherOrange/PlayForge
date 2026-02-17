package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.AgentDefinition;

import java.util.List;

/**
 * Agent定义仓储接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentDefinitionRepository {

    /**
     * 根据ID查询Agent定义
     *
     * @param id Agent定义ID
     * @return Agent定义，不存在返回null
     */
    AgentDefinition findById(Long id);

    /**
     * 根据唯一标识查询Agent定义
     *
     * @param name Agent唯一标识
     * @return Agent定义，不存在返回null
     */
    AgentDefinition findByName(String name);

    /**
     * 查询所有启用的Agent定义
     *
     * @return 启用的Agent定义列表
     */
    List<AgentDefinition> findAllActive();

    /**
     * 根据用户ID查询启用的Agent定义
     *
     * @param userId 用户ID
     * @return 该用户的Agent定义列表
     */
    List<AgentDefinition> findByUserId(Long userId);

    /**
     * 新增Agent定义
     *
     * @param agentDefinition Agent定义实体
     */
    void insert(AgentDefinition agentDefinition);

    /**
     * 更新Agent定义
     *
     * @param agentDefinition Agent定义实体
     */
    void update(AgentDefinition agentDefinition);
}
