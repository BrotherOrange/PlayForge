-- Idempotent migration: add parent_thread_id column for sub-agent support

DELIMITER //

CREATE PROCEDURE __v6_add_parent_thread_id()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 't_agent_definition'
          AND column_name = 'parent_thread_id'
    ) THEN
        ALTER TABLE t_agent_definition
            ADD COLUMN parent_thread_id BIGINT DEFAULT NULL
            COMMENT 'Parent thread ID (for sub-agents created by lead agent)'
            AFTER skill_names;
    END IF;
END //

DELIMITER ;

CALL __v6_add_parent_thread_id();
DROP PROCEDURE IF EXISTS __v6_add_parent_thread_id;
