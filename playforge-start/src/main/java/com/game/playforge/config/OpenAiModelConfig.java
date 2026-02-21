package com.game.playforge.config;

import com.game.playforge.common.constant.AgentConstants;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * OpenAI模型自定义配置
 * <p>
 * GPT-5.x 不接受 max_tokens，必须使用 max_completion_tokens。
 * 此处通过自定义Bean强制走 max_completion_tokens，避免 starter 默认参数触发 400。
 * </p>
 */
@Configuration
public class OpenAiModelConfig {

    @Bean("playforgeOpenAiChatModel")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils)"
            + ".hasText('${langchain4j.open-ai.chat-model.api-key:}')")
    public OpenAiChatModel playforgeOpenAiChatModel(
            Environment environment,
            ObjectProvider<ChatModelListener> listenerProvider) {
        String prefix = "langchain4j.open-ai.chat-model.";
        String apiKey = environment.getProperty(prefix + "api-key");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OpenAI API key is required: " + prefix + "api-key");
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(environment.getProperty(prefix + "model-name", "gpt-5.2"))
                .maxCompletionTokens(resolveMaxCompletionTokens(environment, prefix))
                .listeners(listenerProvider.orderedStream().toList());

        applyCommonOptions(builder, environment, prefix);

        Integer maxRetries = environment.getProperty(prefix + "max-retries", Integer.class);
        if (maxRetries != null) {
            builder.maxRetries(maxRetries);
        }
        return builder.build();
    }

    @Bean("playforgeOpenAiStreamingChatModel")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils)"
            + ".hasText('${langchain4j.open-ai.streaming-chat-model.api-key:}')")
    public OpenAiStreamingChatModel playforgeOpenAiStreamingChatModel(
            Environment environment,
            ObjectProvider<ChatModelListener> listenerProvider) {
        String prefix = "langchain4j.open-ai.streaming-chat-model.";
        String apiKey = environment.getProperty(prefix + "api-key");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OpenAI API key is required: " + prefix + "api-key");
        }

        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(environment.getProperty(prefix + "model-name", "gpt-5.2"))
                .maxCompletionTokens(resolveMaxCompletionTokens(environment, prefix))
                .listeners(listenerProvider.orderedStream().toList());

        applyCommonOptions(builder, environment, prefix);
        return builder.build();
    }

    private Integer resolveMaxCompletionTokens(Environment environment, String prefix) {
        Integer completion = environment.getProperty(prefix + "max-completion-tokens", Integer.class);
        if (completion != null) {
            return completion;
        }
        Integer legacyMaxTokens = environment.getProperty(prefix + "max-tokens", Integer.class);
        if (legacyMaxTokens != null) {
            return legacyMaxTokens;
        }
        return AgentConstants.DEFAULT_MAX_OUTPUT_TOKENS;
    }

    private void applyCommonOptions(Object builder, Environment environment, String prefix) {
        String baseUrl = environment.getProperty(prefix + "base-url");
        String organizationId = environment.getProperty(prefix + "organization-id");
        String projectId = environment.getProperty(prefix + "project-id");
        Double temperature = environment.getProperty(prefix + "temperature", Double.class);
        Double topP = environment.getProperty(prefix + "top-p", Double.class);
        Double presencePenalty = environment.getProperty(prefix + "presence-penalty", Double.class);
        Double frequencyPenalty = environment.getProperty(prefix + "frequency-penalty", Double.class);
        Integer seed = environment.getProperty(prefix + "seed", Integer.class);
        String user = environment.getProperty(prefix + "user");
        Duration timeout = environment.getProperty(prefix + "timeout", Duration.class);
        Boolean logRequests = environment.getProperty(prefix + "log-requests", Boolean.class);
        Boolean logResponses = environment.getProperty(prefix + "log-responses", Boolean.class);
        String[] stop = environment.getProperty(prefix + "stop", String[].class);

        if (builder instanceof OpenAiChatModel.OpenAiChatModelBuilder chatBuilder) {
            if (StringUtils.hasText(baseUrl)) {
                chatBuilder.baseUrl(baseUrl);
            }
            if (StringUtils.hasText(organizationId)) {
                chatBuilder.organizationId(organizationId);
            }
            if (StringUtils.hasText(projectId)) {
                chatBuilder.projectId(projectId);
            }
            if (temperature != null) {
                chatBuilder.temperature(temperature);
            }
            if (topP != null) {
                chatBuilder.topP(topP);
            }
            if (presencePenalty != null) {
                chatBuilder.presencePenalty(presencePenalty);
            }
            if (frequencyPenalty != null) {
                chatBuilder.frequencyPenalty(frequencyPenalty);
            }
            if (seed != null) {
                chatBuilder.seed(seed);
            }
            if (StringUtils.hasText(user)) {
                chatBuilder.user(user);
            }
            if (timeout != null) {
                chatBuilder.timeout(timeout);
            }
            if (logRequests != null) {
                chatBuilder.logRequests(logRequests);
            }
            if (logResponses != null) {
                chatBuilder.logResponses(logResponses);
            }
            if (stop != null && stop.length > 0) {
                chatBuilder.stop(Arrays.stream(stop).filter(StringUtils::hasText).toList());
            }
            return;
        }

        if (builder instanceof OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder streamingBuilder) {
            if (StringUtils.hasText(baseUrl)) {
                streamingBuilder.baseUrl(baseUrl);
            }
            if (StringUtils.hasText(organizationId)) {
                streamingBuilder.organizationId(organizationId);
            }
            if (StringUtils.hasText(projectId)) {
                streamingBuilder.projectId(projectId);
            }
            if (temperature != null) {
                streamingBuilder.temperature(temperature);
            }
            if (topP != null) {
                streamingBuilder.topP(topP);
            }
            if (presencePenalty != null) {
                streamingBuilder.presencePenalty(presencePenalty);
            }
            if (frequencyPenalty != null) {
                streamingBuilder.frequencyPenalty(frequencyPenalty);
            }
            if (seed != null) {
                streamingBuilder.seed(seed);
            }
            if (StringUtils.hasText(user)) {
                streamingBuilder.user(user);
            }
            if (timeout != null) {
                streamingBuilder.timeout(timeout);
            }
            if (logRequests != null) {
                streamingBuilder.logRequests(logRequests);
            }
            if (logResponses != null) {
                streamingBuilder.logResponses(logResponses);
            }
            if (stop != null && stop.length > 0) {
                streamingBuilder.stop(Arrays.stream(stop).filter(StringUtils::hasText).toList());
            }
        }
    }
}
