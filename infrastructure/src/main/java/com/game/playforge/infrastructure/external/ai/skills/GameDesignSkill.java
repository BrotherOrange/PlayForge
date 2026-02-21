package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 游戏设计技能
 * <p>
 * 提供游戏机制分析、系统设计和平衡性评估能力。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "gameDesign",
        description = "游戏设计专家，提供游戏机制分析、系统设计和平衡性评估能力",
        contentRef = "skill-game-design.md",
        toolNames = {"dateTimeTool"}
)
public class GameDesignSkill {

    @Tool("分析游戏机制的平衡性，给出评分和改进建议")
    public String analyzeGameBalance(@P("游戏机制描述") String description) {
        return "已收到游戏机制描述，正在分析平衡性。机制概要: " + description;
    }
}
