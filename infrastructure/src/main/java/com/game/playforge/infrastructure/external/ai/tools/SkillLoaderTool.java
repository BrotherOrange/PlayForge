package com.game.playforge.infrastructure.external.ai.tools;

import com.game.playforge.infrastructure.external.ai.SkillRegistry;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 技能加载工具
 * <p>
 * LLM 通过调用此工具按需加载技能的完整内容。
 * system prompt 中只包含轻量级技能目录，
 * 当 LLM 判断需要某个技能时，调用此工具获取详细指南。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Slf4j
@Component("skillLoaderTool")
@RequiredArgsConstructor
public class SkillLoaderTool {

    private final SkillRegistry skillRegistry;

    @Tool("根据技能名称加载完整的技能指南内容，当你需要某个领域的专业知识时调用此工具")
    public String loadSkill(@P("技能名称") String skillName) {
        log.info("加载技能: {}", skillName);
        String content = skillRegistry.getSkillContent(skillName);
        if (content == null || content.isBlank()) {
            return "未找到技能: " + skillName;
        }
        return content;
    }
}
