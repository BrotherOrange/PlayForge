package com.game.playforge.api.mapper;

import com.game.playforge.api.dto.request.CreateSkillRequest;
import com.game.playforge.domain.model.AgentSkill;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * AgentSkill映射器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface AgentSkillMapper {

    AgentSkillMapper INSTANCE = Mappers.getMapper(AgentSkillMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentSkill fromCreateRequest(CreateSkillRequest request);
}
