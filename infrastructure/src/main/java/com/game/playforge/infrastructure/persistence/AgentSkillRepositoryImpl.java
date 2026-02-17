package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.AgentSkill;
import com.game.playforge.domain.repository.AgentSkillRepository;
import com.game.playforge.infrastructure.persistence.mapper.AgentSkillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * Agent技能仓储实现
 * <p>
 * 基于MyBatis Plus的 {@link AgentSkillMapper} 实现持久化操作。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentSkillRepositoryImpl implements AgentSkillRepository {

    private final AgentSkillMapper agentSkillMapper;

    @Override
    public AgentSkill findById(Long id) {
        log.debug("根据ID查询技能, id={}", id);
        AgentSkill skill = agentSkillMapper.selectById(id);
        log.debug("根据ID查询技能结果, id={}, found={}", id, skill != null);
        return skill;
    }

    @Override
    public AgentSkill findByName(String name) {
        log.debug("根据名称查询技能, name={}", name);
        AgentSkill skill = agentSkillMapper.selectOne(
                new LambdaQueryWrapper<AgentSkill>()
                        .eq(AgentSkill::getName, name)
                        .eq(AgentSkill::getIsActive, true));
        log.debug("根据名称查询技能结果, name={}, found={}", name, skill != null);
        return skill;
    }

    @Override
    public List<AgentSkill> findAllActive() {
        log.debug("查询所有启用的技能");
        List<AgentSkill> skills = agentSkillMapper.selectList(
                new LambdaQueryWrapper<AgentSkill>()
                        .eq(AgentSkill::getIsActive, true));
        log.debug("查询所有启用的技能, count={}", skills.size());
        return skills;
    }

    @Override
    public List<AgentSkill> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        log.debug("根据ID列表批量查询技能, ids={}", ids);
        List<AgentSkill> skills = agentSkillMapper.selectBatchIds(ids);
        log.debug("根据ID列表批量查询技能, count={}", skills.size());
        return skills;
    }

    @Override
    public void insert(AgentSkill agentSkill) {
        log.info("新增技能, name={}", agentSkill.getName());
        agentSkillMapper.insert(agentSkill);
        log.info("新增技能成功, id={}", agentSkill.getId());
    }

    @Override
    public void update(AgentSkill agentSkill) {
        log.info("更新技能, id={}", agentSkill.getId());
        agentSkillMapper.updateById(agentSkill);
        log.info("更新技能成功, id={}", agentSkill.getId());
    }
}
