package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.AgentThread;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent会话仓储接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentThreadRepository {

    /**
     * 根据ID查询会话
     *
     * @param id 会话ID
     * @return 会话实体，不存在返回null
     */
    AgentThread findById(Long id);

    /**
     * 根据用户ID查询会话列表
     *
     * @param userId 用户ID
     * @return 会话列表（按创建时间降序）
     */
    List<AgentThread> findByUserId(Long userId);

    /**
     * 根据用户ID和Agent ID查询会话列表
     *
     * @param userId  用户ID
     * @param agentId Agent定义ID
     * @return 会话列表（按创建时间降序）
     */
    List<AgentThread> findByUserIdAndAgentId(Long userId, Long agentId);

    /**
     * 新增会话
     *
     * @param agentThread 会话实体
     */
    void insert(AgentThread agentThread);

    /**
     * 更新会话
     *
     * @param agentThread 会话实体
     */
    void update(AgentThread agentThread);

    /**
     * 原子递增消息数并更新最后消息时间
     *
     * @param threadId       会话ID
     * @param messageDelta   增量消息数
     * @param lastMessageAt  最后消息时间
     */
    void incrementMessageCount(Long threadId, int messageDelta, LocalDateTime lastMessageAt);
}
