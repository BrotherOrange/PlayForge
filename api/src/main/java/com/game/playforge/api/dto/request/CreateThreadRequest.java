package com.game.playforge.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class CreateThreadRequest {

    /**
     * Agent定义ID
     */
    @NotNull(message = "agentId不能为空")
    private Long agentId;

    /**
     * 会话标题（可选）
     */
    private String title;
}
