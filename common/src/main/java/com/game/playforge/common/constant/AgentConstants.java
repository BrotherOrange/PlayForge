package com.game.playforge.common.constant;

/**
 * AI Agent模块常量
 *
 * @author Richard Zhang
 * @since 1.0
 */
public final class AgentConstants {

    private AgentConstants() {
    }

    /**
     * Redis Key前缀：Agent会话记忆
     */
    public static final String MEMORY_PREFIX = "playforge:agent:memory:";

    /**
     * Redis Key前缀：Agent分布式锁
     */
    public static final String LOCK_PREFIX = "playforge:agent:lock:";

    /**
     * 默认记忆窗口大小（消息条数）
     *
     * @deprecated 使用 {@link #DEFAULT_MAX_MEMORY_TOKENS} 替代
     */
    @Deprecated
    public static final int DEFAULT_MEMORY_WINDOW_SIZE = 60;

    /**
     * 触发摘要压缩的消息数阈值
     *
     * @deprecated 使用 {@link #SUMMARIZATION_TRIGGER_TOKENS} 替代
     */
    @Deprecated
    public static final int SUMMARIZATION_TRIGGER_SIZE = 50;

    /**
     * 摘要后保留的近期消息数
     *
     * @deprecated 使用 {@link #RECENT_TOKENS_TO_KEEP} 替代
     */
    @Deprecated
    public static final int RECENT_MESSAGES_TO_KEEP = 20;

    /**
     * 默认记忆窗口Token上限（200K）
     */
    public static final int DEFAULT_MAX_MEMORY_TOKENS = 200_000;

    /**
     * 触发摘要压缩的Token阈值（128K）
     */
    public static final int SUMMARIZATION_TRIGGER_TOKENS = 128_000;

    /**
     * 摘要后保留的近期消息Token预算（80K）
     */
    public static final int RECENT_TOKENS_TO_KEEP = 80_000;

    /**
     * 记忆缓存过期时间（小时）
     */
    public static final long MEMORY_TTL_HOURS = 72;

    /**
     * Agent默认最大输出Token数
     */
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 32768;

    /**
     * Anthropic最大输出Token上限
     */
    public static final int ANTHROPIC_SAFE_MAX_OUTPUT_TOKENS = 32768;

    /**
     * 子Agent最大输出Token数（每个Agent专注一份完整文件）
     */
    public static final int SUB_AGENT_MAX_OUTPUT_TOKENS = 24576;

}
