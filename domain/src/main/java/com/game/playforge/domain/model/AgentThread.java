package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent会话实体
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
@TableName("t_agent_thread")
public class AgentThread {

    /**
     * 会话ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Agent定义ID
     */
    private Long agentId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 状态（ACTIVE/ARCHIVED/DELETED）
     */
    private String status;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * 累计Token用量
     */
    private Long totalTokensUsed;

    /**
     * 最后消息时间
     */
    private LocalDateTime lastMessageAt;

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
