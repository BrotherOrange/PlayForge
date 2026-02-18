package com.game.playforge.application.service.impl;

import com.game.playforge.application.dto.AgentChatResponse;
import com.game.playforge.application.dto.AgentStreamEvent;
import com.game.playforge.application.service.AgentChatAppService;
import com.game.playforge.application.service.agent.AgentChatService;
import com.game.playforge.application.service.agent.AgentFactory;
import com.game.playforge.application.service.agent.AgentStreamingChatService;
import com.game.playforge.application.service.agent.SubAgentService;
import com.game.playforge.common.constant.AgentConstants;
import com.game.playforge.common.enums.ThreadStatus;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.AgentDefinition;
import com.game.playforge.domain.model.AgentMessage;
import com.game.playforge.domain.model.AgentThread;
import com.game.playforge.domain.repository.AgentDefinitionRepository;
import com.game.playforge.domain.repository.AgentMessageRepository;
import com.game.playforge.domain.repository.AgentThreadRepository;
import com.game.playforge.infrastructure.external.ai.AsyncTaskManager;
import com.game.playforge.infrastructure.external.ai.RedisChatMemoryStore;
import com.game.playforge.application.service.agent.tools.SubAgentTool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent聊天应用服务实现
 * <p>
 * 提供同步和流式聊天能力，包含线程恢复逻辑和消息持久化。
 * 支持Lead Agent的子Agent工具注入。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Service
public class AgentChatAppServiceImpl implements AgentChatAppService {

    private static final int MAX_RATE_LIMIT_RETRIES = 2;
    private static final long BASE_BACKOFF_MILLIS = 2000L;

    private final AgentFactory agentFactory;
    private final AgentThreadRepository agentThreadRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final TransactionTemplate transactionTemplate;
    private final SubAgentService subAgentService;
    private final Map<Long, AsyncTaskManager> taskManagers = new ConcurrentHashMap<>();

    public AgentChatAppServiceImpl(AgentFactory agentFactory,
                                   AgentThreadRepository agentThreadRepository,
                                   AgentDefinitionRepository agentDefinitionRepository,
                                   AgentMessageRepository agentMessageRepository,
                                   RedisChatMemoryStore redisChatMemoryStore,
                                   TransactionTemplate transactionTemplate,
                                   @Lazy SubAgentService subAgentService) {
        this.agentFactory = agentFactory;
        this.agentThreadRepository = agentThreadRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentMessageRepository = agentMessageRepository;
        this.redisChatMemoryStore = redisChatMemoryStore;
        this.transactionTemplate = transactionTemplate;
        this.subAgentService = subAgentService;
    }

    @Override
    public AgentChatResponse chat(Long userId, Long threadId, String message) {
        log.info("同步聊天, userId={}, threadId={}", userId, threadId);

        AgentThread thread = validateAndGetThread(userId, threadId);
        AgentDefinition definition = getAgentDefinition(thread.getAgentId());

        recoverMemoryIfNeeded(thread, definition);

        List<Object> extraTools = buildExtraTools(definition, userId, threadId);
        AgentChatService agent = agentFactory.createAgent(definition, threadId, userId, extraTools);
        String response = chatWithRetry(agent, message, threadId);

        transactionTemplate.executeWithoutResult(status -> {
            saveMessages(threadId, message, response);
            updateThreadStats(threadId, 2);
        });

        log.info("同步聊天完成, threadId={}, responseLength={}", threadId, response.length());
        return new AgentChatResponse(threadId, response);
    }

