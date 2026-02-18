package com.game.playforge.config;

import com.game.playforge.common.constant.AgentConstants;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.googleai.GeminiMode;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Gemini模型自定义配置
 * <p>
 * LangChain4j spring starter当前未暴露 sendThinking/returnThinking 配置，
 * 对带工具调用的Gemini请求会触发 thought_signature 缺失问题。
 * 此处显式启用两项能力，确保函数调用链可连续执行。
 * </p>
 */
@Configuration
public class GeminiModelConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiModelConfig.class);

    @Bean("playforgeGeminiChatModel")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils)"
            + ".hasText('${langchain4j.google-ai-gemini.chat-model.api-key:}')")
    public GoogleAiGeminiChatModel playforgeGeminiChatModel(
            Environment environment,
            ObjectProvider<ChatModelListener> listenerProvider) {
        String prefix = "langchain4j.google-ai-gemini.chat-model.";
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder();
        applyCommonConfig(builder, environment, prefix, listenerProvider.orderedStream().toList());
        return builder.build();
    }

    @Bean("playforgeGeminiStreamingChatModel")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils)"
            + ".hasText('${langchain4j.google-ai-gemini.streaming-chat-model.api-key:}')")
    public GoogleAiGeminiStreamingChatModel playforgeGeminiStreamingChatModel(
            Environment environment,
            ObjectProvider<ChatModelListener> listenerProvider) {
        String prefix = "langchain4j.google-ai-gemini.streaming-chat-model.";
        GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder =
                GoogleAiGeminiStreamingChatModel.builder();
        applyCommonConfig(builder, environment, prefix, listenerProvider.orderedStream().toList());
        return builder.build();
    }

    private void applyCommonConfig(Object builder,
                                   Environment environment,
                                   String prefix,
                                   List<ChatModelListener> listeners) {
        String apiKey = environment.getProperty(prefix + "api-key");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Gemini API key is required: " + prefix + "api-key");
        }

        String modelName = environment.getProperty(prefix + "model-name", "gemini-3-pro-preview");
        Integer maxOutputTokens = environment.getProperty(
                prefix + "max-output-tokens", Integer.class, AgentConstants.DEFAULT_MAX_OUTPUT_TOKENS);

        if (builder instanceof GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder chatBuilder) {
            applyBuilder(chatBuilder, environment, prefix, listeners, apiKey, modelName, maxOutputTokens);
            return;
        }
        if (builder instanceof GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder streamingBuilder) {
            applyBuilder(streamingBuilder, environment, prefix, listeners, apiKey, modelName, maxOutputTokens);
            return;
        }

        throw new IllegalArgumentException("Unsupported Gemini builder type: " + builder.getClass());
    }

    private void applyBuilder(GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder,
                              Environment environment,
                              String prefix,
                              List<ChatModelListener> listeners,
                              String apiKey,
                              String modelName,
                              Integer maxOutputTokens) {
        builder.apiKey(apiKey);
        builder.modelName(modelName);
        builder.maxOutputTokens(maxOutputTokens);
        builder.listeners(listeners);

        applyOptionalConfig(builder, environment, prefix);
    }

    private void applyBuilder(GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder,
                              Environment environment,
                              String prefix,
                              List<ChatModelListener> listeners,
                              String apiKey,
                              String modelName,
                              Integer maxOutputTokens) {
        builder.apiKey(apiKey);
        builder.modelName(modelName);
        builder.maxOutputTokens(maxOutputTokens);
        builder.listeners(listeners);

        applyOptionalConfig(builder, environment, prefix);
    }

    private void applyOptionalConfig(Object builder, Environment environment, String prefix) {
        String baseUrl = environment.getProperty(prefix + "base-url");
        Double temperature = environment.getProperty(prefix + "temperature", Double.class);
        Double topP = environment.getProperty(prefix + "top-p", Double.class);
        Integer topK = environment.getProperty(prefix + "top-k", Integer.class);
        Integer seed = environment.getProperty(prefix + "seed", Integer.class);
        Double frequencyPenalty = environment.getProperty(prefix + "frequency-penalty", Double.class);
        Double presencePenalty = environment.getProperty(prefix + "presence-penalty", Double.class);
        Duration timeout = environment.getProperty(prefix + "timeout", Duration.class);
        Integer maxRetries = environment.getProperty(prefix + "max-retries", Integer.class);
        Boolean allowCodeExecution = environment.getProperty(prefix + "allow-code-execution", Boolean.class);
        Boolean includeCodeExecutionOutput = environment.getProperty(
                prefix + "include-code-execution-output", Boolean.class);
        Boolean logRequestsAndResponses = environment.getProperty(
                prefix + "log-requests-and-responses", Boolean.class);
        String[] stopSequences = environment.getProperty(prefix + "stop-sequences", String[].class);
        String geminiMode = environment.getProperty(prefix + "function-calling-config.gemini-mode");
        String[] allowedNames = environment.getProperty(
                prefix + "function-calling-config.allowed-function-names", String[].class);
        GeminiThinkingConfig thinkingConfig = buildThinkingConfig(environment, prefix);

        if (builder instanceof GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder chatBuilder) {
            if (StringUtils.hasText(baseUrl)) {
                chatBuilder.baseUrl(baseUrl);
            }
            if (temperature != null) {
                chatBuilder.temperature(temperature);
            }
            if (topP != null) {
                chatBuilder.topP(topP);
            }
            if (topK != null) {
                chatBuilder.topK(topK);
            }
            if (seed != null) {
                chatBuilder.seed(seed);
            }
            if (frequencyPenalty != null) {
                chatBuilder.frequencyPenalty(frequencyPenalty);
            }
            if (presencePenalty != null) {
                chatBuilder.presencePenalty(presencePenalty);
            }
            if (timeout != null) {
                chatBuilder.timeout(timeout);
            }
            if (maxRetries != null) {
                chatBuilder.maxRetries(maxRetries);
            }
            if (allowCodeExecution != null) {
                chatBuilder.allowCodeExecution(allowCodeExecution);
            }
            if (includeCodeExecutionOutput != null) {
                chatBuilder.includeCodeExecutionOutput(includeCodeExecutionOutput);
            }
            if (logRequestsAndResponses != null) {
                chatBuilder.logRequestsAndResponses(logRequestsAndResponses);
            }
            if (stopSequences != null && stopSequences.length > 0) {
                chatBuilder.stopSequences(Arrays.stream(stopSequences).filter(StringUtils::hasText).toList());
            }
            applyFunctionCallingConfig(chatBuilder, geminiMode, allowedNames);

            // Critical for Gemini tool calls: preserve and replay thought signatures.
            chatBuilder.sendThinking(Boolean.TRUE);
            chatBuilder.returnThinking(Boolean.TRUE);
            chatBuilder.thinkingConfig(thinkingConfig);
            return;
        }

        if (builder instanceof GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder streamingBuilder) {
            if (StringUtils.hasText(baseUrl)) {
                streamingBuilder.baseUrl(baseUrl);
            }
            if (temperature != null) {
                streamingBuilder.temperature(temperature);
            }
            if (topP != null) {
                streamingBuilder.topP(topP);
            }
            if (topK != null) {
                streamingBuilder.topK(topK);
            }
            if (seed != null) {
                streamingBuilder.seed(seed);
            }
            if (frequencyPenalty != null) {
                streamingBuilder.frequencyPenalty(frequencyPenalty);
            }
            if (presencePenalty != null) {
                streamingBuilder.presencePenalty(presencePenalty);
            }
            if (timeout != null) {
                streamingBuilder.timeout(timeout);
            }
            if (allowCodeExecution != null) {
                streamingBuilder.allowCodeExecution(allowCodeExecution);
            }
            if (includeCodeExecutionOutput != null) {
                streamingBuilder.includeCodeExecutionOutput(includeCodeExecutionOutput);
            }
            if (logRequestsAndResponses != null) {
                streamingBuilder.logRequestsAndResponses(logRequestsAndResponses);
            }
            if (stopSequences != null && stopSequences.length > 0) {
                streamingBuilder.stopSequences(Arrays.stream(stopSequences).filter(StringUtils::hasText).toList());
            }
            applyFunctionCallingConfig(streamingBuilder, geminiMode, allowedNames);

            // Critical for Gemini tool calls: preserve and replay thought signatures.
            streamingBuilder.sendThinking(Boolean.TRUE);
            streamingBuilder.returnThinking(Boolean.TRUE);
            streamingBuilder.thinkingConfig(thinkingConfig);
            return;
        }

        throw new IllegalArgumentException("Unsupported Gemini builder type: " + builder.getClass());
    }

    private void applyFunctionCallingConfig(
            GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder,
            String geminiMode,
            String[] allowedNames) {
        if (!StringUtils.hasText(geminiMode)) {
            return;
        }
        try {
            GeminiMode mode = GeminiMode.valueOf(geminiMode.trim().toUpperCase(Locale.ROOT));
            builder.toolConfig(mode, sanitizeFunctionNames(allowedNames));
        } catch (IllegalArgumentException e) {
            log.warn("忽略非法Gemini function calling mode: {}", geminiMode);
        }
    }

    private void applyFunctionCallingConfig(
            GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder,
            String geminiMode,
            String[] allowedNames) {
        if (!StringUtils.hasText(geminiMode)) {
            return;
        }
        try {
            GeminiMode mode = GeminiMode.valueOf(geminiMode.trim().toUpperCase(Locale.ROOT));
            builder.toolConfig(mode, sanitizeFunctionNames(allowedNames));
        } catch (IllegalArgumentException e) {
            log.warn("忽略非法Gemini function calling mode: {}", geminiMode);
        }
    }

    private String[] sanitizeFunctionNames(String[] names) {
        if (names == null || names.length == 0) {
            return new String[0];
        }
        return Arrays.stream(names)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toArray(String[]::new);
    }

    private GeminiThinkingConfig buildThinkingConfig(Environment environment, String prefix) {
        Boolean includeThoughts = environment.getProperty(
                prefix + "thinking-config.include-thoughts", Boolean.class, Boolean.TRUE);
        Integer thinkingBudget = environment.getProperty(
                prefix + "thinking-config.thinking-budget", Integer.class);
        String thinkingLevel = environment.getProperty(prefix + "thinking-config.thinking-level");

        GeminiThinkingConfig.Builder builder = GeminiThinkingConfig.builder()
                .includeThoughts(includeThoughts);
        if (thinkingBudget != null) {
            builder.thinkingBudget(thinkingBudget);
        }
        if (StringUtils.hasText(thinkingLevel)) {
            builder.thinkingLevel(thinkingLevel.trim());
        }
        return builder.build();
    }
}
