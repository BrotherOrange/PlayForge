package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 关卡策划技能
 * <p>
 * 帮助Lead Agent了解关卡设计领域，以便更好地派发任务给关卡策划子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "levelDesign",
        description = "Level design domain: level layout, encounter design, pacing, boss fight design",
        contentRef = "skills/level-design.md"
)
public class LevelDesignSkill {
}
