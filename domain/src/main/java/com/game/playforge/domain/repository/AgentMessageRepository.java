package com.game.playforge.domain.repository;

import com.game.playforge.domain.model.AgentMessage;

import java.util.List;

/**
 * Agent消息仓储接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentMessageRepository {

    /**
     * 新增消息
     *
     * @param agentMessage 消息实体
     */
    void insert(AgentMessage agentMessage);

    /**
     * 批量新增消息
     *
     * @param messages 消息列表
     */
    void insertBatch(List<AgentMessage> messages);

    /**
     * 根据会话ID查询消息列表（支持分页）
     *
     * @param threadId 会话ID
     * @param limit    查询条数
     * @param offset   偏移量
     * @return 消息列表（按创建时间升序）
     */
    List<AgentMessage> findByThreadId(Long threadId, int limit, int offset);

    /**
     * 查询会话最新消息（按创建时间倒序）
     *
     * @param threadId 会话ID
     * @param limit    查询条数
     * @return 最新消息列表（按创建时间倒序）
     */
    List<AgentMessage> findLatestByThreadId(Long threadId, int limit);

    /**
     * 根据会话ID统计消息数量
     *
     * @param threadId 会话ID
     * @return 消息数量
     */
    long countByThreadId(Long threadId);
}
