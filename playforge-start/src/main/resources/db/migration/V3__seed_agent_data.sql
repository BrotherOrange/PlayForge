-- 插入默认Agent定义
INSERT INTO t_agent_definition (id, name, display_name, description, provider, model_name, system_prompt_ref, tool_names, memory_window_size, temperature, max_tokens)
VALUES (1, 'general-assistant', 'PlayForge助手', '通用游戏设计助手，可以回答问题、提供建议、查询信息', 'anthropic', 'claude-opus-4-6', 'general-assistant.md', 'dateTimeTool,userInfoTool', 20, 0.70, 4096);

-- 插入默认技能
INSERT INTO t_agent_skill (id, name, display_name, description, prompt_fragment, tool_names)
VALUES (1, 'datetime', '日期时间', '提供日期和时间查询能力', '你可以使用日期时间工具来获取当前的日期和时间信息。当用户询问时间相关问题时，请主动使用该工具。', 'dateTimeTool');
