package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent技能实体
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
@TableName("t_agent_skill")
public class AgentSkill {

    /**
     * 技能ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 技能唯一标识
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 描述
     */
    private String description;

    /**
     * 提示词片段
     */
    private String promptFragment;

    /**
     * 工具名称列表（逗号分隔）
     */
    private String toolNames;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 是否删除（逻辑删除）
     */
    @TableLogic
    private Boolean isDeleted;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