    @Override
    public Flux<AgentStreamEvent> chatStream(Long userId, Long threadId, String message) {
        log.info("流式聊天, userId={}, threadId={}", userId, threadId);

        AgentThread thread = validateAndGetThread(userId, threadId);
        AgentDefinition definition = getAgentDefinition(thread.getAgentId());

        recoverMemoryIfNeeded(thread, definition);

        // Save user message immediately so it's visible in DB even if user switches away
        transactionTemplate.executeWithoutResult(status -> {
            saveUserMessage(threadId, message);
            updateThreadStats(threadId, 1);
        });

        List<Object> extraTools = buildExtraTools(definition, userId, threadId);
        AgentStreamingChatService agent = agentFactory.createStreamingAgent(definition, threadId, userId, extraTools);

        return Flux.create(sink -> {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder fullThinking = new StringBuilder();
            startStreamingWithRetry(agent, message, threadId, sink, fullResponse, fullThinking, 0);

            sink.onCancel(() -> {
                log.info("流式聊天被取消, threadId={}", threadId);
            });
        });
    }

    /**
     * 构建额外工具列表（如SubAgentTool）
     */
    private List<Object> buildExtraTools(AgentDefinition definition, Long userId, Long threadId) {
        if (!hasSubAgentTool(definition)) {
            AsyncTaskManager removed = taskManagers.remove(threadId);
            if (removed != null) {
                removed.shutdown();
            }
            return Collections.emptyList();
        }

        AsyncTaskManager taskManager = taskManagers.computeIfAbsent(threadId, ignored -> new AsyncTaskManager());
        SubAgentTool subAgentTool = new SubAgentTool(userId, threadId, subAgentService, taskManager);

        log.info("已注入SubAgentTool, userId={}, threadId={}", userId, threadId);
        return List.of(subAgentTool);
    }

    @PreDestroy
    public void shutdownTaskManagers() {
        for (AsyncTaskManager manager : taskManagers.values()) {
            manager.shutdown();
        }
        taskManagers.clear();
    }

    private boolean hasSubAgentTool(AgentDefinition definition) {
        if (definition.getToolNames() == null || definition.getToolNames().isBlank()) {
            return false;
        }
        return Arrays.stream(definition.getToolNames().split(","))
                .map(String::trim)
                .anyMatch("subAgentTool"::equals);
    }

