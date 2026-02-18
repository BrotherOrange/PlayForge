ALTER TABLE t_agent_definition
    ADD COLUMN parent_thread_id BIGINT DEFAULT NULL
    COMMENT 'Parent thread ID (for sub-agents created by lead agent)'
    AFTER skill_names;
