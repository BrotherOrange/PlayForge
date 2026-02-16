package com.game.playforge.infrastructure.external.cache;

import com.game.playforge.domain.model.User;

/**
 * 用户缓存服务接口
 * <p>
 * 提供用户信息的Redis缓存能力，用于减少数据库查询。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface UserCacheService {

    /**
     * 缓存用户信息
     *
     * @param user 用户实体
     */
    void cacheUser(User user);

    /**
     * 获取缓存的用户信息
     *
     * @param userId 用户ID
     * @return 用户实体，缓存不存在返回null
     */
    User getCachedUser(Long userId);

    /**
     * 清除用户缓存
     *
     * @param userId 用户ID
     */
    void evictUser(Long userId);
}
