package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 战斗策划技能
 * <p>
 * 帮助Lead Agent了解战斗设计领域，以便更好地派发任务给战斗策划子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "combatDesign",
        description = "Combat design domain: combat architecture, skill design, class design, hit feedback",
        contentRef = "skills/combat-design.md"
)
public class CombatDesignSkill {
}
