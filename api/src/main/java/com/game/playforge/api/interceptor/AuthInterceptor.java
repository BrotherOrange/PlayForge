package com.game.playforge.api.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.infrastructure.external.auth.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT认证拦截器
 * <p>
 * 校验请求头中的Bearer Token，通过后将userId写入请求属性供后续使用。
 * 认证失败时根据 {@link ResultCode} 绑定的HTTP状态码返回错误响应。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String authHeader = request.getHeader(AuthConstants.AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            log.warn("认证失败-缺少Authorization头, uri={}", request.getRequestURI());
            writeError(response, ResultCode.NOT_LOGGED_IN);
            return false;
        }

        String token = authHeader.substring(AuthConstants.BEARER_PREFIX.length());

        if (jwtUtil.isExpired(token)) {
            log.warn("认证失败-Token已过期, uri={}", request.getRequestURI());
            writeError(response, ResultCode.TOKEN_EXPIRED);
            return false;
        }

        if (!jwtUtil.isValid(token)) {
            log.warn("认证失败-Token无效, uri={}", request.getRequestURI());
            writeError(response, ResultCode.TOKEN_INVALID);
            return false;
        }

        Long userId = jwtUtil.parseUserId(token);
        request.setAttribute(AuthConstants.CURRENT_USER_ID, userId);
        log.debug("认证通过, userId={}, uri={}", userId, request.getRequestURI());
        return true;
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws Exception {
        response.setStatus(resultCode.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(resultCode)));
    }
}
