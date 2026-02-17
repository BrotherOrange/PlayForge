CREATE TABLE IF NOT EXISTS t_agent_definition (
    id                 BIGINT        NOT NULL COMMENT 'Agent定义ID',
    name               VARCHAR(100)  NOT NULL COMMENT 'Agent唯一标识',
    display_name       VARCHAR(200)  DEFAULT NULL COMMENT '显示名称',
    description        VARCHAR(1000) DEFAULT NULL COMMENT '描述',
    system_prompt      TEXT          DEFAULT NULL COMMENT '系统提示词（内联）',
    system_prompt_ref  VARCHAR(500)  DEFAULT NULL COMMENT '系统提示词文件引用',
    provider           VARCHAR(50)   NOT NULL COMMENT 'AI供应商（openai/anthropic/gemini）',
    model_name         VARCHAR(100)  NOT NULL COMMENT '模型名称',
    tool_names         VARCHAR(1000) DEFAULT NULL COMMENT '工具名称列表（逗号分隔）',
    skill_ids          VARCHAR(500)  DEFAULT NULL COMMENT '技能ID列表（逗号分隔）',
    memory_window_size INT           NOT NULL DEFAULT 20 COMMENT '记忆窗口大小',
    temperature        DECIMAL(3,2)  DEFAULT NULL COMMENT '温度参数',
    max_tokens         INT           DEFAULT NULL COMMENT '最大Token数',
    is_active          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_deleted         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否删除',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent定义表';

CREATE TABLE IF NOT EXISTS t_agent_skill (
    id              BIGINT        NOT NULL COMMENT '技能ID',
    name            VARCHAR(100)  NOT NULL COMMENT '技能唯一标识',
    display_name    VARCHAR(200)  DEFAULT NULL COMMENT '显示名称',
    description     VARCHAR(1000) DEFAULT NULL COMMENT '描述',
    prompt_fragment TEXT          DEFAULT NULL COMMENT '提示词片段',
    tool_names      VARCHAR(1000) DEFAULT NULL COMMENT '工具名称列表（逗号分隔）',
    is_active       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_deleted      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否删除',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent技能表';

CREATE TABLE IF NOT EXISTS t_agent_thread (
    id                BIGINT       NOT NULL COMMENT '会话ID',
    agent_id          BIGINT       NOT NULL COMMENT 'Agent定义ID',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    title             VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE/ARCHIVED/DELETED）',
    message_count     INT          NOT NULL DEFAULT 0 COMMENT '消息数量',
    total_tokens_used BIGINT       NOT NULL DEFAULT 0 COMMENT '累计Token用量',
    last_message_at   DATETIME     DEFAULT NULL COMMENT '最后消息时间',
    is_deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_agent (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';

CREATE TABLE IF NOT EXISTS t_agent_message (
    id          BIGINT       NOT NULL COMMENT '消息ID',
    thread_id   BIGINT       NOT NULL COMMENT '会话ID',
    role        VARCHAR(20)  NOT NULL COMMENT '角色（user/assistant/system/tool）',
    content     MEDIUMTEXT   DEFAULT NULL COMMENT '消息内容',
    tool_name   VARCHAR(100) DEFAULT NULL COMMENT '工具名称',
    token_count INT          NOT NULL DEFAULT 0 COMMENT 'Token数量',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_thread_time (thread_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息表';
