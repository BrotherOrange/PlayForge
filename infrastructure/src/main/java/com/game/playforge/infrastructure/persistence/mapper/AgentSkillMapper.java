package com.game.playforge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.playforge.domain.model.AgentSkill;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent技能MyBatis Mapper接口
 * <p>
 * 继承 {@link BaseMapper}，由MyBatis Plus自动提供CRUD实现。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper
public interface AgentSkillMapper extends BaseMapper<AgentSkill> {
}
