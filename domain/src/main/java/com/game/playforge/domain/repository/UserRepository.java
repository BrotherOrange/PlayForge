package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.User;

/**
 * 用户仓储接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface UserRepository {

    /**
     * 新增用户
     *
     * @param user 用户实体
     */
    void insert(User user);

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户实体，不存在返回null
     */
    User findById(Long id);

    /**
     * 根据手机号查询用户
     *
     * @param phone 手机号
     * @return 用户实体，不存在返回null
     */
    User findByPhone(String phone);

    /**
     * 更新用户信息
     *
     * @param user 用户实体
     */
    void update(User user);
}
