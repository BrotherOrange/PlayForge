-- 添加 user_id 列（nullable 兼容已有数据）
ALTER TABLE t_agent_definition ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;

-- 删除旧的 name 唯一键
ALTER TABLE t_agent_definition DROP INDEX uk_name;

-- 添加用户级 name 唯一键
ALTER TABLE t_agent_definition ADD UNIQUE KEY uk_user_name (user_id, name);

-- 添加 user_id 索引
ALTER TABLE t_agent_definition ADD INDEX idx_user_id (user_id);
