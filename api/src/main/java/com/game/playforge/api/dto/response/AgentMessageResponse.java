package com.game.playforge.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent消息响应
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class AgentMessageResponse {

    private Long id;
    private String role;
    private String content;
    private String toolName;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}
