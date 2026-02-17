package com.game.playforge.infrastructure.external.ai;

import com.game.playforge.common.constant.AuthConstants;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Agent可观测性监听器
 * <p>
 * 实现LangChain4J的 {@link ChatModelListener} 接口，
 * 记录AI模型请求、响应和错误的日志，集成链路追踪。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
public class AgentObservabilityListener implements ChatModelListener {

    @Override
    public void onRequest(ChatModelRequestContext context) {
        String traceId = MDC.get(AuthConstants.TRACE_ID_MDC_KEY);
        int messageCount = context.chatRequest().messages() != null
                ? context.chatRequest().messages().size() : 0;
        log.info("[Agent请求] traceId={}, model={}, messageCount={}",
                traceId, context.chatRequest().modelName(), messageCount);
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        String traceId = MDC.get(AuthConstants.TRACE_ID_MDC_KEY);
        TokenUsage usage = context.chatResponse().tokenUsage();
        if (usage != null) {
            log.info("[Agent响应] traceId={}, inputTokens={}, outputTokens={}, totalTokens={}",
                    traceId, usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
        } else {
            log.info("[Agent响应] traceId={}, tokenUsage=null", traceId);
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        String traceId = MDC.get(AuthConstants.TRACE_ID_MDC_KEY);
        log.error("[Agent错误] traceId={}, error={}", traceId, context.error().getMessage(), context.error());
    }
}
