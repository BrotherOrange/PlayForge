ALTER TABLE t_review_task
    ADD COLUMN created_by_user_id BIGINT NULL COMMENT '创建任务的用户ID' AFTER document_count;

CREATE INDEX idx_review_task_creator_updated
    ON t_review_task (created_by_user_id, updated_at, created_at);
