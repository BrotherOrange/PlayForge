package com.game.playforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天请求
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class ChatRequest {

    /**
     * 用户消息
     */
    @NotBlank(message = "消息不能为空")
    private String message;
}
