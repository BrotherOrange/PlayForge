package com.game.playforge.infrastructure.external.ai;

import com.game.playforge.common.constant.AgentConstants;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带摘要压缩的聊天记忆存储
 * <p>
 * 装饰器模式包装 {@link RedisChatMemoryStore}，当消息数超过阈值时，
 * 自动调用LLM生成摘要替代旧消息，保留近期消息原文。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
public class SummarizingChatMemoryStore implements ChatMemoryStore {

    private static final long SUMMARIZATION_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final String SUMMARY_PREFIX = "[Context Summary]";

    private static final String SUMMARY_PROMPT = """
            Summarize the following conversation history concisely, preserving:
            - Key decisions and conclusions
            - Important context and requirements
            - Current task state and progress
            Keep it under 500 words. Output the summary directly, no preamble.

            """;

    private final RedisChatMemoryStore delegate;
    private final ModelProviderRegistry modelProviderRegistry;
    private final CharBasedTokenEstimator tokenEstimator;
    private final Map<Object, Long> recentlySummarized = new ConcurrentHashMap<>();

    public SummarizingChatMemoryStore(RedisChatMemoryStore delegate,
                                      ModelProviderRegistry modelProviderRegistry,
                                      CharBasedTokenEstimator tokenEstimator) {
        this.delegate = delegate;
        this.modelProviderRegistry = modelProviderRegistry;
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return delegate.getMessages(memoryId);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        int estimatedTokens = tokenEstimator.estimateTokenCountInMessages(messages);
        if (estimatedTokens <= AgentConstants.SUMMARIZATION_TRIGGER_TOKENS) {
            delegate.updateMessages(memoryId, messages);
            return;
        }
        log.info("记忆Token数超过阈值, memoryId={}, tokens={}, threshold={}",
                memoryId, estimatedTokens, AgentConstants.SUMMARIZATION_TRIGGER_TOKENS);

        Long lastSummarized = recentlySummarized.get(memoryId);
        if (lastSummarized != null && System.currentTimeMillis() - lastSummarized < SUMMARIZATION_COOLDOWN_MS) {
            delegate.updateMessages(memoryId, messages);
            return;
        }

        try {
            List<ChatMessage> compressed = compressMessages(memoryId, messages);
            delegate.updateMessages(memoryId, compressed);
            recentlySummarized.put(memoryId, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("记忆摘要生成失败, 回退到原始消息, memoryId={}", memoryId, e);
            recentlySummarized.put(memoryId, System.currentTimeMillis());
            delegate.updateMessages(memoryId, messages);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        recentlySummarized.remove(memoryId);
        delegate.deleteMessages(memoryId);
    }

    private List<ChatMessage> compressMessages(Object memoryId, List<ChatMessage> messages) {
        // Iterate from end, accumulate tokens until reaching RECENT_TOKENS_TO_KEEP
        int recentTokens = 0;
        int splitIndex = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--) {
            int msgTokens = tokenEstimator.estimateTokenCountInMessage(messages.get(i));
            if (recentTokens + msgTokens > AgentConstants.RECENT_TOKENS_TO_KEEP) {
                break;
            }
            recentTokens += msgTokens;
            splitIndex = i;
        }
        // Ensure at least 1 message is summarized
        if (splitIndex <= 0) {
            splitIndex = 1;
        }

        List<ChatMessage> oldMessages = messages.subList(0, splitIndex);
        List<ChatMessage> recentMessages = messages.subList(splitIndex, messages.size());

        String existingSummary = null;
        List<ChatMessage> toSummarize = oldMessages;
        if (!oldMessages.isEmpty() && oldMessages.getFirst() instanceof SystemMessage sm
                && sm.text().startsWith(SUMMARY_PREFIX)) {
            existingSummary = sm.text();
            toSummarize = oldMessages.subList(1, oldMessages.size());
        }

        String summaryText = generateSummary(existingSummary, toSummarize);

        List<ChatMessage> result = new ArrayList<>();
        result.add(SystemMessage.from(SUMMARY_PREFIX + "\n" + summaryText));
        result.addAll(recentMessages);

        int originalTokens = tokenEstimator.estimateTokenCountInMessages(messages);
        int compressedTokens = tokenEstimator.estimateTokenCountInMessages(result);
        log.info("记忆摘要压缩完成, memoryId={}, 原消息数={}, 压缩后={}, 原Token数={}, 压缩后Token数={}",
                memoryId, messages.size(), result.size(), originalTokens, compressedTokens);
        return result;
    }

    private String generateSummary(String existingSummary, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder(SUMMARY_PROMPT);

        if (existingSummary != null) {
            sb.append("Previous summary:\n");
            sb.append(existingSummary.substring(SUMMARY_PREFIX.length()).trim());
            sb.append("\n\nNew messages to incorporate:\n");
        } else {
            sb.append("Conversation:\n");
        }

        for (ChatMessage msg : messages) {
            switch (msg) {
                case UserMessage um -> sb.append("User: ").append(um.singleText()).append("\n");
                case AiMessage am -> {
                    if (am.text() != null) {
                        sb.append("Assistant: ").append(am.text()).append("\n");
                    }
                }
                default -> {
                    // Skip SystemMessage, ToolExecutionRequestMessage, ToolExecutionResultMessage
                }
            }
        }

        ChatModel model = modelProviderRegistry.getCheapestChatModel();
        return model.chat(sb.toString());
    }
}
