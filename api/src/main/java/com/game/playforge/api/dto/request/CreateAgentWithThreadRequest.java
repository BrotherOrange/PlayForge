package com.game.playforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建Agent+Thread请求
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class CreateAgentWithThreadRequest {

    @NotBlank(message = "供应商不能为空")
    private String provider;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private String displayName;
}
