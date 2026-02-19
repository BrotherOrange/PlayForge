package com.game.playforge.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent定义响应
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class AgentDefinitionResponse {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String provider;
    private String modelName;
    private Long threadId;
    private Long parentThreadId;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
