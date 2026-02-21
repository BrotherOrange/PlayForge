package com.game.playforge.api.dto.response;

import lombok.Data;

/**
 * 创建Agent+Thread的组合响应
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class CreateAgentWithThreadResponse {

    private AgentDefinitionResponse agent;
    private AgentThreadResponse thread;
}
