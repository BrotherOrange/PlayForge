package com.game.playforge.application.service.agent;

import com.game.playforge.common.enums.ThreadStatus;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentMessage;
import com.game.playforge.domain.model.AgentThread;
import com.game.playforge.domain.repository.AgentDefinitionRepository;
import com.game.playforge.domain.repository.AgentMessageRepository;
import com.game.playforge.domain.repository.AgentThreadRepository;
import com.game.playforge.infrastructure.external.ai.AgentTypeRegistry;
import com.game.playforge.infrastructure.external.ai.AgentTypeRegistry.AgentTypeDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 子Agent服务
 * <p>
 * 负责子Agent的创建、聊天、销毁和列表查询。
 * 子Agent由Lead Agent在运行时通过工具调用创建，不能创建更多子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubAgentService {

    private final AgentFactory agentFactory;
    private final AgentTypeRegistry agentTypeRegistry;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentThreadRepository agentThreadRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final TransactionTemplate transactionTemplate;

    public record SubAgentInfo(String agentName, Long threadId, String type, String displayName) {}
    private record SubAgentContext(AgentThread thread, AgentDefinition definition) {}

    /**
     * 创建子Agent
     *
     * @param userId           用户ID
     * @param parentThreadId   父线程ID（Lead Agent的线程）
     * @param type             Agent类型名称
     * @param task             任务描述
     * @param additionalPrompt 附加提示词
     * @param additionalTools  附加工具（逗号分隔）
     * @return 子Agent信息
     */
    public SubAgentInfo createSubAgent(Long userId, Long parentThreadId, String type,
                                       String task, String additionalPrompt, String additionalTools) {
        log.info("创建子Agent, userId={}, parentThreadId={}, type={}", userId, parentThreadId, type);

        AgentTypeDescriptor typeDescriptor = agentTypeRegistry.getType(type);
        if (typeDescriptor == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED,
                    "未知的Agent类型: " + type + ", 可用类型: " + agentTypeRegistry.getRegisteredTypeNames());
        }

        // 查找Lead Agent的定义以继承provider和model
        AgentThread parentThread = agentThreadRepository.findById(parentThreadId);
        if (parentThread == null) {
            throw new BusinessException(ResultCode.THREAD_NOT_FOUND, "父线程不存在");
        }
        if (!userId.equals(parentThread.getUserId())) {
            throw new BusinessException(ResultCode.THREAD_ACCESS_DENIED, "无权访问父线程");
        }
        if (ThreadStatus.DELETED.name().equals(parentThread.getStatus())) {
            throw new BusinessException(ResultCode.THREAD_NOT_FOUND, "父线程不存在");
        }
        AgentDefinition parentAgent = agentDefinitionRepository.findById(parentThread.getAgentId());
        if (parentAgent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND, "父Agent不存在");
        }
        if (!hasSubAgentTool(parentAgent)) {
            throw new BusinessException(ResultCode.AGENT_ACCESS_DENIED, "当前Agent不允许创建子Agent");
        }

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt(typeDescriptor, additionalPrompt);

        // 构建工具列表（过滤掉subAgentTool防止嵌套）
        String mergedTools = buildToolNames(typeDescriptor, additionalTools);

        // 生成名称
        String agentName = type + "-" + UUID.randomUUID().toString().substring(0, 8);

        // 事务中创建Agent + Thread
        SubAgentInfo[] result = new SubAgentInfo[1];
        transactionTemplate.executeWithoutResult(status -> {
            AgentDefinition agent = new AgentDefinition();
            agent.setUserId(userId);
            agent.setName(agentName);
            agent.setDisplayName(typeDescriptor.description());
            agent.setDescription("Sub-agent for task: " + (task != null ? task : ""));
            agent.setProvider(parentAgent.getProvider());
            agent.setModelName(parentAgent.getModelName());
            agent.setSystemPrompt(systemPrompt.isEmpty() ? null : systemPrompt);
            agent.setToolNames(mergedTools.isEmpty() ? null : mergedTools);
            agent.setParentThreadId(parentThreadId);
            agent.setMemoryWindowSize(20);
            agent.setTemperature(parentAgent.getTemperature());
            agent.setMaxTokens(parentAgent.getMaxTokens());
            agent.setIsActive(true);
            agentDefinitionRepository.insert(agent);

            AgentThread thread = new AgentThread();
            thread.setAgentId(agent.getId());
            thread.setUserId(userId);
            thread.setTitle(typeDescriptor.description());
            thread.setStatus(ThreadStatus.ACTIVE.name());
            thread.setMessageCount(0);
            thread.setTotalTokensUsed(0L);
            agentThreadRepository.insert(thread);

            result[0] = new SubAgentInfo(agentName, thread.getId(), type, typeDescriptor.description());
            log.info("子Agent创建成功, agentName={}, threadId={}", agentName, thread.getId());
        });

        return result[0];
    }

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_SECONDS = 15;

    /**
     * 与子Agent同步聊天（由AsyncTaskManager在后台线程中调用）
     *
     * @param threadId 子Agent的线程ID
     * @param message  消息内容
     * @return Agent回复
     */
    public String chat(Long userId, Long parentThreadId, Long threadId, String message) {
        log.info("子Agent聊天, userId={}, parentThreadId={}, threadId={}", userId, parentThreadId, threadId);
        SubAgentContext context = validateSubAgentAccess(userId, parentThreadId, threadId);
        AgentDefinition definition = context.definition();

        // 先保存用户消息（确保前端能看到已派发的任务）
        transactionTemplate.executeWithoutResult(status -> {
            AgentMessage userMsg = new AgentMessage();
            userMsg.setThreadId(threadId);
            userMsg.setRole("user");
            userMsg.setContent(message);
            userMsg.setTokenCount(0);
            agentMessageRepository.insert(userMsg);
            agentThreadRepository.incrementMessageCount(threadId, 1, java.time.LocalDateTime.now());
        });

        // 调用LLM（带速率限制重试）
        String response;
        try {
            AgentChatService agent = agentFactory.createAgent(definition, threadId, null, Collections.emptyList());
            response = chatWithRetry(agent, message, threadId);
        } catch (Exception e) {
            // 保存错误消息到对话中（用户能看到失败原因）
            String errorContent = "[Error] " + extractErrorMessage(e);
            transactionTemplate.executeWithoutResult(status -> {
                AgentMessage errorMsg = new AgentMessage();
                errorMsg.setThreadId(threadId);
                errorMsg.setRole("assistant");
                errorMsg.setContent(errorContent);
                errorMsg.setTokenCount(0);
                agentMessageRepository.insert(errorMsg);
                agentThreadRepository.incrementMessageCount(threadId, 1, java.time.LocalDateTime.now());
            });
            throw e;
        }

        // 保存助手回复
        transactionTemplate.executeWithoutResult(status -> {
            AgentMessage assistantMsg = new AgentMessage();
            assistantMsg.setThreadId(threadId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(response);
            assistantMsg.setTokenCount(0);
            agentMessageRepository.insert(assistantMsg);
            agentThreadRepository.incrementMessageCount(threadId, 1, java.time.LocalDateTime.now());
        });

        log.info("子Agent聊天完成, threadId={}, responseLength={}", threadId, response.length());
        return response;
    }

    private String chatWithRetry(AgentChatService agent, String message, Long threadId) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return agent.chat(message);
            } catch (Exception e) {
                if (isRateLimitError(e) && attempt < MAX_RETRIES) {
                    long waitSeconds = (long) Math.pow(2, attempt) * BASE_BACKOFF_SECONDS
                            + ThreadLocalRandom.current().nextInt(10);
                    log.warn("速率限制, threadId={}, 等待{}秒后重试 ({}/{})",
                            threadId, waitSeconds, attempt + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(waitSeconds * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Unreachable");
    }

    private boolean isRateLimitError(Throwable e) {
        while (e != null) {
            if (e.getClass().getSimpleName().equals("RateLimitException")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private String extractErrorMessage(Throwable e) {
        // 提取最内层原因的简短消息
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg != null && msg.contains("rate_limit")) {
            return "Rate limit exceeded. The task will be retried automatically.";
        }
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    /**
     * 软删除子Agent
     */
    public void destroySubAgent(Long userId, Long parentThreadId, Long threadId) {
        log.info("销毁子Agent, userId={}, parentThreadId={}, threadId={}", userId, parentThreadId, threadId);
        SubAgentContext context = validateSubAgentAccess(userId, parentThreadId, threadId);
        AgentThread thread = context.thread();
        AgentDefinition definition = context.definition();

        transactionTemplate.executeWithoutResult(status -> {
            definition.setIsActive(false);
            agentDefinitionRepository.update(definition);
            thread.setStatus(ThreadStatus.ARCHIVED.name());
            agentThreadRepository.update(thread);
        });

        log.info("子Agent已销毁, threadId={}, agentName={}", threadId, definition.getName());
    }

    /**
     * 列出指定父线程下的所有活跃子Agent
     */
    public List<SubAgentInfo> listTeamAgents(Long userId, Long parentThreadId) {
        validateParentThreadAccess(userId, parentThreadId);

        // 通过父线程ID查找所有子Agent定义
        List<AgentDefinition> allAgents = agentDefinitionRepository.findByParentThreadId(parentThreadId);
        List<SubAgentInfo> result = new ArrayList<>();

        for (AgentDefinition agent : allAgents) {
            if (!Boolean.TRUE.equals(agent.getIsActive())) {
                continue;
            }
            if (!userId.equals(agent.getUserId())) {
                continue;
            }
            // 查找对应的线程
            List<AgentThread> threads = agentThreadRepository.findByUserIdAndAgentId(agent.getUserId(), agent.getId());
            for (AgentThread thread : threads) {
                if (ThreadStatus.ACTIVE.name().equals(thread.getStatus())) {
                    // 从名称中提取类型
                    String type = agent.getName().contains("-")
                            ? agent.getName().substring(0, agent.getName().lastIndexOf('-'))
                            : agent.getName();
                    result.add(new SubAgentInfo(agent.getName(), thread.getId(), type, agent.getDisplayName()));
                }
            }
        }
        return result;
    }

    /**
     * 软删除指定父线程下所有子Agent（用于删除Lead Agent时级联清理）
     */
    public void destroyAllByParentThread(Long userId, Long parentThreadId) {
        List<SubAgentInfo> teamAgents = listTeamAgents(userId, parentThreadId);
        for (SubAgentInfo info : teamAgents) {
            destroySubAgent(userId, parentThreadId, info.threadId());
        }
    }

    private String buildSystemPrompt(AgentTypeDescriptor typeDescriptor, String additionalPrompt) {
        String base = typeDescriptor.promptContent() != null ? typeDescriptor.promptContent() : "";
        if (additionalPrompt != null && !additionalPrompt.isBlank()) {
            if (base.isEmpty()) {
                return additionalPrompt;
            }
            return base + "\n\n<additional-instructions>\n" + additionalPrompt + "\n</additional-instructions>";
        }
        return base;
    }

    private String buildToolNames(AgentTypeDescriptor typeDescriptor, String additionalTools) {
        Set<String> tools = new LinkedHashSet<>();
        if (typeDescriptor.defaultTools() != null) {
            tools.addAll(typeDescriptor.defaultTools());
        }
        if (additionalTools != null && !additionalTools.isBlank()) {
            Arrays.stream(additionalTools.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(tools::add);
        }
        // 防止嵌套：子Agent不能创建子Agent
        tools.remove("subAgentTool");
        return String.join(",", tools);
    }

    private boolean hasSubAgentTool(AgentDefinition definition) {
        if (definition.getToolNames() == null || definition.getToolNames().isBlank()) {
            return false;
        }
        return Arrays.stream(definition.getToolNames().split(","))
                .map(String::trim)
                .anyMatch("subAgentTool"::equals);
    }

    private void validateParentThreadAccess(Long userId, Long parentThreadId) {
        AgentThread parentThread = agentThreadRepository.findById(parentThreadId);
        if (parentThread == null || ThreadStatus.DELETED.name().equals(parentThread.getStatus())) {
            throw new BusinessException(ResultCode.THREAD_NOT_FOUND, "父线程不存在");
        }
        if (!userId.equals(parentThread.getUserId())) {
            throw new BusinessException(ResultCode.THREAD_ACCESS_DENIED, "无权访问父线程");
        }
    }

    private SubAgentContext validateSubAgentAccess(Long userId, Long parentThreadId, Long threadId) {
        validateParentThreadAccess(userId, parentThreadId);

        AgentThread thread = agentThreadRepository.findById(threadId);
        if (thread == null || !ThreadStatus.ACTIVE.name().equals(thread.getStatus())) {
            throw new BusinessException(ResultCode.THREAD_NOT_FOUND, "子Agent线程不存在");
        }
        if (!userId.equals(thread.getUserId())) {
            throw new BusinessException(ResultCode.THREAD_ACCESS_DENIED, "无权访问子Agent线程");
        }

        AgentDefinition definition = agentDefinitionRepository.findById(thread.getAgentId());
        if (definition == null || !Boolean.TRUE.equals(definition.getIsActive())) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND, "子Agent不存在");
        }
        if (!userId.equals(definition.getUserId()) || !Objects.equals(parentThreadId, definition.getParentThreadId())) {
            throw new BusinessException(ResultCode.AGENT_ACCESS_DENIED, "子Agent不属于当前团队");
        }

        return new SubAgentContext(thread, definition);
    }

    private void saveMessages(Long threadId, String userContent, String assistantContent) {
        AgentMessage userMsg = new AgentMessage();
        userMsg.setThreadId(threadId);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        userMsg.setTokenCount(0);
        agentMessageRepository.insert(userMsg);

        AgentMessage assistantMsg = new AgentMessage();
        assistantMsg.setThreadId(threadId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantContent);
        assistantMsg.setTokenCount(0);
        agentMessageRepository.insert(assistantMsg);
    }
}
