package com.game.playforge.infrastructure.external.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import org.springframework.stereotype.Component;

/**
 * 基于字符数的Token估算器
 * <p>
 * 轻量级实现，不依赖任何API调用。
 * 按 1 token ≈ 3 字符估算，适用于中英文混合及代码场景。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
public class CharBasedTokenEstimator implements TokenCountEstimator {

    private static final int CHARS_PER_TOKEN = 3;

    @Override
    public int estimateTokenCountInText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        return switch (message) {
            case SystemMessage sm -> estimateTokenCountInText(sm.text());
            case UserMessage um -> estimateTokenCountInText(um.singleText());
            case AiMessage am -> {
                int tokens = 0;
                if (am.text() != null) {
                    tokens += estimateTokenCountInText(am.text());
                }
                if (am.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest req : am.toolExecutionRequests()) {
                        if (req.name() != null) {
                            tokens += estimateTokenCountInText(req.name());
                        }
                        if (req.arguments() != null) {
                            tokens += estimateTokenCountInText(req.arguments());
                        }
                    }
                }
                yield tokens;
            }
            case ToolExecutionResultMessage tr -> {
                int tokens = 0;
                if (tr.text() != null) {
                    tokens += estimateTokenCountInText(tr.text());
                }
                if (tr.toolName() != null) {
                    tokens += estimateTokenCountInText(tr.toolName());
                }
                yield tokens;
            }
            default -> estimateTokenCountInText(message.toString());
        };
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        if (messages == null) {
            return 0;
        }
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokenCountInMessage(message);
        }
        return total;
    }
}
