package com.game.playforge.application.service.agent;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agent级别请求参数包装（同步模型）
 */
public class AgentScopedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ChatRequestParameters scopedParameters;

    public AgentScopedChatModel(ChatModel delegate, ChatRequestParameters scopedParameters) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.scopedParameters = Objects.requireNonNull(scopedParameters, "scopedParameters must not be null");
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return delegate.doChat(chatRequest);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters().overrideWith(scopedParameters);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
