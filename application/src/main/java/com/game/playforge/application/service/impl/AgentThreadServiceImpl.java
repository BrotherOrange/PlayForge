package com.game.playforge.application.service.impl;

import com.game.playforge.application.service.AgentThreadService;
import com.game.playforge.common.enums.ThreadStatus;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentMessage;
import com.game.playforge.domain.model.AgentThread;
import com.game.playforge.domain.repository.AgentDefinitionRepository;
import com.game.playforge.domain.repository.AgentMessageRepository;
import com.game.playforge.domain.repository.AgentThreadRepository;
import com.game.playforge.infrastructure.external.ai.RedisChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent会话服务实现
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentThreadServiceImpl implements AgentThreadService {

    private final AgentThreadRepository agentThreadRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public AgentThread createThread(Long userId, Long agentId, String title) {
        log.info("创建会话, userId={}, agentId={}", userId, agentId);

        if (agentDefinitionRepository.findById(agentId) == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }

        AgentThread thread = new AgentThread();
        thread.setAgentId(agentId);
        thread.setUserId(userId);
        thread.setTitle(title);
        thread.setStatus(ThreadStatus.ACTIVE.name());
        thread.setMessageCount(0);
        thread.setTotalTokensUsed(0L);
        agentThreadRepository.insert(thread);

        log.info("创建会话成功, threadId={}", thread.getId());
        return thread;
    }

    @Override
    public List<AgentThread> listThreads(Long userId, Long agentId) {
        log.debug("查询会话列表, userId={}, agentId={}", userId, agentId);
        if (agentId != null) {
            return agentThreadRepository.findByUserIdAndAgentId(userId, agentId);
        }
        return agentThreadRepository.findByUserId(userId);
    }

    @Override
    public AgentThread getThread(Long userId, Long threadId) {
        log.debug("获取会话详情, userId={}, threadId={}", userId, threadId);
        AgentThread thread = agentThreadRepository.findById(threadId);
        if (thread == null) {
            throw new BusinessException(ResultCode.THREAD_NOT_FOUND);
        }
        if (!thread.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.THREAD_ACCESS_DENIED);
        }
        return thread;
    }

    @Override
    public void deleteThread(Long userId, Long threadId) {
        log.info("删除会话, userId={}, threadId={}", userId, threadId);
        AgentThread thread = getThread(userId, threadId);
        thread.setStatus(ThreadStatus.DELETED.name());
        agentThreadRepository.update(thread);
        redisChatMemoryStore.deleteMessages(threadId);
        log.info("删除会话成功, threadId={}", threadId);
    }

    @Override
    public List<AgentMessage> getMessageHistory(Long userId, Long threadId, int limit, int offset) {
        log.debug("获取消息历史, userId={}, threadId={}, limit={}, offset={}", userId, threadId, limit, offset);
        getThread(userId, threadId);
        return agentMessageRepository.findByThreadId(threadId, limit, offset);
    }
}
