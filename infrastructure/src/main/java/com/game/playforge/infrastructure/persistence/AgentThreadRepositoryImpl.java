package com.game.playforge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.playforge.common.enums.ThreadStatus;
import com.game.playforge.domain.model.AgentThread;
import com.game.playforge.domain.repository.AgentThreadRepository;
import com.game.playforge.infrastructure.persistence.mapper.AgentThreadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent会话仓储实现
 * <p>
 * 基于MyBatis Plus的 {@link AgentThreadMapper} 实现持久化操作。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentThreadRepositoryImpl implements AgentThreadRepository {

    private final AgentThreadMapper agentThreadMapper;

    @Override
    public AgentThread findById(Long id) {
        log.debug("根据ID查询会话, id={}", id);
        AgentThread thread = agentThreadMapper.selectById(id);
        log.debug("根据ID查询会话结果, id={}, found={}", id, thread != null);
        return thread;
    }

    @Override
    public List<AgentThread> findByUserId(Long userId) {
        log.debug("根据用户ID查询会话列表, userId={}", userId);
        List<AgentThread> threads = agentThreadMapper.selectList(
                new LambdaQueryWrapper<AgentThread>()
                        .eq(AgentThread::getUserId, userId)
                        .ne(AgentThread::getStatus, ThreadStatus.DELETED.name())
                        .orderByDesc(AgentThread::getCreatedAt));
        log.debug("根据用户ID查询会话列表, userId={}, count={}", userId, threads.size());
        return threads;
    }

    @Override
    public List<AgentThread> findByUserIdAndAgentId(Long userId, Long agentId) {
        log.debug("根据用户ID和AgentID查询会话列表, userId={}, agentId={}", userId, agentId);
        List<AgentThread> threads = agentThreadMapper.selectList(
                new LambdaQueryWrapper<AgentThread>()
                        .eq(AgentThread::getUserId, userId)
                        .eq(AgentThread::getAgentId, agentId)
                        .ne(AgentThread::getStatus, ThreadStatus.DELETED.name())
                        .orderByDesc(AgentThread::getCreatedAt));
        log.debug("根据用户ID和AgentID查询会话列表, userId={}, agentId={}, count={}",
                userId, agentId, threads.size());
        return threads;
    }

    @Override
    public Map<Long, Long> findLatestThreadIdsByAgentIds(Long userId, List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Map.of();
        }
        log.debug("批量查询最新会话, userId={}, agentCount={}", userId, agentIds.size());
        List<Map<String, Object>> rows = agentThreadMapper.selectLatestThreadIdsByAgentIds(userId, agentIds);

        Map<Long, Long> latest = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long agentId = ((Number) row.get("agentId")).longValue();
            Long threadId = ((Number) row.get("threadId")).longValue();
            latest.put(agentId, threadId);
        }
        log.debug("批量查询最新会话完成, userId={}, hit={}", userId, latest.size());
        return latest;
    }

    @Override
    public void insert(AgentThread agentThread) {
        log.info("新增会话, agentId={}, userId={}", agentThread.getAgentId(), agentThread.getUserId());
        agentThreadMapper.insert(agentThread);
        log.info("新增会话成功, threadId={}", agentThread.getId());
    }

    @Override
    public void update(AgentThread agentThread) {
        log.info("更新会话, threadId={}", agentThread.getId());
        agentThreadMapper.updateById(agentThread);
        log.info("更新会话成功, threadId={}", agentThread.getId());
    }

    @Override
    public void incrementMessageCount(Long threadId, int messageDelta, LocalDateTime lastMessageAt) {
        if (messageDelta <= 0) {
            return;
        }
        log.debug("递增会话消息数, threadId={}, delta={}", threadId, messageDelta);
        agentThreadMapper.update(
                null,
                new LambdaUpdateWrapper<AgentThread>()
                        .eq(AgentThread::getId, threadId)
                        .setSql("message_count = COALESCE(message_count, 0) + {0}", messageDelta)
                        .set(AgentThread::getLastMessageAt, lastMessageAt)
        );
    }
}
