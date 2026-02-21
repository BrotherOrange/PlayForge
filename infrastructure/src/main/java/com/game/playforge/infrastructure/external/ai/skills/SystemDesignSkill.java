package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 系统策划技能
 * <p>
 * 帮助Lead Agent了解系统设计领域，以便更好地派发任务给系统策划子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "systemDesign",
        description = "Systems design domain: core loops, economy, progression, social systems, and events",
        contentRef = "skills/system-design.md"
)
public class SystemDesignSkill {
}
