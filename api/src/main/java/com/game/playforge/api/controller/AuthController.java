package com.game.playforge.api.controller;

import com.game.playforge.api.dto.request.LoginRequest;
import com.game.playforge.api.dto.request.RefreshRequest;
import com.game.playforge.api.dto.request.RegisterRequest;
import com.game.playforge.api.dto.response.TokenResponse;
import com.game.playforge.application.dto.TokenPair;
import com.game.playforge.application.service.AuthService;
import com.game.playforge.common.result.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * <p>
 * 提供注册、登录、Token刷新和登出接口。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return Token对
     */
    @PostMapping("/register")
    public ApiResult<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("收到注册请求, phone={}", request.getPhone());
        TokenPair tokenPair = authService.register(request.getPhone(), request.getPassword(),
                request.getNickname(), request.getAvatarUrl(), request.getBio());
        log.info("注册请求处理完成, phone={}", request.getPhone());
        return ApiResult.success(toResponse(tokenPair));
    }

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return Token对
     */
    @PostMapping("/login")
    public ApiResult<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("收到登录请求, phone={}", request.getPhone());
        TokenPair tokenPair = authService.login(request.getPhone(), request.getPassword());
        log.info("登录请求处理完成, phone={}", request.getPhone());
        return ApiResult.success(toResponse(tokenPair));
    }

    /**
     * 刷新Token
     *
     * @param request 刷新请求
     * @return 新的Token对
     */
    @PostMapping("/refresh")
    public ApiResult<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("收到刷新Token请求");
        TokenPair tokenPair = authService.refresh(request.getRefreshToken());
        log.info("刷新Token请求处理完成");
        return ApiResult.success(toResponse(tokenPair));
    }

    /**
     * 用户登出
     *
     * @param request 登出请求（包含refreshToken）
     * @return 成功响应
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestBody RefreshRequest request) {
        log.info("收到登出请求");
        authService.logout(request.getRefreshToken());
        log.info("登出请求处理完成");
        return ApiResult.success();
    }

    private TokenResponse toResponse(TokenPair tokenPair) {
        return new TokenResponse(tokenPair.accessToken(), tokenPair.refreshToken());
    }
}
