package com.game.playforge.application.service;

import com.game.playforge.domain.model.User;

/**
 * 用户服务接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface UserService {

    /**
     * 获取用户资料
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    User getProfile(Long userId);

    /**
     * 更新用户资料
     *
     * @param userId    用户ID
     * @param nickname  昵称
     * @param avatarUrl 头像URL
     * @param bio       个人简介
     * @return 更新后的用户实体
     */
    User updateProfile(Long userId, String nickname, String avatarUrl, String bio);
}
