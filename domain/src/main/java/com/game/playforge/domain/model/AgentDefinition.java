package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent定义实体
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
@TableName("t_agent_definition")
public class AgentDefinition {

    /**
     * Agent定义ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * Agent唯一标识
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
     * 系统提示词（内联文本）
     */
    private String systemPrompt;

    /**
     * 系统提示词文件引用（classpath:prompts/ 下的文件名）
     */
    private String systemPromptRef;

    /**
     * AI供应商标识（openai/anthropic/gemini）
     */
    private String provider;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 工具名称列表（逗号分隔）
     */
    private String toolNames;

    /**
     * 技能名称列表（逗号分隔，引用代码定义的@Skill）
     */
    private String skillNames;

    /**
     * 父线程ID（子Agent由Lead Agent在此线程内创建）
     */
    private Long parentThreadId;

    /**
     * 记忆窗口大小（消息条数）
     */
    private Integer memoryWindowSize;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大Token数
     */
    private Integer maxTokens;

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
