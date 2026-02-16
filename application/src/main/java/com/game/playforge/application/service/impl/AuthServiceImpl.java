package com.game.playforge.application.service.impl;

import com.game.playforge.application.dto.TokenPair;
import com.game.playforge.application.service.AuthService;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.User;
import com.game.playforge.domain.repository.UserRepository;
import com.game.playforge.domain.service.PasswordEncoder;
import com.game.playforge.infrastructure.external.auth.JwtUtil;
import com.game.playforge.infrastructure.external.auth.RedisTokenStore;
import com.game.playforge.infrastructure.external.cache.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTokenStore redisTokenStore;
    private final UserCacheService userCacheService;

    @Override
    public TokenPair register(String phone, String password, String nickname, String avatarUrl, String bio) {
        log.info("用户注册, phone={}", phone);
        User existing = userRepository.findByPhone(phone);
        if (existing != null) {
            log.warn("注册失败-手机号已注册, phone={}", phone);
            throw new BusinessException(ResultCode.PHONE_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setBio(bio);
        user.setIsAdmin(false);
        userRepository.insert(user);

        log.info("用户注册成功, userId={}, phone={}", user.getId(), phone);
        return generateTokenPair(user.getId());
    }

    @Override
    public TokenPair login(String phone, String password) {
        log.info("用户登录, phone={}", phone);
        User user = userRepository.findByPhone(phone);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            log.warn("登录失败-凭证错误, phone={}", phone);
            throw new BusinessException(ResultCode.CREDENTIALS_ERROR);
        }

        userCacheService.cacheUser(user);
        log.info("用户登录成功, userId={}, phone={}", user.getId(), phone);
        return generateTokenPair(user.getId());
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        log.info("刷新Token");
        Long userId = redisTokenStore.getUserIdByRefreshToken(refreshToken);
        if (userId == null) {
            log.warn("刷新Token失败-refreshToken无效");
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        // 轮换：删除旧RefreshToken
        redisTokenStore.deleteRefreshToken(refreshToken);

        log.info("刷新Token成功, userId={}", userId);
        return generateTokenPair(userId);
    }

    @Override
    public void logout(String refreshToken) {
        log.info("用户登出");
        if (refreshToken != null) {
            redisTokenStore.deleteRefreshToken(refreshToken);
        }
        log.info("用户登出完成");
    }

    @Override
    public User getCurrentUser(Long userId) {
        log.debug("获取当前用户, userId={}", userId);
        User cached = userCacheService.getCachedUser(userId);
        if (cached != null) {
            return cached;
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            log.warn("用户不存在, userId={}", userId);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        userCacheService.cacheUser(user);
        return user;
    }

    /**
     * 生成Token对（AccessToken + RefreshToken）
     *
     * @param userId 用户ID
     * @return Token对
     */
    private TokenPair generateTokenPair(Long userId) {
        String accessToken = jwtUtil.generateAccessToken(userId);
        String refreshToken = redisTokenStore.createRefreshToken(userId);
        log.debug("生成Token对, userId={}", userId);
        return new TokenPair(accessToken, refreshToken);
    }
}
