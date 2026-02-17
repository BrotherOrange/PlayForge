package com.game.playforge.common.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一业务结果码枚举
 * <p>
 * 编码规范：0=成功, 10xx=认证, 20xx=用户, 30xx=OSS, 40xx=客户端, 50xx=Agent, 9xxx=系统。
 * 每个业务码绑定对应的HTTP状态码，供异常处理器和拦截器统一使用。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(0, "OK", 200),

    // ---------- 10xx - 认证相关 ----------

    /**
     * 手机号或密码错误
     */
    CREDENTIALS_ERROR(1001, "手机号或密码错误", 401),

    /**
     * 手机号已注册
     */
    PHONE_ALREADY_REGISTERED(1002, "手机号已注册", 409),

    /**
     * AccessToken已过期
     */
    TOKEN_EXPIRED(1003, "token已过期", 401),

    /**
     * AccessToken无效
     */
    TOKEN_INVALID(1004, "token无效", 401),

    /**
     * RefreshToken无效或已过期
     */
    REFRESH_TOKEN_INVALID(1005, "refreshToken无效", 401),

    /**
     * 未登录（缺少Authorization头）
     */
    NOT_LOGGED_IN(1006, "未登录", 401),

    // ---------- 20xx - 用户相关 ----------

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(2001, "用户不存在", 404),

    // ---------- 30xx - OSS相关 ----------

    /**
     * 上传签名生成失败
     */
    OSS_POLICY_ERROR(3001, "上传签名生成失败", 500),

    // ---------- 4xxx - 通用客户端错误 ----------

    /**
     * 资源不存在
     */
    NOT_FOUND(4001, "资源不存在", 404),

    /**
     * 需要管理员权限
     */
    ADMIN_REQUIRED(4002, "需要管理员权限，请联系管理员开通", 403),

    // ---------- 50xx - Agent相关 ----------

    /**
     * Agent不存在
     */
    AGENT_NOT_FOUND(5001, "Agent不存在", 404),

    /**
     * 会话不存在
     */
    THREAD_NOT_FOUND(5002, "会话不存在", 404),

    /**
     * 无权访问该会话
     */
    THREAD_ACCESS_DENIED(5003, "无权访问该会话", 403),

    /**
     * 无权操作该Agent
     */
    AGENT_ACCESS_DENIED(5006, "无权操作该Agent", 403),

    /**
     * AI服务暂不可用
     */
    AGENT_PROVIDER_UNAVAILABLE(5004, "AI服务暂不可用", 503),

    /**
     * 工具执行错误
     */
    AGENT_TOOL_ERROR(5005, "工具执行错误", 500),

    // ---------- 9xxx - 系统级 ----------

    /**
     * 系统内部错误
     */
    INTERNAL_ERROR(9001, "系统内部错误", 500),

    /**
     * 参数校验失败
     */
    PARAM_VALIDATION_FAILED(9002, "参数校验失败", 400);

    /**
     * 业务结果码
     */
    private final int code;

    /**
     * 结果描述信息
     */
    private final String message;

    /**
     * 对应的HTTP状态码
     */
    private final int httpStatus;
}
