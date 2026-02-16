package com.game.playforge.infrastructure.external.auth;

import com.game.playforge.common.constant.AuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的RefreshToken存储
 * <p>
 * 使用Redis存储RefreshToken与用户ID的映射关系，支持创建、查询和删除操作。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    /**
     * 创建RefreshToken并存入Redis
     *
     * @param userId 用户ID
     * @return RefreshToken字符串
     */
    public String createRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = AuthConstants.REFRESH_TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, String.valueOf(userId),
                jwtProperties.getRefreshTokenExpireDays(), TimeUnit.DAYS);
        log.info("创建RefreshToken, userId={}, expireDays={}", userId, jwtProperties.getRefreshTokenExpireDays());
        return token;
    }

    /**
     * 根据RefreshToken获取用户ID
     *
     * @param refreshToken RefreshToken字符串
     * @return 用户ID，不存在或已过期返回null
     */
    public Long getUserIdByRefreshToken(String refreshToken) {
        String key = AuthConstants.REFRESH_TOKEN_PREFIX + refreshToken;
        String userId = redisTemplate.opsForValue().get(key);
        log.debug("查询RefreshToken, found={}", userId != null);
        return userId != null ? Long.parseLong(userId) : null;
    }

    /**
     * 删除RefreshToken（登出或轮换时调用）
     *
     * @param refreshToken RefreshToken字符串
     */
    public void deleteRefreshToken(String refreshToken) {
        String key = AuthConstants.REFRESH_TOKEN_PREFIX + refreshToken;
        Boolean deleted = redisTemplate.delete(key);
        log.info("删除RefreshToken, deleted={}", Boolean.TRUE.equals(deleted));
    }
}
