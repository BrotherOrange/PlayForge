package com.game.playforge.infrastructure.external.ai.skills;

import com.game.playforge.common.annotation.Skill;
import org.springframework.stereotype.Component;

/**
 * 执行策划技能
 * <p>
 * 帮助Lead Agent了解执行策划领域，以便更好地派发文档和细节任务给执行策划子Agent。
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Component
@Skill(
        name = "executionPlanning",
        description = "Execution planning domain: documentation, config tables, test cases, competitive analysis",
        contentRef = "skills/execution-planning.md"
)
public class ExecutionPlanningSkill {
}
