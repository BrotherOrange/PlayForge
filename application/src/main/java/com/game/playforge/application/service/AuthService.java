package com.game.playforge.application.service;

import com.game.playforge.application.dto.TokenPair;
import com.game.playforge.domain.model.User;

/**
 * 认证服务接口
 * <p>
 * 提供用户注册、登录、登出、Token刷新和当前用户查询能力。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AuthService {

    /**
     * 用户注册
     *
     * @param phone    手机号
     * @param password 密码
     * @return Token对（AccessToken + RefreshToken）
     */
    TokenPair register(String phone, String password, String nickname, String avatarUrl, String bio);

    /**
     * 用户登录
     *
     * @param phone    手机号
     * @param password 密码
     * @return Token对（AccessToken + RefreshToken）
     */
    TokenPair login(String phone, String password);

    /**
     * 刷新Token（轮换RefreshToken）
     *
     * @param refreshToken 当前RefreshToken
     * @return 新的Token对
     */
    TokenPair refresh(String refreshToken);

    /**
     * 用户登出（删除RefreshToken）
     *
     * @param refreshToken 当前RefreshToken
     */
    void logout(String refreshToken);

    /**
     * 获取当前登录用户信息（优先读缓存）
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    User getCurrentUser(Long userId);
}