    private AgentThread validateAndGetThread(Long userId, Long threadId) {
        AgentThread thread = agentThreadRepository.findById(threadId);
        if (thread == null) {
            throw new BusinessException(ResultCode.THREAD_NOT_FOUND);
        }
        if (ThreadStatus.DELETED.name().equals(thread.getStatus())) {
            throw new BusinessException(ResultCode.THREAD_NOT_FOUND);
        }
        if (!thread.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.THREAD_ACCESS_DENIED);
        }
        return thread;
    }

    private AgentDefinition getAgentDefinition(Long agentId) {
        AgentDefinition definition = agentDefinitionRepository.findById(agentId);
        if (definition == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND);
        }
        return definition;
    }

    private void recoverMemoryIfNeeded(AgentThread thread, AgentDefinition definition) {
        List<ChatMessage> existing = redisChatMemoryStore.getMessages(thread.getId());
        if (!existing.isEmpty()) {
            return;
        }

        if (thread.getMessageCount() == null || thread.getMessageCount() == 0) {
            return;
        }

        log.info("Redis记忆过期，从MySQL恢复, threadId={}, messageCount={}",
                thread.getId(), thread.getMessageCount());

        int windowSize = definition.getMemoryWindowSize() != null
                ? definition.getMemoryWindowSize()
                : AgentConstants.DEFAULT_MEMORY_WINDOW_SIZE;
        List<AgentMessage> dbMessages = agentMessageRepository.findLatestByThreadId(
                thread.getId(), windowSize);

        if (dbMessages.isEmpty()) {
            return;
        }
        Collections.reverse(dbMessages);

        List<ChatMessage> chatMessages = new ArrayList<>();
        for (AgentMessage msg : dbMessages) {
            switch (msg.getRole()) {
                case "user" -> chatMessages.add(UserMessage.from(msg.getContent()));
                case "assistant" -> chatMessages.add(AiMessage.from(msg.getContent()));
                default -> log.debug("跳过非user/assistant消息, role={}", msg.getRole());
            }
        }

        if (!chatMessages.isEmpty()) {
            redisChatMemoryStore.updateMessages(thread.getId(), chatMessages);
            log.info("Redis记忆恢复完成, threadId={}, recoveredCount={}", thread.getId(), chatMessages.size());
        }
    }

    private void saveUserMessage(Long threadId, String userContent) {
        AgentMessage userMsg = new AgentMessage();
        userMsg.setThreadId(threadId);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        userMsg.setTokenCount(0);
        agentMessageRepository.insert(userMsg);
    }

    private void saveAssistantMessage(Long threadId, String assistantContent) {
        AgentMessage assistantMsg = new AgentMessage();
        assistantMsg.setThreadId(threadId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantContent);
        assistantMsg.setTokenCount(0);
        agentMessageRepository.insert(assistantMsg);
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

    private void updateThreadStats(Long threadId, int messageDelta) {
        agentThreadRepository.incrementMessageCount(threadId, messageDelta, LocalDateTime.now());
    }

    private String chatWithRetry(AgentChatService agent, String message, Long threadId) {
        for (int attempt = 0; attempt <= MAX_RATE_LIMIT_RETRIES; attempt++) {
            try {
                return agent.chat(message);
            } catch (Exception e) {
                if (!isRetryableError(e) || attempt >= MAX_RATE_LIMIT_RETRIES) {
                    throw e;
                }
                long waitMillis = computeBackoffMillis(attempt);
                String reason = isRateLimitError(e) ? "速率限制" : "上游网络超时";
                log.warn("同步聊天触发{}, threadId={}, 等待{}ms后重试 ({}/{})",
                        reason, threadId, waitMillis, attempt + 1, MAX_RATE_LIMIT_RETRIES);
                if (!sleepQuietly(waitMillis)) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private void startStreamingWithRetry(AgentStreamingChatService agent,
                                         String message,
                                         Long threadId,
                                         FluxSink<AgentStreamEvent> sink,
                                         StringBuilder fullResponse,
                                         StringBuilder fullThinking,
                                         int attempt) {
        if (sink.isCancelled()) {
            return;
        }

        TokenStream tokenStream;
        try {
            tokenStream = agent.chat(message);
        } catch (Exception error) {
            boolean canRetry = isRetryableError(error)
                    && fullResponse.isEmpty()
                    && attempt < MAX_RATE_LIMIT_RETRIES;
            if (canRetry) {
                long waitMillis = computeBackoffMillis(attempt);
                String reason = isRateLimitError(error) ? "速率限制" : "上游网络超时";
                log.warn("流式聊天初始化触发{}, threadId={}, 等待{}ms后重试 ({}/{})",
                        reason, threadId, waitMillis, attempt + 1, MAX_RATE_LIMIT_RETRIES);
                if (!sleepQuietly(waitMillis)) {
                    sink.error(error);
                    return;
                }
                startStreamingWithRetry(agent, message, threadId, sink, fullResponse, fullThinking, attempt + 1);
                return;
            }
            sink.error(error);
            return;
        }
        tokenStream
                .onPartialResponse(token -> {
                    if (sink.isCancelled()) {
                        return;
                    }
                    fullResponse.append(token);
                    sink.next(AgentStreamEvent.token(token));
                })
                .onPartialThinking(partialThinking -> {
                    if (sink.isCancelled() || partialThinking == null) {
                        return;
                    }
                    String thinkingText = partialThinking.text();
                    if (thinkingText == null || thinkingText.isBlank()) {
                        return;
                    }
                    fullThinking.append(thinkingText);
                    sink.next(AgentStreamEvent.thinking(thinkingText));
                })
                .onCompleteResponse(resp -> {
                    if (sink.isCancelled()) {
                        return;
                    }
                    emitCompletionFallbackIfNeeded(threadId, sink, fullResponse, fullThinking, resp);
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            // User message already saved before stream started
                            saveAssistantMessage(threadId, fullResponse.toString());
                            updateThreadStats(threadId, 1);
                        });
                    } catch (Exception e) {
                        log.error("保存消息失败, threadId={}", threadId, e);
                    }
                    sink.complete();
                })
                .onError(error -> {
                    if (sink.isCancelled()) {
                        return;
                    }
                    boolean canRetry = isRetryableError(error)
                            && fullResponse.isEmpty()
                            && attempt < MAX_RATE_LIMIT_RETRIES;
                    if (canRetry) {
                        long waitMillis = computeBackoffMillis(attempt);
                        String reason = isRateLimitError(error) ? "速率限制" : "上游网络超时";
                        log.warn("流式聊天触发{}, threadId={}, 等待{}ms后重试 ({}/{})",
                                reason, threadId, waitMillis, attempt + 1, MAX_RATE_LIMIT_RETRIES);
                        if (!sleepQuietly(waitMillis)) {
                            sink.error(error);
                            return;
                        }
                        startStreamingWithRetry(agent, message, threadId, sink, fullResponse, fullThinking, attempt + 1);
                        return;
                    }
                    log.error("流式聊天错误, threadId={}", threadId, error);
                    sink.error(error);
                })
                .start();
    }

    /**
     * 某些模型在工具调用链中可能不触发partial token，仅在complete响应里携带文本。
     * 这里做统一兜底，避免前端出现“无响应”。
     */
    private void emitCompletionFallbackIfNeeded(Long threadId,
                                                FluxSink<AgentStreamEvent> sink,
                                                StringBuilder fullResponse,
                                                StringBuilder fullThinking,
                                                ChatResponse completeResponse) {
        if (fullThinking.isEmpty()) {
            String completionThinking = extractCompletionThinking(completeResponse);
            if (!completionThinking.isBlank()) {
                fullThinking.append(completionThinking);
                sink.next(AgentStreamEvent.thinking(completionThinking));
            }
        }

        if (!fullResponse.isEmpty()) {
            return;
        }

        String completionText = extractCompletionText(completeResponse);
        if (!completionText.isBlank()) {
            fullResponse.append(completionText);
            sink.next(AgentStreamEvent.token(completionText));
            return;
        }

        AsyncTaskManager taskManager = taskManagers.get(threadId);
        if (taskManager != null && taskManager.pendingCount() > 0) {
            String progressHint = String.format(
                    "已派发 %d 个子Agent任务，正在处理中。请稍候，我会继续汇总结果。",
                    taskManager.pendingCount());
            fullResponse.append(progressHint);
            sink.next(AgentStreamEvent.token(progressHint));
        }
    }

    private String extractCompletionText(ChatResponse completeResponse) {
        if (completeResponse == null || completeResponse.aiMessage() == null) {
            return "";
        }
        String text = completeResponse.aiMessage().text();
        return text == null ? "" : text.trim();
    }

    private String extractCompletionThinking(ChatResponse completeResponse) {
        if (completeResponse == null || completeResponse.aiMessage() == null) {
            return "";
        }
        String thinking = completeResponse.aiMessage().thinking();
        return thinking == null ? "" : thinking.trim();
    }

    private boolean isRetryableError(Throwable throwable) {
        return isRateLimitError(throwable) || isTransientNetworkError(throwable);
    }

    private boolean isRateLimitError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if ("RateLimitException".equals(cursor.getClass().getSimpleName())) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("rate_limit") || lower.contains("rate limit")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isTransientNetworkError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SocketTimeoutException
                    || cursor instanceof IOException) {
                return true;
            }
            String simpleName = cursor.getClass().getSimpleName();
            if ("ResourceAccessException".equals(simpleName)
                    || "ReadTimeoutException".equals(simpleName)
                    || "ConnectTimeoutException".equals(simpleName)) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("i/o error")
                        || lower.contains("readtimeout")
                        || lower.contains("timeout")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private long computeBackoffMillis(int attempt) {
        long base = (long) Math.pow(2, attempt) * BASE_BACKOFF_MILLIS;
        long jitter = ThreadLocalRandom.current().nextLong(300, 1200);
        return base + jitter;
    }

    private boolean sleepQuietly(long waitMillis) {
        try {
            Thread.sleep(waitMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
