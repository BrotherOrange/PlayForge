package com.game.playforge.api.interceptor;

import com.game.playforge.common.constant.AuthConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 链路追踪拦截器
 * <p>
 * 为每个请求生成唯一traceId，写入MDC和响应头，支持全链路日志追踪。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(AuthConstants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(AuthConstants.TRACE_ID_HEADER, traceId);
        log.info("请求开始, method={}, uri={}, traceId={}", request.getMethod(), request.getRequestURI(), traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        log.info("请求结束, method={}, uri={}, status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
        MDC.remove(AuthConstants.TRACE_ID_MDC_KEY);
    }
}
