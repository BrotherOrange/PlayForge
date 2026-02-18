package com.game.playforge.api.mapper;

import com.game.playforge.api.dto.response.UserProfileResponse;
import com.game.playforge.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * User映射器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "avatarKey", source = "avatarUrl")
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "isAdmin", expression = "java(Boolean.TRUE.equals(user.getIsAdmin()))")
    UserProfileResponse toResponse(User user);
}
