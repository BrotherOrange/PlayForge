package com.game.playforge.api.mapper;

import com.game.playforge.api.dto.request.CreateAgentRequest;
import com.game.playforge.api.dto.response.AgentDefinitionResponse;
import com.game.playforge.domain.model.AgentDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * AgentDefinition映射器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface AgentDefinitionMapper {

    AgentDefinitionMapper INSTANCE = Mappers.getMapper(AgentDefinitionMapper.class);

    @Mapping(target = "threadId", ignore = true)
    AgentDefinitionResponse toResponse(AgentDefinition agent);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "parentThreadId", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentDefinition fromCreateRequest(CreateAgentRequest request);
}
