package com.game.playforge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.playforge.domain.model.ReviewTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {
}
