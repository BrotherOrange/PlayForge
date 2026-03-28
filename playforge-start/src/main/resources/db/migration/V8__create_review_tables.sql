CREATE TABLE IF NOT EXISTS t_review_task (
    id                      BIGINT        NOT NULL COMMENT 'Review task id',
    public_id               VARCHAR(64)   NOT NULL COMMENT 'Public access id',
    title                   VARCHAR(255)  NOT NULL COMMENT 'Task title',
    provider                VARCHAR(50)   NOT NULL COMMENT 'Model provider',
    model_name              VARCHAR(100)  NOT NULL COMMENT 'Model name',
    status                  VARCHAR(32)   NOT NULL COMMENT 'Task status',
    document_count          INT           NOT NULL DEFAULT 0 COMMENT 'Document count',
    overall_report_markdown MEDIUMTEXT    NULL COMMENT 'Overall task report',
    error_message           VARCHAR(1000) NULL COMMENT 'Task level error',
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_task_public_id (public_id),
    KEY idx_review_task_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Public review tasks';

CREATE TABLE IF NOT EXISTS t_review_document (
    id               BIGINT        NOT NULL COMMENT 'Review document id',
    task_id          BIGINT        NOT NULL COMMENT 'Parent task id',
    source_type      VARCHAR(32)   NOT NULL COMMENT 'Input source type',
    sort_order       INT           NOT NULL DEFAULT 0 COMMENT 'Sort order inside task',
    title            VARCHAR(255)  NOT NULL COMMENT 'Document title',
    original_filename VARCHAR(255) NULL COMMENT 'Original uploaded filename',
    markdown_content MEDIUMTEXT    NOT NULL COMMENT 'Canonical markdown source',
    blocks_json      LONGTEXT      NOT NULL COMMENT 'Serialized review blocks',
    status           VARCHAR(32)   NOT NULL COMMENT 'Document status',
    warnings_json    LONGTEXT      NULL COMMENT 'Serialized warnings',
    summary_markdown MEDIUMTEXT    NULL COMMENT 'Per-document summary report',
    error_message    VARCHAR(1000) NULL COMMENT 'Document level error',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (id),
    KEY idx_review_document_task (task_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Documents under a review task';

CREATE TABLE IF NOT EXISTS t_review_role_run (
    id               BIGINT        NOT NULL COMMENT 'Role run id',
    document_id      BIGINT        NOT NULL COMMENT 'Review document id',
    role_key         VARCHAR(64)   NOT NULL COMMENT 'Role key',
    role_name        VARCHAR(64)   NOT NULL COMMENT 'Role label',
    role_color       VARCHAR(16)   NOT NULL COMMENT 'Role color',
    status           VARCHAR(32)   NOT NULL COMMENT 'Role run status',
    annotation_count INT           NOT NULL DEFAULT 0 COMMENT 'Generated annotation count',
    error_message    VARCHAR(1000) NULL COMMENT 'Role level error',
    completed_at     DATETIME      NULL COMMENT 'Completed at',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_role_document_role (document_id, role_key),
    KEY idx_review_role_document_status (document_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-role review executions';

CREATE TABLE IF NOT EXISTS t_review_annotation (
    id              BIGINT        NOT NULL COMMENT 'Annotation id',
    document_id     BIGINT        NOT NULL COMMENT 'Review document id',
    role_run_id     BIGINT        NOT NULL COMMENT 'Role run id',
    role_key        VARCHAR(64)   NOT NULL COMMENT 'Role key',
    role_name       VARCHAR(64)   NOT NULL COMMENT 'Role label',
    role_color      VARCHAR(16)   NOT NULL COMMENT 'Role color',
    annotation_type VARCHAR(32)   NOT NULL COMMENT 'Annotation type',
    priority        VARCHAR(32)   NOT NULL COMMENT 'Priority',
    block_id        VARCHAR(64)   NOT NULL COMMENT 'Target block id',
    start_offset    INT           NOT NULL COMMENT 'Block-relative start offset',
    end_offset      INT           NOT NULL COMMENT 'Block-relative end offset',
    quote_text      VARCHAR(500)  NOT NULL COMMENT 'Quoted text',
    title           VARCHAR(255)  NOT NULL COMMENT 'Short annotation title',
    content         TEXT          NOT NULL COMMENT 'Annotation body',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (id),
    KEY idx_review_annotation_document_block (document_id, block_id, start_offset),
    KEY idx_review_annotation_role_run (role_run_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Review annotations';
