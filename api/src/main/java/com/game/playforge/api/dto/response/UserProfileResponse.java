package com.game.playforge.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料响应
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class UserProfileResponse {

    private Long id;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String avatarKey;
    private String bio;
    private LocalDateTime createdAt;
}
