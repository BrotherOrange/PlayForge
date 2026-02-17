package com.game.playforge.application.service;

import com.game.playforge.domain.model.AgentMessage;
import com.game.playforge.domain.model.AgentThread;

import java.util.List;

/**
 * Agent会话服务接口
 *
 * @author Richard Zhang
 * @since 1.0
 */
public interface AgentThreadService {

    /**
     * 创建会话
     *
     * @param userId  用户ID
     * @param agentId Agent定义ID
     * @param title   会话标题（可选）
     * @return 会话实体
     */
    AgentThread createThread(Long userId, Long agentId, String title);

    /**
     * 获取用户的会话列表
     *
     * @param userId  用户ID
     * @param agentId Agent定义ID（可选，为null则查全部）
     * @return 会话列表
     */
    List<AgentThread> listThreads(Long userId, Long agentId);

    /**
     * 获取会话详情（校验所属权）
     *
     * @param userId   用户ID
     * @param threadId 会话ID
     * @return 会话实体
     */
    AgentThread getThread(Long userId, Long threadId);

    /**
     * 删除会话（校验所属权，清理Redis记忆）
     *
     * @param userId   用户ID
     * @param threadId 会话ID
     */
    void deleteThread(Long userId, Long threadId);

    /**
     * 获取消息历史
     *
     * @param userId   用户ID
     * @param threadId 会话ID
     * @param limit    查询条数
     * @param offset   偏移量
     * @return 消息列表
     */
    List<AgentMessage> getMessageHistory(Long userId, Long threadId, int limit, int offset);
}
