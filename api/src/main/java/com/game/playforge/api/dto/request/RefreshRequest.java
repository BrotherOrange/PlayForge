package com.game.playforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token刷新请求DTO
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class RefreshRequest {

    /**
     * 刷新令牌
     */
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
