package com.game.playforge.api.mapper;

import com.game.playforge.api.dto.response.AgentThreadResponse;
import com.game.playforge.domain.model.AgentThread;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * AgentThread映射器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface AgentThreadMapper {

    AgentThreadMapper INSTANCE = Mappers.getMapper(AgentThreadMapper.class);

    AgentThreadResponse toResponse(AgentThread thread);

    List<AgentThreadResponse> toResponseList(List<AgentThread> threads);
}
