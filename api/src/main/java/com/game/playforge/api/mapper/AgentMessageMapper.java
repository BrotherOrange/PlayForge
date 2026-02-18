package com.game.playforge.api.mapper;

import com.game.playforge.api.dto.response.AgentMessageResponse;
import com.game.playforge.domain.model.AgentMessage;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * AgentMessage映射器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface AgentMessageMapper {

    AgentMessageMapper INSTANCE = Mappers.getMapper(AgentMessageMapper.class);

    AgentMessageResponse toResponse(AgentMessage message);

    List<AgentMessageResponse> toResponseList(List<AgentMessage> messages);
}
