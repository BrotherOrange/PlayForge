package com.game.playforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建Agent请求
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class CreateAgentRequest {

    @NotBlank(message = "Agent标识不能为空")
    private String name;

    private String displayName;

    private String description;

    private String systemPrompt;

    private String systemPromptRef;

    @NotBlank(message = "供应商不能为空")
    private String provider;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private String toolNames;

    private String skillNames;

    private Integer memoryWindowSize = 20;

    private Double temperature = 0.7;

    private Integer maxTokens = 32768;
}
