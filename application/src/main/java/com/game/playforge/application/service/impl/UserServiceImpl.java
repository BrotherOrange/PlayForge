package com.game.playforge.application.service.impl;

import com.game.playforge.application.service.UserService;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.User;
import com.game.playforge.domain.repository.UserRepository;
import com.game.playforge.infrastructure.external.cache.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    @Override
    public User getProfile(Long userId) {
        log.debug("获取用户资料, userId={}", userId);
        User cached = userCacheService.getCachedUser(userId);
        if (cached != null) {
            return cached;
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        userCacheService.cacheUser(user);
        return user;
    }

    @Override
    public User updateProfile(Long userId, String nickname, String avatarUrl, String bio) {
        log.info("更新用户资料, userId={}", userId);
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        if (bio != null) {
            user.setBio(bio);
        }
        userRepository.update(user);

        userCacheService.evictUser(userId);
        userCacheService.cacheUser(user);

        log.info("用户资料更新完成, userId={}", userId);
        return user;
    }
}
