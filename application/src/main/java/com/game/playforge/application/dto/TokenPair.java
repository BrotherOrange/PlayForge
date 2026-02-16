package com.game.playforge.application.dto;

/**
 * Token对值对象
 * <p>
 * 包含AccessToken和RefreshToken，登录/注册/刷新成功后返回。
 * </p>
 *
 * @param accessToken  JWT访问令牌
 * @param refreshToken 刷新令牌
 * @author Richard Zhang
 * @since 1.0
 */
public record TokenPair(String accessToken, String refreshToken) {

}
