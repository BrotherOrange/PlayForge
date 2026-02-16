package com.game.playforge.infrastructure.external.cache.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.domain.model.User;
import com.game.playforge.infrastructure.external.cache.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 用户缓存服务实现
 * <p>
 * 使用Redis + JSON序列化存储用户信息，默认缓存30分钟。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheServiceImpl implements UserCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void cacheUser(User user) {
        try {
            String key = AuthConstants.USER_CACHE_PREFIX + user.getId();
            String json = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES);
            log.debug("缓存用户信息, userId={}", user.getId());
        } catch (JsonProcessingException e) {
            log.warn("缓存用户信息失败, userId={}, error={}", user.getId(), e.getMessage());
        }
    }

    @Override
    public User getCachedUser(Long userId) {
        try {
            String key = AuthConstants.USER_CACHE_PREFIX + userId;
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                log.debug("用户缓存命中, userId={}", userId);
                return objectMapper.readValue(json, User.class);
            }
            log.debug("用户缓存未命中, userId={}", userId);
        } catch (JsonProcessingException e) {
            log.warn("读取用户缓存失败, userId={}, error={}", userId, e.getMessage());
        }
        return null;
    }

    @Override
    public void evictUser(Long userId) {
        String key = AuthConstants.USER_CACHE_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("清除用户缓存, userId={}, deleted={}", userId, Boolean.TRUE.equals(deleted));
    }
}
