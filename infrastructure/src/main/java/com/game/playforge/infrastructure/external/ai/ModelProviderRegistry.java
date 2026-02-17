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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * AI模型供应商注册中心
 * <p>
 * 管理多个AI供应商的ChatModel实例，提供统一的模型获取接口。
 * 使用 @Lazy 延迟加载，配合 try-catch 实现优雅降级：
 * 当某个供应商的 API Key 未配置时，跳过该供应商而不影响启动。
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

    @Lazy
    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel openAiChatModel;

    @Lazy
    @Autowired(required = false)
    @Qualifier("anthropicChatModel")
    private ChatModel anthropicChatModel;

    @Lazy
    @Autowired(required = false)
    @Qualifier("googleAiGeminiChatModel")
    private ChatModel geminiChatModel;

    @Lazy
    @Autowired(required = false)
    @Qualifier("openAiStreamingChatModel")
    private StreamingChatModel openAiStreamingChatModel;

    @Lazy
    @Autowired(required = false)
    @Qualifier("anthropicStreamingChatModel")
    private StreamingChatModel anthropicStreamingChatModel;

    @Lazy
    @Autowired(required = false)
    @Qualifier("googleAiGeminiStreamingChatModel")
    private StreamingChatModel geminiStreamingChatModel;

    @PostConstruct
    public void init() {
        tryRegisterChatModel(ModelProvider.OPENAI, openAiChatModel);
        tryRegisterChatModel(ModelProvider.ANTHROPIC, anthropicChatModel);
        tryRegisterChatModel(ModelProvider.GEMINI, geminiChatModel);

        tryRegisterStreamingModel(ModelProvider.OPENAI, openAiStreamingChatModel);
        tryRegisterStreamingModel(ModelProvider.ANTHROPIC, anthropicStreamingChatModel);
        tryRegisterStreamingModel(ModelProvider.GEMINI, geminiStreamingChatModel);

        log.info("ModelProviderRegistry初始化完成, chatModels={}, streamingModels={}",
                chatModels.keySet(), streamingModels.keySet());
    }

    private void tryRegisterChatModel(ModelProvider provider, ChatModel lazyModel) {
        try {
            if (lazyModel != null) {
                // 触发 @Lazy 代理的实际初始化，验证 bean 可用
                lazyModel.toString();
                chatModels.put(provider, lazyModel);
                log.info("注册ChatModel: {}", provider);
            }
        } catch (Exception e) {
            log.warn("ChatModel不可用, 跳过 {}: {}", provider, e.getMessage());
        }
    }

    private void tryRegisterStreamingModel(ModelProvider provider, StreamingChatModel lazyModel) {
        try {
            if (lazyModel != null) {
                lazyModel.toString();
                streamingModels.put(provider, lazyModel);
                log.info("注册StreamingChatModel: {}", provider);
            }
        } catch (Exception e) {
            log.warn("StreamingChatModel不可用, 跳过 {}: {}", provider, e.getMessage());
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
