CREATE TABLE IF NOT EXISTS t_review_report_history (
    id              BIGINT        NOT NULL COMMENT 'Review report history id',
    task_id         BIGINT        NOT NULL COMMENT 'Parent review task id',
    document_id     BIGINT        NULL COMMENT 'Document id when scope is document',
    report_scope    VARCHAR(32)   NOT NULL COMMENT 'Report scope such as overall or document',
    title           VARCHAR(255)  NOT NULL COMMENT 'Report title',
    provider        VARCHAR(50)   NOT NULL COMMENT 'Model provider',
    model_name      VARCHAR(100)  NOT NULL COMMENT 'Model name',
    report_markdown MEDIUMTEXT    NOT NULL COMMENT 'Generated markdown report',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (id),
    KEY idx_review_report_history_task_scope (task_id, report_scope, created_at),
    KEY idx_review_report_history_document_scope (document_id, report_scope, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Generated review report history';
