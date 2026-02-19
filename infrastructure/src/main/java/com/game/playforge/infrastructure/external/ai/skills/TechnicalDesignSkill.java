package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 技术策划技能
 * <p>
 * 帮助Lead Agent了解技术设计领域，以便更好地派发任务给技术策划子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "technicalDesign",
        description = "Technical design domain: engine selection, network architecture, performance budgets, platform constraints",
        contentRef = "skills/technical-design.md"
)
public class TechnicalDesignSkill {
}
