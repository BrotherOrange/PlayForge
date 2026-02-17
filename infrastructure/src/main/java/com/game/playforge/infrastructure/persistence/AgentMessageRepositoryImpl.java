package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.playforge.domain.model.AgentMessage;
import com.game.playforge.domain.repository.AgentMessageRepository;
import com.game.playforge.infrastructure.persistence.mapper.AgentMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent消息仓储实现
 * <p>
 * 基于MyBatis Plus的 {@link AgentMessageMapper} 实现持久化操作。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentMessageRepositoryImpl implements AgentMessageRepository {

    private final AgentMessageMapper agentMessageMapper;

    @Override
    public void insert(AgentMessage agentMessage) {
        log.debug("新增消息, threadId={}, role={}", agentMessage.getThreadId(), agentMessage.getRole());
        agentMessageMapper.insert(agentMessage);
        log.debug("新增消息成功, messageId={}", agentMessage.getId());
    }

    @Override
    public void insertBatch(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        log.info("批量新增消息, count={}", messages.size());
        for (AgentMessage message : messages) {
            agentMessageMapper.insert(message);
        }
        log.info("批量新增消息成功, count={}", messages.size());
    }

    @Override
    public List<AgentMessage> findByThreadId(Long threadId, int limit, int offset) {
        log.debug("查询会话消息, threadId={}, limit={}, offset={}", threadId, limit, offset);
        List<AgentMessage> messages = agentMessageMapper.selectList(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getThreadId, threadId)
                        .orderByAsc(AgentMessage::getCreatedAt)
                        .last("LIMIT " + limit + " OFFSET " + offset));
        log.debug("查询会话消息结果, threadId={}, count={}", threadId, messages.size());
        return messages;
    }

    @Override
    public long countByThreadId(Long threadId) {
        log.debug("统计会话消息数量, threadId={}", threadId);
        long count = agentMessageMapper.selectCount(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getThreadId, threadId));
        log.debug("统计会话消息数量, threadId={}, count={}", threadId, count);
        return count;
    }
}
