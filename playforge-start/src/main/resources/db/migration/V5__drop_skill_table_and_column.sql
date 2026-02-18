-- Idempotent migration: replace skill_ids with skill_names, drop t_agent_skill table
-- Uses stored procedure because MySQL 8.0 lacks ADD COLUMN IF NOT EXISTS

DELIMITER //

CREATE PROCEDURE __v5_migrate()
BEGIN
    DECLARE has_skill_names INT DEFAULT 0;
    DECLARE has_skill_ids INT DEFAULT 0;
    DECLARE has_skill_table INT DEFAULT 0;

    SELECT COUNT(*) INTO has_skill_names FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_agent_definition' AND column_name = 'skill_names';

    SELECT COUNT(*) INTO has_skill_ids FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_agent_definition' AND column_name = 'skill_ids';

    SELECT COUNT(*) INTO has_skill_table FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 't_agent_skill';

    -- Add skill_names column if not exists
    IF has_skill_names = 0 THEN
        ALTER TABLE t_agent_definition
            ADD COLUMN skill_names VARCHAR(1000) DEFAULT NULL
            COMMENT '技能名称列表（逗号分隔，引用代码定义的@Skill）'
            AFTER tool_names;
    END IF;

    -- Backfill skill_names from skill_ids + t_agent_skill (only if both still exist)
    IF has_skill_ids > 0 AND has_skill_table > 0 THEN
        UPDATE t_agent_definition ad
        SET skill_names = (
            SELECT GROUP_CONCAT(s.name ORDER BY FIND_IN_SET(CAST(s.id AS CHAR), ad.skill_ids))
            FROM t_agent_skill s
            WHERE FIND_IN_SET(CAST(s.id AS CHAR), ad.skill_ids) > 0
        )
        WHERE ad.skill_ids IS NOT NULL
          AND ad.skill_ids <> '';
    END IF;
END //

DELIMITER ;

CALL __v5_migrate();
DROP PROCEDURE IF EXISTS __v5_migrate;

-- Drop old column and table (already idempotent with IF EXISTS)
ALTER TABLE t_agent_definition DROP COLUMN IF EXISTS skill_ids;
DROP TABLE IF EXISTS t_agent_skill;
