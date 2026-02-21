package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 剧情/文案策划技能
 * <p>
 * 帮助Lead Agent了解叙事设计领域，以便更好地派发任务给剧情策划子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "narrativeDesign",
        description = "Narrative design domain: world-building, character creation, quest narrative, branching dialogue",
        contentRef = "skills/narrative-design.md"
)
public class NarrativeDesignSkill {
}
