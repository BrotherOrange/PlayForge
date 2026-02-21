-- Optimize /api/agents listing queries.
-- 1) t_agent_definition: filter by user_id + is_active and sort by created_at
-- 2) t_agent_thread: filter by user_id + status + agent_id and pick latest thread

DELIMITER //

CREATE PROCEDURE __v7_add_agent_list_indexes()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 't_agent_definition'
          AND index_name = 'idx_agent_user_active_created'
    ) THEN
        ALTER TABLE t_agent_definition
            ADD INDEX idx_agent_user_active_created (user_id, is_active, created_at, id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 't_agent_thread'
          AND index_name = 'idx_thread_user_status_agent_created'
    ) THEN
        ALTER TABLE t_agent_thread
            ADD INDEX idx_thread_user_status_agent_created (user_id, status, agent_id, created_at, id);
    END IF;
END //

DELIMITER ;

CALL __v7_add_agent_list_indexes();
DROP PROCEDURE IF EXISTS __v7_add_agent_list_indexes;
