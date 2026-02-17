package com.game.playforge.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent会话响应
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class AgentThreadResponse {

    private Long id;
    private Long agentId;
    private String title;
    private String status;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
