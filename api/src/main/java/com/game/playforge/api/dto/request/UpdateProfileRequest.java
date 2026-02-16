package com.game.playforge.api.dto.request;

import lombok.Data;

/**
 * 更新用户资料请求
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class UpdateProfileRequest {

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 个人简介
     */
    private String bio;
}
