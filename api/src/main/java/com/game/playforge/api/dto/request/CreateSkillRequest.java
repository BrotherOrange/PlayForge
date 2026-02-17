package com.game.playforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建技能请求
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
public class CreateSkillRequest {

    @NotBlank(message = "技能标识不能为空")
    private String name;

    private String displayName;

    private String description;

    private String promptFragment;

    private String toolNames;
}
