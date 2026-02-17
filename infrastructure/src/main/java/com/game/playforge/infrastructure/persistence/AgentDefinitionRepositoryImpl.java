package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.repository.AgentDefinitionRepository;
import com.game.playforge.infrastructure.persistence.mapper.AgentDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent定义仓储实现
 * <p>
 * 基于MyBatis Plus的 {@link AgentDefinitionMapper} 实现持久化操作。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentDefinitionRepositoryImpl implements AgentDefinitionRepository {

    private final AgentDefinitionMapper agentDefinitionMapper;

    @Override
    public AgentDefinition findById(Long id) {
        log.debug("根据ID查询Agent定义, id={}", id);
        AgentDefinition definition = agentDefinitionMapper.selectById(id);
        log.debug("根据ID查询Agent定义结果, id={}, found={}", id, definition != null);
        return definition;
    }

    @Override
    public AgentDefinition findByName(String name) {
        log.debug("根据名称查询Agent定义, name={}", name);
        AgentDefinition definition = agentDefinitionMapper.selectOne(
                new LambdaQueryWrapper<AgentDefinition>()
                        .eq(AgentDefinition::getName, name)
                        .eq(AgentDefinition::getIsActive, true));
        log.debug("根据名称查询Agent定义结果, name={}, found={}", name, definition != null);
        return definition;
    }

    @Override
    public List<AgentDefinition> findAllActive() {
        log.debug("查询所有启用的Agent定义");
        List<AgentDefinition> definitions = agentDefinitionMapper.selectList(
                new LambdaQueryWrapper<AgentDefinition>()
                        .eq(AgentDefinition::getIsActive, true));
        log.debug("查询所有启用的Agent定义, count={}", definitions.size());
        return definitions;
    }

    @Override
    public void insert(AgentDefinition agentDefinition) {
        log.info("新增Agent定义, name={}", agentDefinition.getName());
        agentDefinitionMapper.insert(agentDefinition);
        log.info("新增Agent定义成功, id={}", agentDefinition.getId());
    }

    @Override
    public void update(AgentDefinition agentDefinition) {
        log.info("更新Agent定义, id={}", agentDefinition.getId());
        agentDefinitionMapper.updateById(agentDefinition);
        log.info("更新Agent定义成功, id={}", agentDefinition.getId());
    }
}
