package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 数值策划技能
 * <p>
 * 帮助Lead Agent了解数值平衡领域，以便更好地派发任务给数值策划子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "balancingDesign",
        description = "Balancing design domain: damage formulas, growth curves, economy models, probability systems",
        contentRef = "skills/balancing-design.md"
)
public class BalancingDesignSkill {
}
