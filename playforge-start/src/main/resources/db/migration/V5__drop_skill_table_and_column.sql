-- 将 skill_ids 列替换为 skill_names（先迁移历史数据，再删除旧结构）
ALTER TABLE t_agent_definition
    ADD COLUMN skill_names VARCHAR(1000) DEFAULT NULL
    COMMENT '技能名称列表（逗号分隔，引用代码定义的@Skill）'
    AFTER tool_names;

-- 回填：把 skill_ids 映射为 skill_names，保留顺序
UPDATE t_agent_definition ad
SET skill_names = (
    SELECT GROUP_CONCAT(s.name ORDER BY FIND_IN_SET(CAST(s.id AS CHAR), ad.skill_ids))
    FROM t_agent_skill s
    WHERE FIND_IN_SET(CAST(s.id AS CHAR), ad.skill_ids) > 0
)
WHERE ad.skill_ids IS NOT NULL
  AND ad.skill_ids <> '';

ALTER TABLE t_agent_definition DROP COLUMN IF EXISTS skill_ids;

-- 删除 Skill 数据库表，改为代码定义（@Skill 注解）
DROP TABLE IF EXISTS t_agent_skill;
