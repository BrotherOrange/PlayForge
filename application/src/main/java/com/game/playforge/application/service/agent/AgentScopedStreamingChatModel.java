package com.game.playforge.application.service.agent;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agent级别请求参数包装（流式模型）
 */
public class AgentScopedStreamingChatModel implements StreamingChatModel {

    private static final String THINKING_SIGNATURE_KEY = "thinking_signature";

    private final StreamingChatModel delegate;
    private final ChatRequestParameters scopedParameters;
    private final Map<Object, Object> requestAttributes;

    public AgentScopedStreamingChatModel(StreamingChatModel delegate,
                                         ChatRequestParameters scopedParameters,
                                         Map<Object, Object> requestAttributes) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.scopedParameters = Objects.requireNonNull(scopedParameters, "scopedParameters must not be null");
        this.requestAttributes = requestAttributes == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(requestAttributes));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequest sanitizedRequest = sanitizeGeminiToolMessages(chatRequest);
        delegate.doChat(sanitizedRequest, handler);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters().overrideWith(scopedParameters);
    }

    @Override
    public List<ChatModelListener> listeners() {
        List<ChatModelListener> delegateListeners = delegate.listeners();
        if (requestAttributes.isEmpty()) {
            return delegateListeners;
        }
        List<ChatModelListener> listeners = new ArrayList<>(delegateListeners.size() + 1);
        listeners.add(new FixedAttributesListener(requestAttributes));
        listeners.addAll(delegateListeners);
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private ChatRequest sanitizeGeminiToolMessages(ChatRequest chatRequest) {
        if (provider() != ModelProvider.GOOGLE_AI_GEMINI || chatRequest == null || chatRequest.messages() == null) {
            return chatRequest;
        }

        boolean changed = false;
        boolean skipToolResults = false;
        List<ChatMessage> sanitized = new ArrayList<>(chatRequest.messages().size());
        for (ChatMessage message : chatRequest.messages()) {
            if (skipToolResults && message instanceof ToolExecutionResultMessage) {
                changed = true;
                continue;
            }
            skipToolResults = false;

            if (message instanceof AiMessage aiMessage
                    && aiMessage.hasToolExecutionRequests()
                    && hasMissingThinkingSignature(aiMessage)) {
                changed = true;
                skipToolResults = true;
                copyAiMessageWithoutToolRequests(aiMessage).ifPresent(sanitized::add);
                continue;
            }
            sanitized.add(message);
        }

        if (!changed) {
            return chatRequest;
        }
        return chatRequest.toBuilder().messages(sanitized).build();
    }

    private boolean hasMissingThinkingSignature(AiMessage message) {
        if (message.attributes() == null) {
            return true;
        }
        Object value = message.attributes().get(THINKING_SIGNATURE_KEY);
        return value == null || (value instanceof String stringValue && stringValue.isBlank());
    }

    private java.util.Optional<AiMessage> copyAiMessageWithoutToolRequests(AiMessage source) {
        if (!hasVisibleContent(source)) {
            return java.util.Optional.empty();
        }

        AiMessage.Builder builder = AiMessage.builder()
                .attributes(source.attributes());
        if (!isBlank(source.text())) {
            builder.text(source.text());
        }
        if (!isBlank(source.thinking())) {
            builder.thinking(source.thinking());
        }
        return java.util.Optional.of(builder.build());
    }

    private boolean hasVisibleContent(AiMessage message) {
        return !isBlank(message.text()) || !isBlank(message.thinking());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class FixedAttributesListener implements ChatModelListener {

        private final Map<Object, Object> attributes;

        private FixedAttributesListener(Map<Object, Object> attributes) {
            this.attributes = attributes;
        }

        @Override
        public void onRequest(ChatModelRequestContext context) {
            mergeAttributes(context.attributes());
        }

        @Override
        public void onResponse(ChatModelResponseContext context) {
            mergeAttributes(context.attributes());
        }

        @Override
        public void onError(ChatModelErrorContext context) {
            mergeAttributes(context.attributes());
        }

        private void mergeAttributes(Map<Object, Object> target) {
            if (target == null || attributes.isEmpty()) {
                return;
            }
            attributes.forEach((key, value) -> {
                if (value != null && !target.containsKey(key)) {
                    target.put(key, value);
                }
            });
        }
    }
}
