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
        if (openAiChatModel != null) {
            chatModels.put(ModelProvider.OPENAI, openAiChatModel);
            log.info("注册ChatModel: OPENAI");
        }
        if (anthropicChatModel != null) {
            chatModels.put(ModelProvider.ANTHROPIC, anthropicChatModel);
            log.info("注册ChatModel: ANTHROPIC");
        }
        if (geminiChatModel != null) {
            chatModels.put(ModelProvider.GEMINI, geminiChatModel);
            log.info("注册ChatModel: GEMINI");
        }

        if (openAiStreamingChatModel != null) {
            streamingModels.put(ModelProvider.OPENAI, openAiStreamingChatModel);
            log.info("注册StreamingChatModel: OPENAI");
        }
        if (anthropicStreamingChatModel != null) {
            streamingModels.put(ModelProvider.ANTHROPIC, anthropicStreamingChatModel);
            log.info("注册StreamingChatModel: ANTHROPIC");
        }
        if (geminiStreamingChatModel != null) {
            streamingModels.put(ModelProvider.GEMINI, geminiStreamingChatModel);
            log.info("注册StreamingChatModel: GEMINI");
        }

        log.info("ModelProviderRegistry初始化完成, chatModels={}, streamingModels={}",
                chatModels.keySet(), streamingModels.keySet());
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
