package com.game.playforge.common.constant;

/**
 * 认证模块常量
 *
 * @author Richard Zhang
 * @since 1.0
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /**
     * HTTP请求头：Authorization
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Bearer Token前缀
     */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 响应头：链路追踪ID
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * MDC中traceId的Key
     */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /**
     * 请求属性：当前登录用户ID
     */
    public static final String CURRENT_USER_ID = "currentUserId";

    /**
     * Redis Key前缀：RefreshToken → userId
     */
    public static final String REFRESH_TOKEN_PREFIX = "playforge:refresh_token:";

    /**
     * Redis Key前缀：用户信息缓存
     */
    public static final String USER_CACHE_PREFIX = "playforge:user:cache:";
}
