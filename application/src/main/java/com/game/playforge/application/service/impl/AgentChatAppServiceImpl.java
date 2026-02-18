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
import java.util.function.Consumer;

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
    private static final int STREAM_PERSIST_TOKEN_STEP = 80;

    private static final class StreamPersistenceState {
        private Long assistantMessageId;
        private int lastPersistedLength;
    }

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
            startStreamingWithRetry(
                    agent, message, threadId, sink, fullResponse, fullThinking,
                    0, false, null);

            sink.onCancel(() -> {
                log.info("流式聊天被取消, threadId={}", threadId);
            });
        });
    }

    @Override
    public Flux<AgentStreamEvent> chatWithProgress(Long userId, Long threadId, String message) {
        log.info("带进度聊天(非流式), userId={}, threadId={}", userId, threadId);

        AgentThread thread = validateAndGetThread(userId, threadId);
        AgentDefinition definition = getAgentDefinition(thread.getAgentId());
        recoverMemoryIfNeeded(thread, definition);

        // Save user message first so it is visible while lead agent is still processing.
        transactionTemplate.executeWithoutResult(status -> {
            saveUserMessage(threadId, message);
            updateThreadStats(threadId, 1);
        });

        return Flux.create(sink -> {
            // Use virtual thread so SSE progress events are pushed in real-time
            // while the sync chat blocks until complete.
            Thread.startVirtualThread(() -> {
                try {
                    Consumer<AgentStreamEvent> progressCallback = event -> {
                        if (AgentStreamEvent.TYPE_PROGRESS.equals(event.type())
                                && event.content() != null
                                && !event.content().isBlank()) {
                            transactionTemplate.executeWithoutResult(status -> {
                                saveToolMessage(threadId, AgentStreamEvent.TYPE_PROGRESS, event.content());
                                updateThreadStats(threadId, 1);
                            });
                        }
                        if (!sink.isCancelled()) {
                            sink.next(event);
                        }
                    };

                    // Accumulate thinking from all LLM rounds for persistence
                    StringBuilder accumulatedThinking = new StringBuilder();

                    // Intercept each LLM round's complete response and push to SSE immediately.
                    // This gives per-round granularity without token-by-token streaming.
                    Consumer<ChatResponse> responseInterceptor = chatResponse -> {
                        if (chatResponse.aiMessage() == null || sink.isCancelled()) {
                            return;
                        }
                        String thinking = chatResponse.aiMessage().thinking();
                        if (thinking != null && !thinking.isBlank()) {
                            accumulatedThinking.append(thinking).append("\n");
                            sink.next(AgentStreamEvent.thinking(thinking));
                        }
                        String text = chatResponse.aiMessage().text();
                        if (text != null && !text.isBlank()) {
                            sink.next(AgentStreamEvent.response(text));
                        }
                    };

                    List<Object> extraTools = buildExtraTools(definition, userId, threadId, progressCallback);
                    AgentChatService agent = agentFactory.createAgent(
                            definition, threadId, userId, extraTools, responseInterceptor);
                    String response = chatWithRetry(agent, message, threadId);

                    // Persist thinking + final assistant message
                    String thinkingToSave = accumulatedThinking.toString().trim();
                    transactionTemplate.executeWithoutResult(status -> {
                        int delta = 0;
                        if (!thinkingToSave.isBlank()) {
                            saveToolMessage(threadId, AgentStreamEvent.TYPE_THINKING, thinkingToSave);
                            delta++;
                        }
                        saveAssistantMessage(threadId, response);
                        delta++;
                        updateThreadStats(threadId, delta);
                    });

                    // Responses already emitted by responseInterceptor; just signal completion
                    if (!sink.isCancelled()) {
                        sink.complete();
                    }

                    log.info("带进度聊天完成, threadId={}, responseLength={}", threadId, response.length());
                } catch (Exception e) {
                    log.error("带进度聊天失败, threadId={}", threadId, e);
                    if (!sink.isCancelled()) {
                        sink.next(AgentStreamEvent.error(e.getMessage()));
                        sink.complete();
                    }
                }
            });

            sink.onCancel(() -> {
                log.info("带进度聊天被取消, threadId={}", threadId);
            });
        });
    }

    /**
     * 构建额外工具列表（如SubAgentTool）
     */
    private List<Object> buildExtraTools(AgentDefinition definition, Long userId, Long threadId) {
        return buildExtraTools(definition, userId, threadId, null);
    }

    private List<Object> buildExtraTools(AgentDefinition definition, Long userId, Long threadId,
                                         Consumer<AgentStreamEvent> progressCallback) {
        if (!hasSubAgentTool(definition)) {
            AsyncTaskManager removed = taskManagers.remove(threadId);
            if (removed != null) {
                removed.shutdown();
            }
            return Collections.emptyList();
        }

        AsyncTaskManager taskManager = taskManagers.computeIfAbsent(threadId, ignored -> new AsyncTaskManager());
        SubAgentTool subAgentTool = new SubAgentTool(userId, threadId, subAgentService, taskManager, progressCallback);

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

    private Long saveAssistantMessageReturningId(Long threadId, String assistantContent) {
        AgentMessage assistantMsg = new AgentMessage();
        assistantMsg.setThreadId(threadId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantContent);
        assistantMsg.setTokenCount(0);
        agentMessageRepository.insert(assistantMsg);
        return assistantMsg.getId();
    }

    private void saveToolMessage(Long threadId, String toolName, String content) {
        AgentMessage toolMsg = new AgentMessage();
        toolMsg.setThreadId(threadId);
        toolMsg.setRole("tool");
        toolMsg.setToolName(toolName);
        toolMsg.setContent(content);
        toolMsg.setTokenCount(0);
        agentMessageRepository.insert(toolMsg);
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

    private void persistStreamingAssistantIfNeeded(Long threadId,
                                                   String fullContent,
                                                   StreamPersistenceState state) {
        if (fullContent == null || fullContent.isBlank()) {
            return;
        }
        int currentLength = fullContent.length();
        if (currentLength - state.lastPersistedLength < STREAM_PERSIST_TOKEN_STEP) {
            return;
        }
        if (state.assistantMessageId == null) {
            Long messageId = saveAssistantMessageReturningId(threadId, fullContent);
            if (messageId == null) {
                return;
            }
            state.assistantMessageId = messageId;
            state.lastPersistedLength = currentLength;
            updateThreadStats(threadId, 1);
            return;
        }
        agentMessageRepository.updateContentById(state.assistantMessageId, fullContent);
        state.lastPersistedLength = currentLength;
    }

    /**
     * @return 本次完成时是否新增了assistant消息（用于线程消息数递增）
     */
    private int persistFinalStreamingAssistant(Long threadId,
                                               String fullContent,
                                               StreamPersistenceState state) {
        if (state.assistantMessageId == null) {
            Long messageId = saveAssistantMessageReturningId(threadId, fullContent);
            if (messageId == null) {
                return 0;
            }
            state.assistantMessageId = messageId;
            state.lastPersistedLength = fullContent.length();
            return 1;
        }
        agentMessageRepository.updateContentById(state.assistantMessageId, fullContent);
        state.lastPersistedLength = fullContent.length();
        return 0;
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
                                         int attempt,
                                         boolean persistWhenClientDisconnected,
                                         StreamPersistenceState persistenceState) {
        if (sink.isCancelled() && !persistWhenClientDisconnected) {
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
                startStreamingWithRetry(
                        agent, message, threadId, sink, fullResponse, fullThinking,
                        attempt + 1, persistWhenClientDisconnected, persistenceState);
                return;
            }
            sink.error(error);
            return;
        }
        tokenStream
                .onPartialResponse(token -> {
                    if ((sink.isCancelled() && !persistWhenClientDisconnected) || token == null || token.isBlank()) {
                        return;
                    }
                    fullResponse.append(token);
                    if (persistWhenClientDisconnected && persistenceState != null) {
                        persistStreamingAssistantIfNeeded(threadId, fullResponse.toString(), persistenceState);
                    }
                    if (!sink.isCancelled()) {
                        sink.next(AgentStreamEvent.token(token));
                    }
                })
                .onPartialThinking(partialThinking -> {
                    if ((sink.isCancelled() && !persistWhenClientDisconnected) || partialThinking == null) {
                        return;
                    }
                    String thinkingText = partialThinking.text();
                    if (thinkingText == null || thinkingText.isBlank()) {
                        return;
                    }
                    fullThinking.append(thinkingText);
                    if (!sink.isCancelled()) {
                        sink.next(AgentStreamEvent.thinking(thinkingText));
                    }
                })
                .onCompleteResponse(resp -> {
                    if (sink.isCancelled() && !persistWhenClientDisconnected) {
                        return;
                    }
                    boolean emitToClient = !sink.isCancelled();
                    emitCompletionFallbackIfNeeded(
                            threadId, sink, fullResponse, fullThinking, resp, emitToClient);
                    try {
                        String thinkingToSave = fullThinking.toString().trim();
                        transactionTemplate.executeWithoutResult(status -> {
                            // User message already saved before stream started
                            int delta = 0;
                            if (!thinkingToSave.isBlank()) {
                                saveToolMessage(threadId, AgentStreamEvent.TYPE_THINKING, thinkingToSave);
                                delta += 1;
                            }
                            if (persistWhenClientDisconnected && persistenceState != null) {
                                delta += persistFinalStreamingAssistant(
                                        threadId, fullResponse.toString(), persistenceState);
                            } else {
                                saveAssistantMessage(threadId, fullResponse.toString());
                                delta += 1;
                            }
                            updateThreadStats(threadId, delta);
                        });
                    } catch (Exception e) {
                        log.error("保存消息失败, threadId={}", threadId, e);
                    }
                    if (!sink.isCancelled()) {
                        sink.complete();
                    }
                })
                .onError(error -> {
                    if (sink.isCancelled() && !persistWhenClientDisconnected) {
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
                        startStreamingWithRetry(
                                agent, message, threadId, sink, fullResponse, fullThinking,
                                attempt + 1, persistWhenClientDisconnected, persistenceState);
                        return;
                    }
                    log.error("流式聊天错误, threadId={}", threadId, error);
                    if (!sink.isCancelled()) {
                        sink.error(error);
                    }
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
                                                ChatResponse completeResponse,
                                                boolean emitToClient) {
        if (fullThinking.isEmpty()) {
            String completionThinking = extractCompletionThinking(completeResponse);
            if (!completionThinking.isBlank()) {
                fullThinking.append(completionThinking);
                if (emitToClient) {
                    sink.next(AgentStreamEvent.thinking(completionThinking));
                }
            }
        }

        if (!fullResponse.isEmpty()) {
            return;
        }

        String completionText = extractCompletionText(completeResponse);
        if (!completionText.isBlank()) {
            fullResponse.append(completionText);
            if (emitToClient) {
                sink.next(AgentStreamEvent.token(completionText));
            }
            return;
        }

        AsyncTaskManager taskManager = taskManagers.get(threadId);
        if (taskManager != null && taskManager.pendingCount() > 0) {
            String progressHint = String.format(
                    "已派发 %d 个子Agent任务，正在处理中。请稍候，我会继续汇总结果。",
                    taskManager.pendingCount());
            fullResponse.append(progressHint);
            if (emitToClient) {
                sink.next(AgentStreamEvent.token(progressHint));
            }
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
