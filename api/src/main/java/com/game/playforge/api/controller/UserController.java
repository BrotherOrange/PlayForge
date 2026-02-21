package com.game.playforge.api.controller;

import com.game.playforge.api.dto.request.UpdateProfileRequest;
import com.game.playforge.api.dto.response.UserProfileResponse;
import com.game.playforge.api.mapper.UserMapper;
import com.game.playforge.application.service.UserService;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.domain.model.User;
import com.game.playforge.infrastructure.external.oss.OssService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OssService ossService;
    private final UserMapper userMapper;

    /**
     * 获取当前用户资料
     *
     * @param request HTTP请求
     * @return 用户资料
     */
    @GetMapping("/profile")
    public ApiResult<UserProfileResponse> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("获取用户资料, userId={}", userId);
        User user = userService.getProfile(userId);
        return ApiResult.success(toResponse(user));
    }

    /**
     * 更新当前用户资料
     *
     * @param request        HTTP请求
     * @param profileRequest 更新请求
     * @return 更新后的用户资料
     */
    @PutMapping("/profile")
    public ApiResult<UserProfileResponse> updateProfile(
            HttpServletRequest request,
            @Valid @RequestBody UpdateProfileRequest profileRequest) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("更新用户资料, userId={}", userId);
        User user = userService.updateProfile(userId,
                profileRequest.getNickname(),
                profileRequest.getAvatarUrl(),
                profileRequest.getBio());
        return ApiResult.success(toResponse(user));
    }

    private UserProfileResponse toResponse(User user) {
        UserProfileResponse response = userMapper.toResponse(user);
        response.setAvatarUrl(ossService.generateSignedUrl(user.getAvatarUrl()));
        return response;
    }
}
