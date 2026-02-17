package com.game.playforge.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent消息实体
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Data
@TableName("t_agent_message")
public class AgentMessage {

    /**
     * 消息ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID
     */
    private Long threadId;

    /**
     * 角色（user/assistant/system/tool）
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * Token数量
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
