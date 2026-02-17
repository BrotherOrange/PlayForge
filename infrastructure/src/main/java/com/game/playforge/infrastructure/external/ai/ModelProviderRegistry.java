package com.game.playforge.infrastructure.external.ai;

import com.game.playforge.common.enums.ModelProvider;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * AI模型供应商注册中心
 * <p>
 * 管理多个AI供应商的ChatModel实例，提供统一的模型获取接口。
 * 配合 LangChain4jBeanFilter 使用：未配置 API Key 的供应商 Bean 定义
 * 已被移除，此处通过 @Autowired(required=false) 获得 null。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
public class ModelProviderRegistry {

    private final Map<ModelProvider, ChatModel> chatModels = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, StreamingChatModel> streamingModels = new EnumMap<>(ModelProvider.class);

    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel openAiChatModel;

    @Autowired(required = false)
    @Qualifier("anthropicChatModel")
    private ChatModel anthropicChatModel;

    @Autowired(required = false)
    @Qualifier("googleAiGeminiChatModel")
    private ChatModel geminiChatModel;

    @Autowired(required = false)
    @Qualifier("openAiStreamingChatModel")
    private StreamingChatModel openAiStreamingChatModel;

    @Autowired(required = false)
    @Qualifier("anthropicStreamingChatModel")
    private StreamingChatModel anthropicStreamingChatModel;

    @Autowired(required = false)
    @Qualifier("googleAiGeminiStreamingChatModel")
    private StreamingChatModel geminiStreamingChatModel;

    @PostConstruct
    public void init() {
        register(ModelProvider.OPENAI, openAiChatModel, chatModels, "ChatModel");
        register(ModelProvider.ANTHROPIC, anthropicChatModel, chatModels, "ChatModel");
        register(ModelProvider.GEMINI, geminiChatModel, chatModels, "ChatModel");

        register(ModelProvider.OPENAI, openAiStreamingChatModel, streamingModels, "StreamingChatModel");
        register(ModelProvider.ANTHROPIC, anthropicStreamingChatModel, streamingModels, "StreamingChatModel");
        register(ModelProvider.GEMINI, geminiStreamingChatModel, streamingModels, "StreamingChatModel");

        log.info("ModelProviderRegistry初始化完成, chatModels={}, streamingModels={}",
                chatModels.keySet(), streamingModels.keySet());
    }

    private <T> void register(ModelProvider provider, T model, Map<ModelProvider, T> target, String type) {
        if (model != null) {
            target.put(provider, model);
            log.info("注册{}: {}", type, provider);
        }
    }

    /**
     * 获取指定供应商的ChatModel
     */
    public ChatModel getChatModel(ModelProvider provider) {
        ChatModel model = chatModels.get(provider);
        if (model == null) {
            log.error("ChatModel不可用, provider={}", provider);
            throw new BusinessException(ResultCode.AGENT_PROVIDER_UNAVAILABLE);
        }
        return model;
    }

    /**
     * 获取指定供应商的StreamingChatModel
     */
    public StreamingChatModel getStreamingChatModel(ModelProvider provider) {
        StreamingChatModel model = streamingModels.get(provider);
        if (model == null) {
            log.error("StreamingChatModel不可用, provider={}", provider);
            throw new BusinessException(ResultCode.AGENT_PROVIDER_UNAVAILABLE);
        }
        return model;
    }
}
